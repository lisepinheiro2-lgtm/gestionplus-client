package gestion.app;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class DemoDataInitializer {

	public static void initialize(Path directory) throws IOException {

		Files.createDirectories(directory);

		String[] fileNames = { "clients.csv", "employees.csv", "employee_absences.csv", "interventions.csv",
				"recurringInterventions.csv", "replacements.csv" };

		for (String fileName : fileNames) {

			Path destination = directory.resolve(fileName);

			if (Files.exists(destination))
				continue;

			try (InputStream source = DemoDataInitializer.class.getResourceAsStream("/demo-data/" + fileName)) {

				if (source == null)
					throw new IOException("CSV de démonstration introuvable : " + fileName);

				Files.copy(source, destination);
			}
		}
	}
}
