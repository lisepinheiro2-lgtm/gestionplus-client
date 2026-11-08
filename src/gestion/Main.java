package gestion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import gestion.app.ApplicationContext;
import gestion.app.DemoDataInitializer;

public final class Main {

	private Main() {
	}

	public static void main(String[] args) {

		try {

			Path directory = Path.of(".");

			if (Boolean.getBoolean("gestionplus.demo")) {

				String localAppData = System.getenv("LOCALAPPDATA");

				if (localAppData == null || localAppData.isBlank()) {
					throw new IOException("Dossier LOCALAPPDATA introuvable.");
				}

				directory = Path.of(localAppData, "GestionPlus", "Demo");
				DemoDataInitializer.initialize(directory);
			}

			ApplicationContext application = new ApplicationContext(directory);
			SwingUtilities.invokeLater(application::openWindow);

		} catch (IOException | UncheckedIOException | IllegalArgumentException exception) {

			SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
					"Impossible de charger les données : " + exception.getMessage(), "Erreur de lecture",
					JOptionPane.ERROR_MESSAGE));
		}
	}
}
