package gestion.app;

import java.time.Duration;
import java.util.Objects;
import java.util.function.LongSupplier;

import gestion.net.SessionObservation;

final class SessionSchedule {

	private static final long CHECK_INTERVAL = Duration.ofSeconds(30).toNanos();
	private final LongSupplier ticker;
	private boolean observed;
	private boolean checking;
	private boolean unavailable;
	private long observedAt;
	private long expiresAfter;
	private long warnsAfter;
	private long checkedAt;

	SessionSchedule(LongSupplier ticker) {
		this.ticker = Objects.requireNonNull(ticker);
	}

	boolean shouldCheck() {
		return !checking && (!observed && !unavailable || deadlineReached()
				|| ticker.getAsLong() - checkedAt >= CHECK_INTERVAL);
	}

	void beginCheck() {
		checking = true;
	}

	long now() {
		return ticker.getAsLong();
	}

	void received(SessionObservation observation, long receivedAt) {
		long expiration = Duration.between(observation.serverTime(), observation.expiration().expiresAt())
				.minus(observation.roundTrip()).toNanos();
		long warning = Duration.between(observation.serverTime(), observation.expiration().warnAt())
				.minus(observation.roundTrip()).toNanos();
		observedAt = receivedAt;
		expiresAfter = Math.max(0, expiration);
		warnsAfter = Math.max(0, warning);
		observed = true;
		checking = false;
		unavailable = false;
		checkedAt = ticker.getAsLong();
	}

	void failed() {
		checking = false;
		unavailable = true;
		checkedAt = ticker.getAsLong();
	}

	boolean deadlineReached() {
		return observed && ticker.getAsLong() - observedAt >= expiresAfter;
	}

	SessionStatus status() {
		if (checking && (unavailable || !observed || deadlineReached())) {
			return new SessionStatus(SessionStatus.State.CHECKING, remainingSeconds());
		}
		if (unavailable) {
			return new SessionStatus(SessionStatus.State.UNAVAILABLE, remainingSeconds());
		}
		if (!observed || deadlineReached()) {
			return new SessionStatus(SessionStatus.State.CHECKING, remainingSeconds());
		}
		boolean warning = ticker.getAsLong() - observedAt >= warnsAfter;
		return new SessionStatus(warning ? SessionStatus.State.EXPIRING : SessionStatus.State.ACTIVE,
				remainingSeconds());
	}

	private long remainingSeconds() {
		if (!observed) {
			return 0;
		}
		long remaining = Math.max(0, expiresAfter - (ticker.getAsLong() - observedAt));
		return remaining / 1_000_000_000L + (remaining % 1_000_000_000L == 0 ? 0 : 1);
	}
}
