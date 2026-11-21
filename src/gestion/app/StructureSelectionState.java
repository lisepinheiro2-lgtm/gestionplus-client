package gestion.app;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import gestion.net.StructureSummary;

public record StructureSelectionState(Phase phase, List<StructureSummary> structures, UUID selectionID,
		String message) {

	public StructureSelectionState {
		Objects.requireNonNull(phase);
		structures = List.copyOf(structures);
		Objects.requireNonNull(message);
	}

	public enum Phase {
		SUSPENDED, LOADING, READY, EMPTY, FAILED, VALIDATING
	}
}
