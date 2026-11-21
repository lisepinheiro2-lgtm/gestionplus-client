package gestion.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import gestion.net.ServerConnection;
import gestion.net.ServerConnection.CurrentUser;
import gestion.net.StructureSummary;

class StructureContextTest {

	private static final StructureSummary FIRST = new StructureSummary(UUID.randomUUID(), "Fictitious first");
	private static final StructureSummary SECOND = new StructureSummary(UUID.randomUUID(), "Fictitious second");

	@Test
	void selectsTheOnlyStructureAndRequiresBothASelectionAndServerAvailability() {
		try (var context = context()) {
			context.setAvailable(true);
			assertTrue(context.isSessionAvailable());
			assertFalse(context.canStartBusinessOperation());
			context.replaceStructures(List.of(FIRST), null);
			assertEquals(FIRST, context.selectedStructure().orElseThrow());
			assertTrue(context.canStartBusinessOperation());
			context.setAvailable(false);
			assertFalse(context.canStartBusinessOperation());
		}
	}

	@Test
	void multipleStructuresRequireAnExplicitChoiceAndCannotUseAForeignPreferredIdentifier() {
		try (var context = context()) {
			context.setAvailable(true);
			context.replaceStructures(List.of(FIRST, SECOND), UUID.randomUUID());
			assertTrue(context.selectedStructure().isEmpty());
			assertFalse(context.canStartBusinessOperation());
			context.replaceStructures(List.of(FIRST, SECOND), SECOND.structureID());
			assertEquals(SECOND, context.selectedStructure().orElseThrow());
			context.clearSelection();
			assertFalse(context.canStartBusinessOperation());
		}
	}

	@Test
	void retainsAnImmutableSnapshotAndRemovesASelectionThatLostItsAccess() {
		try (var context = context()) {
			context.setAvailable(true);
			List<StructureSummary> source = new ArrayList<>(List.of(FIRST, SECOND));
			context.replaceStructures(source, FIRST.structureID());
			source.clear();
			assertEquals(2, context.structures().size());
			assertThrows(UnsupportedOperationException.class, () -> context.structures().clear());
			context.replaceStructures(List.of(), FIRST.structureID());
			assertTrue(context.structures().isEmpty());
			assertTrue(context.selectedStructure().isEmpty());
		}
	}

	@Test
	void closureClearsTheScopeAndAFormerAccountCannotPopulateTheNextAccount() {
		try (var first = context(); var second = context()) {
			first.setAvailable(true);
			second.setAvailable(true);
			first.replaceStructures(List.of(FIRST), null);
			first.beginClosing();
			assertTrue(first.structures().isEmpty());
			first.replaceStructures(List.of(SECOND), null);
			first.close();
			assertTrue(first.selectedStructure().isEmpty());
			assertTrue(second.structures().isEmpty());
			assertTrue(second.selectedStructure().isEmpty());
			assertFalse(second.canStartBusinessOperation());
		}
	}

	@Test
	void allResourcesAreReleasedEvenWhenOneCleanupFails() {
		var context = context();
		AtomicInteger released = new AtomicInteger();
		RuntimeException failure = new IllegalStateException("Fictitious cleanup failure");
		context.onClose(() -> {
			throw failure;
		});
		context.onClose(released::incrementAndGet);
		assertEquals(failure, assertThrows(IllegalStateException.class, context::close));
		assertEquals(1, released.get());
		assertThrows(java.io.IOException.class, () -> context.connection().fetchAccessibleStructures());
	}

	private static ConnectedApplicationContext context() {
		return new ConnectedApplicationContext(new ServerConnection(URI.create("http://127.0.0.1:1")),
				new CurrentUser("fictitious_structure_user"));
	}
}
