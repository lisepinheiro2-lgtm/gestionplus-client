package gestion.net;

import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StructureSummary(UUID structureID, String structureName) {

	public StructureSummary {
		Objects.requireNonNull(structureID, "L'identifiant de structure est obligatoire.");
		if (structureName == null || structureName.isBlank()) {
			throw new IllegalArgumentException("Le nom de structure est obligatoire.");
		}
	}
}
