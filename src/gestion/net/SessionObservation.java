package gestion.net;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record SessionObservation(SessionExpiration expiration, Instant serverTime, Duration roundTrip) {

	public SessionObservation {
		Objects.requireNonNull(expiration);
		Objects.requireNonNull(serverTime);
		Objects.requireNonNull(roundTrip);
		if (roundTrip.isNegative()) {
			throw new IllegalArgumentException("La durée de vérification ne peut pas être négative.");
		}
	}
}
