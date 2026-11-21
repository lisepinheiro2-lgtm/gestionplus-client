package gestion.app;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import javax.swing.SwingWorker;

import gestion.net.ActivationInvitation;
import gestion.net.HttpApiException;
import gestion.net.ServerConnection;
import gestion.ui.ActivationWindow;

public final class ActivationController implements AutoCloseable {

	private final ActivationWindow window;
	private final ServerConnection connection;
	private final WindowAdapter lifecycle;
	private SwingWorker<Void, Void> pending;
	private boolean closed;

	public ActivationController(ActivationWindow window, ServerConnection connection) {
		this.window = Objects.requireNonNull(window);
		this.connection = Objects.requireNonNull(connection);
		lifecycle = new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent event) {
				close();
			}
		};
		window.addWindowListener(lifecycle);
		window.setActions(() -> submit(false), () -> submit(true));
	}

	private void submit(boolean activation) {
		if (closed || pending != null || !window.isDisplayable()) {
			return;
		}
		ActivationInvitation invitation = window.invitation();
		String userName = activation ? window.username() : null;
		String code = activation ? window.code() : null;
		char[] password = activation ? window.password() : new char[0];
		window.setBusy(true);
		SwingWorker<Void, Void> worker = new SwingWorker<>() {
			private char[] waitingPassword = password;

			@Override
			protected Void doInBackground() throws Exception {
				char[] supplied;
				synchronized (this) {
					if (isCancelled()) {
						return null;
					}
					supplied = waitingPassword;
					waitingPassword = null;
				}
				try {
					if (activation) {
						connection.completeActivation(invitation, userName, supplied, code);
					} else {
						connection.requestActivationChallenge(invitation);
					}
					return null;
				} finally {
					Arrays.fill(supplied, '\0');
				}
			}

			@Override
			protected void done() {
				synchronized (this) {
					if (waitingPassword != null) {
						Arrays.fill(waitingPassword, '\0');
						waitingPassword = null;
					}
				}
				if (closed || pending != this || isCancelled() || !window.isDisplayable()) {
					return;
				}
				pending = null;
				window.setBusy(false);
				try {
					get();
					if (activation) {
						window.showSuccess();
					} else {
						window.showMessage("Code SMS demandé. Utilisez le dernier code reçu, valable au maximum cinq minutes.", false);
					}
				} catch (CancellationException failure) {
					return;
				} catch (InterruptedException failure) {
					Thread.currentThread().interrupt();
					showFailure(failure);
				} catch (ExecutionException failure) {
					showFailure(failure.getCause());
				}
			}
		};
		pending = worker;
		try {
			worker.execute();
		} catch (RuntimeException failure) {
			pending = null;
			worker.cancel(true);
			window.setBusy(false);
			showFailure(failure);
		}
	}

	private void showFailure(Throwable failure) {
		window.clearSecrets();
		String message = "Impossible de confirmer la demande. Vérifiez votre connexion avant de réessayer.";
		if (failure instanceof HttpApiException http) {
			message = switch (http.status()) {
			case 400 -> "Les informations saisies ne sont pas acceptées. Vérifiez l’identifiant, le mot de passe et le code.";
			case 403 -> "Activation refusée. Vérifiez le lien d’invitation et le code SMS, ou contactez votre administrateur.";
			case 409 -> "Une demande récente ou la limite d’envoi bloque le renvoi. Utilisez le dernier code reçu ou contactez votre administrateur.";
			case 429 -> "Trop de demandes ont été effectuées. Patientez avant de réessayer.";
			case 503 -> "Le service est momentanément indisponible. Aucune nouvelle demande n’est relancée automatiquement.";
			default -> message;
			};
			if (http.status() == 429) {
				window.retryAfter(http.retryAfter().orElse(Duration.ofMinutes(15)));
			}
		}
		window.showMessage(message, true);
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
		connection.close();
	}
}
