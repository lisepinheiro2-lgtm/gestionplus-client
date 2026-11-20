package gestion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import gestion.app.ApplicationContext;
import gestion.app.DemoDataInitializer;
import gestion.app.LoginController;
import gestion.net.ServerConnection;
import gestion.net.ServerConnection.CurrentUser;
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

			LaunchSelection selection = selectLaunchMode();

			if (selection.mode() == LaunchMode.CONNECTED) {
				selection.connection().close();
				return;
			}

			if (selection.mode() != LaunchMode.DEMO) {
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

	private static LaunchSelection selectLaunchMode() throws InterruptedException, ExecutionException {

		FutureTask<LaunchSelection> selection = new FutureTask<>(() -> {
			UIUtils.installLookAndFeel();

			while (true) {
				LaunchMode mode = new LaunchSelectorWindow().askLaunchMode();

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
