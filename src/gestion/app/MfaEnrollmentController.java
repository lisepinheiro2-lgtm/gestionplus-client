package gestion.app;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import javax.swing.SwingWorker;

import gestion.net.HttpApiException;
import gestion.net.MfaEnrollmentObservation;
import gestion.net.MfaRecoveryCodes;
import gestion.net.ServerConnection;
import gestion.ui.MfaEnrollmentWindow;

public final class MfaEnrollmentController implements AutoCloseable {

	private final MfaEnrollmentWindow window;
	private final ServerConnection connection;
	private final WindowAdapter lifecycle;
	private SwingWorker<?, Void> pending;
	private UUID enrollmentID;
	private boolean confirming;
	private boolean expired;
	private boolean completed;
	private boolean closed;
	private volatile boolean contextOpened;

	public MfaEnrollmentController(MfaEnrollmentWindow window, ServerConnection connection, String proof) {
		this.window = Objects.requireNonNull(window);
		this.connection = Objects.requireNonNull(connection);
		if (proof == null || !proof.matches("[A-Za-z0-9_-]{22}")) {
			throw new IllegalArgumentException("La preuve d’enrôlement est invalide.");
		}
		lifecycle = new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent event) {
				close();
			}
		};
		window.addWindowListener(lifecycle);
		window.setActions(this::confirm, this::begin, this::cancel, this::expire);
		submit(new EnrollmentOpening(proof), this::showEnrollment, false);
	}

	private void begin() {
		submit(connection::beginMfaEnrollment, this::showEnrollment, false);
	}

	private void showEnrollment(MfaEnrollmentObservation observation) {
		enrollmentID = observation.enrollment().enrollmentID();
		contextOpened = true;
		window.showEnrollment(observation);
	}

	private void confirm() {
		if (enrollmentID == null) {
			return;
		}
		String code = window.code();
		window.clearCode();
		submit(() -> connection.completeMfaEnrollment(enrollmentID, code), this::showRecoveryCodes, true);
	}

	private void showRecoveryCodes(MfaRecoveryCodes codes) {
		completed = true;
		enrollmentID = null;
		window.showRecoveryCodes(codes);
		connection.close();
	}

	private <T> void submit(Request<T> request, Consumer<T> success, boolean confirmation) {
		if (closed || pending != null || expired || completed || !window.isDisplayable()) {
			return;
		}
		confirming = confirmation;
		window.setBusy(true, confirmation);
		SwingWorker<T, Void> worker = new SwingWorker<>() {
			@Override
			protected T doInBackground() throws Exception {
				return request.execute();
			}

			@Override
			protected void done() {
				if (closed || pending != this || isCancelled() || !window.isDisplayable()) {
					return;
				}
				pending = null;
				confirming = false;
				window.setBusy(false, false);
				try {
					T result = get();
					if (expired && !confirmation) {
						showExpired();
					} else {
						success.accept(result);
					}
				} catch (CancellationException cancelled) {
					return;
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					showFailure(interrupted, confirmation);
				} catch (ExecutionException failure) {
					showFailure(failure.getCause(), confirmation);
				}
			}
		};
		pending = worker;
		try {
			worker.execute();
		} catch (RuntimeException failure) {
			pending = null;
			confirming = false;
			window.setBusy(false, false);
			showFailure(failure, confirmation);
		}
	}

	private void showFailure(Throwable failure, boolean confirmation) {
		window.clearCode();
		if (expired || failure instanceof HttpApiException http && http.status() == 401) {
			showExpired();
			return;
		}
		if (failure instanceof HttpApiException http) {
			if (http.status() == 429) {
				if (!contextOpened) {
					window.showTerminal("La limite de demandes empêche l’ouverture du parcours. Votre compte est déjà activé ; contactez votre administrateur pour reprendre l’enrôlement avec une preuve valide.");
					close();
					return;
				}
				window.retryAfter(http.retryAfter().orElse(Duration.ofMinutes(10)));
				window.showMessage("Trop de demandes. Patientez avant de réessayer ; l’échéance du parcours reste inchangée.", true);
				return;
			}
			if (confirmation && (http.status() == 400 || http.status() == 403)) {
				window.showMessage("Vérification refusée. Vérifiez le code de l’authentificateur et l’heure de votre appareil. Les tentatives sont limitées.", true);
				return;
			}
			if (!contextOpened && http.status() == 403) {
				window.showTerminal("L’ouverture de l’enrôlement est refusée. Votre compte est déjà activé ; contactez votre administrateur pour reprendre ce parcours de façon sûre.");
				close();
				return;
			}
		}
		if (confirmation) {
			window.showTerminal("Le résultat de la confirmation est incertain. Le code ne sera pas renvoyé. Essayez une connexion normale avec un nouveau TOTP ; si les codes de récupération n’ont pas été reçus ou si l’accès reste impossible, contactez votre administrateur.");
			close();
		} else {
			window.showMessage("Impossible de charger la configuration. « Réessayer » recherche uniquement le parcours déjà ouvert ; la preuve d’ouverture ne sera pas renvoyée. Si le parcours est introuvable, contactez votre administrateur.", true);
		}
	}

	private void expire() {
		expired = true;
		if (confirming && pending != null) {
			window.showMessage("L’échéance est atteinte. Attente du résultat de la confirmation en cours, sans nouvel envoi.", true);
		} else {
			showExpired();
		}
	}

	private void showExpired() {
		window.showTerminal("Le parcours a expiré ou n’est plus disponible. Votre compte est déjà activé. Contactez votre administrateur pour reprendre l’enrôlement ; le SMS et la preuve ne peuvent pas être réutilisés.");
		close();
	}

	private void cancel() {
		close();
		window.dispose();
	}

	@Override
	public void close() {
		if (closed) {
			return;
		}
		closed = true;
		window.removeWindowListener(lifecycle);
		if (pending != null) {
			pending.cancel(true);
			pending = null;
		}
		enrollmentID = null;
		boolean logout = !completed;
		Thread.startVirtualThread(() -> {
			try {
				if (logout) {
					connection.logout();
				}
			} catch (Exception unavailable) {
				if (unavailable instanceof InterruptedException) {
					Thread.currentThread().interrupt();
				}
			} finally {
				connection.close();
			}
		});
	}

	@FunctionalInterface
	private interface Request<T> {
		T execute() throws Exception;
	}

	private final class EnrollmentOpening implements Request<MfaEnrollmentObservation> {
		private String proof;

		private EnrollmentOpening(String proof) {
			this.proof = proof;
		}

		@Override
		public MfaEnrollmentObservation execute() throws Exception {
			String suppliedProof = proof;
			proof = null;
			connection.openMfaEnrollment(suppliedProof);
			contextOpened = true;
			return connection.beginMfaEnrollment();
		}
	}
}
