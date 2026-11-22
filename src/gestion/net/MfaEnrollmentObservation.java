package gestion.net;

import java.time.Duration;
import java.util.Objects;

public record MfaEnrollmentObservation(MfaEnrollment enrollment, Duration remaining) {

	public MfaEnrollmentObservation {
		Objects.requireNonNull(enrollment);
		Objects.requireNonNull(remaining);
		if (remaining.isNegative() || remaining.compareTo(Duration.ofMinutes(10)) > 0) {
			throw new IllegalArgumentException("L’échéance d’enrôlement est invalide.");
		}
	}
}
