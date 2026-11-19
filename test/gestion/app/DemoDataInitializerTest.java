package gestion.app;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DemoDataInitializerTest {

	private static final List<String> FILE_NAMES = List.of("clients.csv", "employees.csv", "employee_absences.csv",
			"interventions.csv", "recurringInterventions.csv", "replacements.csv");

	@TempDir
	Path directory;

	@Test
	void createsTheRequestedDirectoryAndCopiesAllSixPackagedFiles() throws IOException {
		Path target = directory.resolve("fictitious-demo/nested");
		DemoDataInitializer.initialize(target);

		try (var files = Files.list(target)) {
			assertEquals(Set.copyOf(FILE_NAMES),
					files.map(path -> path.getFileName().toString()).collect(Collectors.toSet()));
		}

		for (String name : FILE_NAMES) {
			assertArrayEquals(resourceBytes(name), Files.readAllBytes(target.resolve(name)), name);
		}
	}

	@Test
	void preservesExistingBytesWhileRestoringMissingDemoFiles() throws IOException {
		byte[] existing = "Données fictives déjà modifiées ; à conserver\n".getBytes(StandardCharsets.UTF_8);
		Files.write(directory.resolve("clients.csv"), existing);
		DemoDataInitializer.initialize(directory);
		assertArrayEquals(existing, Files.readAllBytes(directory.resolve("clients.csv")));

		for (String name : FILE_NAMES.stream().filter(name -> !name.equals("clients.csv")).toList()) {
			assertArrayEquals(resourceBytes(name), Files.readAllBytes(directory.resolve(name)), name);
		}
	}

	@Test
	void repeatingInitializationDoesNotRewriteExistingFiles() throws IOException {
		DemoDataInitializer.initialize(directory);
		FileTime marker = FileTime.from(Instant.parse("2026-01-01T00:00:00Z"));
		Map<String, byte[]> contents = new LinkedHashMap<>();

		for (String name : FILE_NAMES) {
			Path file = directory.resolve(name);
			Files.setLastModifiedTime(file, marker);
			contents.put(name, Files.readAllBytes(file));
		}

		DemoDataInitializer.initialize(directory);

		for (String name : FILE_NAMES) {
			assertArrayEquals(contents.get(name), Files.readAllBytes(directory.resolve(name)), name);
			assertEquals(marker, Files.getLastModifiedTime(directory.resolve(name)), name);
		}
	}

	@Test
	void refusesAFileUsedAsTheTargetDirectoryWithoutChangingIt() throws IOException {
		Path target = directory.resolve("fictitious-demo");
		byte[] existing = "Fichier fictif à préserver".getBytes(StandardCharsets.UTF_8);
		Files.write(target, existing);
		assertThrows(IOException.class, () -> DemoDataInitializer.initialize(target));
		assertArrayEquals(existing, Files.readAllBytes(target));
	}

	@Test
	void keepsSiblingDataAndUnrelatedFilesUntouched() throws IOException {
		Path otherData = Files.createDirectory(directory.resolve("other-fictitious-data"));
		Path demo = Files.createDirectory(directory.resolve("demo"));
		Path otherClient = otherData.resolve("clients.csv");
		Path note = demo.resolve("keep.txt");
		Files.writeString(otherClient, "Autres données fictives", StandardCharsets.UTF_8);
		Files.writeString(note, "Note fictive", StandardCharsets.UTF_8);
		DemoDataInitializer.initialize(demo);
		assertEquals("Autres données fictives", Files.readString(otherClient, StandardCharsets.UTF_8));
		assertEquals("Note fictive", Files.readString(note, StandardCharsets.UTF_8));
		assertTrue(Files.isRegularFile(demo.resolve("clients.csv")));
	}

	@Test
	void packagedDemoCanInitializeTheApplicationWithoutOpeningAWindow() throws IOException {
		DemoDataInitializer.initialize(directory);
		assertDoesNotThrow(() -> new ApplicationContext(directory));
	}

	private byte[] resourceBytes(String name) throws IOException {
		try (var input = getClass().getResourceAsStream("/demo-data/" + name)) {
			assertNotNull(input, "Ressource de démonstration absente : " + name);
			return input.readAllBytes();
		}
	}
}
