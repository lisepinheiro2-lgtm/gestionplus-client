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

				LoginController login = new LoginController(new LoginWindow(), new ServerConnection());

				switch (login.showLogin()) {
				case AUTHENTICATED:
					return LaunchMode.CONNECTED;

				case CLOSED:
					return null;

				case MODE_SELECTION:
					break;
				}

			}

		});

		SwingUtilities.invokeLater(selection);
		return selection.get();
	}

}
