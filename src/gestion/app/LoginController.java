package gestion.app;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import javax.swing.SwingWorker;

import gestion.net.LoginStatus;
import gestion.net.ServerConnection;
import gestion.net.ServerConnection.LoginResult;
import gestion.ui.LoginWindow;

public final class LoginController implements AutoCloseable {

	public enum Outcome {
		AUTHENTICATED, MODE_SELECTION, CLOSED
	}

	private final LoginWindow window;
	private final ServerConnection connection;
	private final WindowAdapter lifecycle;
	private SwingWorker<LoginResult, Void> activeAttempt;
	private boolean authenticated;
	private boolean closed;

	public LoginController(LoginWindow window, ServerConnection connection) {
		this.window = Objects.requireNonNull(window);
		this.connection = Objects.requireNonNull(connection);
		lifecycle = new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent event) {
				close();
			}
		};
		window.addWindowListener(lifecycle);
		window.setLoginAction(this::submit);
	}

	public Outcome showLogin() {

		try {
			window.showLogin();

			if (authenticated) {
				return Outcome.AUTHENTICATED;
			}

			return window.isModeSelectionRequested() ? Outcome.MODE_SELECTION : Outcome.CLOSED;
		} finally {
			close();
		}

	}

	@Override
	public void close() {

		if (closed) {
			return;
		}

		closed = true;
		window.removeWindowListener(lifecycle);

		if (activeAttempt != null) {
			activeAttempt.cancel(true);
			activeAttempt = null;
		}

	}

	private void submit() {

		if (closed || authenticated || activeAttempt != null || !window.isDisplayable()) {
			return;
		}

		String userName = window.getUsername();
		char[] password = window.getPassword();
		String totpCode = window.getTotpCode();
		SwingWorker<LoginResult, Void> worker = new SwingWorker<>() {
			private char[] pendingPassword = password;

			@Override
			protected LoginResult doInBackground() {
				char[] suppliedPassword;

				synchronized (this) {

					if (isCancelled()) {
						return new LoginResult(LoginStatus.FAILED, null);
					}

					suppliedPassword = pendingPassword;
					pendingPassword = null;
				}

				try {
					return connection.login(userName, suppliedPassword, totpCode);
				} finally {
					Arrays.fill(suppliedPassword, '\0');
				}

			}

			@Override
			protected void done() {

				synchronized (this) {

					if (pendingPassword != null) {
						Arrays.fill(pendingPassword, '\0');
						pendingPassword = null;
					}

				}

				if (closed || isCancelled() || activeAttempt != this || !window.isDisplayable()) {
					return;
				}

				activeAttempt = null;
				LoginResult result;

				try {
					result = get();

					if (result == null || result.status() == null) {
						result = new LoginResult(LoginStatus.FAILED, null);
					}

				} catch (CancellationException failure) {
					return;
				} catch (InterruptedException failure) {
					Thread.currentThread().interrupt();
					result = new LoginResult(LoginStatus.FAILED, null);
				} catch (ExecutionException failure) {
					result = new LoginResult(LoginStatus.FAILED, null);
				}

				showResult(result);
			}
		};
		activeAttempt = worker;

		try {
			worker.execute();
		} catch (RuntimeException failure) {
			activeAttempt = null;
			worker.cancel(true);
			throw failure;
		}

	}

	private void showResult(LoginResult result) {
		authenticated = result.status() == LoginStatus.AUTHENTICATED;

		switch (result.status()) {
		case AUTHENTICATED:
			window.showSuccess();
			break;

		case REJECTED:
			window.showAuthenticationRejected();
			break;

		case CSRF_REJECTED:
			window.showError("La session de sécurité a expiré. Réessayez.");
			break;

		case UNAVAILABLE:
			window.showError("Le serveur est indisponible. Réessayez plus tard.");
			break;

		case FAILED:
			window.showError("Impossible de contacter le serveur. Vérifiez votre connexion.");
			break;
		}

	}
}
