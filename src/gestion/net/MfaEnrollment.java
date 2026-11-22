package gestion.net;

import java.time.Instant;
import java.util.UUID;

public record MfaEnrollment(UUID enrollmentID, String setupKey, Instant expiresAt) {

	public MfaEnrollment {
		if (enrollmentID == null || setupKey == null || !setupKey.matches("[A-Z2-7]{32}") || expiresAt == null) {
			throw new IllegalArgumentException("La configuration de l’authentificateur est invalide.");
		}
	}

	@Override
	public String toString() {
		return "MfaEnrollment[protected]";
	}
}
