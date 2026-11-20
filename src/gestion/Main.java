package gestion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import gestion.app.ApplicationContext;
import gestion.app.ConnectedApplicationContext;
import gestion.app.DemoDataInitializer;
import gestion.app.LoginController;
import gestion.app.SessionCoordinator;
import gestion.app.SessionCoordinator.LogoutResult;
import gestion.net.ServerConnection;
import gestion.net.ServerConnection.CurrentUser;
import gestion.ui.ConnectedHomeWindow;
import gestion.ui.LaunchMode;
import gestion.ui.LaunchSelectorWindow;
import gestion.ui.LoginWindow;
import gestion.ui.UIUtils;

public final class Main {

	private record LaunchSelection(LaunchMode mode, ServerConnection connection, CurrentUser user) {
	}

	private Main() {
	}

	public static void main(String[] args) {

		try {

			LaunchMode initialMode = null;

			while (true) {

				LaunchSelection selection = selectLaunchMode(initialMode);

				if (selection.mode() == LaunchMode.DEMO) {
					openDemo();
					return;
				}

				if (selection.mode() == LaunchMode.CONNECTED) {

					if (runConnectedSession(selection)) {
						return;
					}

					initialMode = LaunchMode.CONNECTED;
					continue;
				}

				return;

			}

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

	private static void openDemo() throws IOException {

		String localAppData = System.getenv("LOCALAPPDATA");

		if (localAppData == null || localAppData.isBlank()) {
			throw new IOException("Dossier LOCALAPPDATA introuvable.");
		}

		Path directory = Path.of(localAppData, "GestionPlus", "Demo");
		DemoDataInitializer.initialize(directory);
		ApplicationContext application = new ApplicationContext(directory);
		SwingUtilities.invokeLater(application::openWindow);

	}

	private static boolean runConnectedSession(LaunchSelection selection)
			throws InterruptedException, ExecutionException {

		ConnectedApplicationContext context = new ConnectedApplicationContext(selection.connection(),
				selection.user());
		CompletableFuture<LogoutResult> sessionEnded = new CompletableFuture<>();
		AtomicReference<ConnectedHomeWindow> window = new AtomicReference<>();
		SessionCoordinator coordinator = new SessionCoordinator(context, result -> {

			try {
				ConnectedHomeWindow current = window.getAndSet(null);

				if (current != null) {
					current.dispose();
				}

				if (!result.serverConfirmed()) {
					JOptionPane.showMessageDialog(null,
							"La déconnexion a été effectuée sur cet ordinateur.\n"
									+ "Le serveur n'a pas confirmé la fermeture de la session.",
							"Déconnexion non confirmée", JOptionPane.WARNING_MESSAGE);
				}

			} finally {
				sessionEnded.complete(result);
			}

		});

		SwingUtilities.invokeLater(() -> {
			ConnectedHomeWindow homeWindow = new ConnectedHomeWindow(context, () -> coordinator.requestLogout(false),
					() -> coordinator.requestLogout(true));
			window.set(homeWindow);
			homeWindow.show();
		});

		return sessionEnded.get().quitAfter();

	}

	private static LaunchSelection selectLaunchMode(LaunchMode initialMode)
			throws InterruptedException, ExecutionException {

		FutureTask<LaunchSelection> selection = new FutureTask<>(() -> {
			UIUtils.installLookAndFeel();
			LaunchMode mode = initialMode;

			while (true) {

				if (mode == null) {
					mode = new LaunchSelectorWindow().askLaunchMode();
				}

				if (mode != LaunchMode.CONNECTED) {
					return new LaunchSelection(mode, null, null);
				}

				ServerConnection connection = new ServerConnection();
				boolean transferred = false;

				try {
					LoginController login = new LoginController(new LoginWindow(), connection);
					LoginController.Result result = login.showLogin();

					switch (result.outcome()) {
					case AUTHENTICATED:
						transferred = true;
						return new LaunchSelection(LaunchMode.CONNECTED, connection, result.user());

					case CLOSED:
						return new LaunchSelection(null, null, null);

					case MODE_SELECTION:
						mode = null;
						break;
					}

				} finally {

					if (!transferred) {
						connection.close();
					}

				}

			}

		});

		SwingUtilities.invokeLater(selection);
		return selection.get();
	}

}
