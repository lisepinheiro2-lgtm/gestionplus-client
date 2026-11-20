package gestion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import gestion.app.ApplicationContext;
import gestion.app.DemoDataInitializer;
import gestion.net.LoginStatus;
import gestion.net.ServerConnection;
import gestion.net.ServerConnection.LoginResult;
import gestion.ui.LaunchMode;
import gestion.ui.LaunchSelectorWindow;
import gestion.ui.LoginWindow;
import gestion.ui.UIUtils;

public final class Main {

	private Main() {
	}

	public static void main(String[] args) {

		try {

			LaunchMode mode = selectLaunchMode();

			if (mode != LaunchMode.DEMO) {
				return;
			}

			String localAppData = System.getenv("LOCALAPPDATA");

			if (localAppData == null || localAppData.isBlank()) {
				throw new IOException("Dossier LOCALAPPDATA introuvable.");
			}

			Path directory = Path.of(localAppData, "GestionPlus", "Demo");
			DemoDataInitializer.initialize(directory);
			ApplicationContext application = new ApplicationContext(directory);
			SwingUtilities.invokeLater(application::openWindow);

		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		} catch (ExecutionException exception) {
			SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
					"Impossible d’ouvrir la fenêtre de démarrage.", "Erreur de démarrage", JOptionPane.ERROR_MESSAGE));
		} catch (IOException | UncheckedIOException | IllegalArgumentException exception) {

			SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
					"Impossible de charger les données : " + exception.getMessage(), "Erreur de lecture",
					JOptionPane.ERROR_MESSAGE));
		}

	}

	private static LaunchMode selectLaunchMode() throws InterruptedException, ExecutionException {

		FutureTask<LaunchMode> selection = new FutureTask<>(() -> {
			UIUtils.installLookAndFeel();

			while (true) {
				LaunchMode mode = new LaunchSelectorWindow().askLaunchMode();

				if (mode != LaunchMode.CONNECTED) {
					return mode;
				}

				if (launchConnectedMode()) {
					return LaunchMode.CONNECTED;
				}

			}

		});

		SwingUtilities.invokeLater(selection);
		return selection.get();
	}

	private static boolean launchConnectedMode() {

		ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
		ServerConnection connection = new ServerConnection();
		LoginWindow loginWindow = new LoginWindow();
		AtomicBoolean authenticated = new AtomicBoolean(false);

		loginWindow.setLoginAction(() -> {
			loginWindow.showSubmitting();
			CompletableFuture
					.supplyAsync(() -> connection.login(loginWindow.getUsername(), loginWindow.getPassword(),
							loginWindow.getTotpCode()), networkExecutor)
					.thenCompose(result -> {

						if (result.status() != LoginStatus.AUTHENTICATED) {
							return CompletableFuture.completedFuture(result);
						}

						return CompletableFuture.supplyAsync(() -> {

							try {
								return connection.fetchCurrentUser().orElse(null);
							} catch (IOException | InterruptedException e) {

								if (e instanceof InterruptedException) {
									Thread.currentThread().interrupt();
								}

								return null;
							}

						}, networkExecutor).thenApply(userName -> new LoginResult(result.status(), userName));
					})
					.thenAccept(result -> SwingUtilities.invokeLater(() -> {
						showLoginResult(loginWindow, result);
						authenticated.set(result.status() == LoginStatus.AUTHENTICATED);
					}));
		});

		loginWindow.showLogin();
		networkExecutor.shutdown();
		return authenticated.get();
	}

	private static void showLoginResult(LoginWindow loginWindow, LoginResult result) {

		switch (result.status()) {
		case AUTHENTICATED:
			loginWindow.showSuccess();
			break;

		case REJECTED:
			loginWindow.showAuthenticationRejected();
			break;

		case CSRF_REJECTED:
			loginWindow.showError("La session de sécurité a expiré. Réessayez.");
			break;

		case UNAVAILABLE:
			loginWindow.showError("Le serveur est indisponible. Réessayez plus tard.");
			break;

		default:
			loginWindow.showError("Impossible de contacter le serveur. Vérifiez votre connexion.");
			break;
		}

	}
}
