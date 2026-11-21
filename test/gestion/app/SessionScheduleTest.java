package gestion.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import gestion.net.SessionExpiration;
import gestion.net.SessionObservation;

class SessionScheduleTest {

	private static final Instant SERVER_TIME = Instant.parse("2026-11-21T12:00:00Z");
	private final AtomicLong time = new AtomicLong();
	private final SessionSchedule schedule = new SessionSchedule(time::get);

	@Test
	void startsSuspendedUntilTheFirstSuccessfulObservation() {
		assertEquals(SessionStatus.State.CHECKING, schedule.status().state());
		assertFalse(schedule.status().allowsBusinessOperations());
		assertTrue(schedule.shouldCheck());
		schedule.beginCheck();
		assertFalse(schedule.shouldCheck());
	}

	@Test
	void usesTheServerClockAndCountsDownWithMonotonicTime() {
		observe(180, 60);
		assertEquals(SessionStatus.State.ACTIVE, schedule.status().state());
		assertEquals(180, schedule.status().remainingSeconds());
		advance(60);
		assertEquals(SessionStatus.State.EXPIRING, schedule.status().state());
		assertEquals(120, schedule.status().remainingSeconds());
		assertTrue(schedule.status().allowsBusinessOperations());
	}

	@Test
	void accountsForTheNetworkDurationAndTheDelayBeforeSwingProcessesTheResult() {
		SessionObservation observation = new SessionObservation(
				new SessionExpiration(SERVER_TIME.plusSeconds(180), SERVER_TIME.plusSeconds(60)),
				SERVER_TIME, Duration.ofSeconds(3));
		advance(7);
		schedule.received(observation, 0);
		assertEquals(170, schedule.status().remainingSeconds());
	}

	@Test
	void roundsTheDisplayedRemainingTimeUpWithoutExtendingTheDeadline() {
		observe(2, 1);
		time.set(Duration.ofMillis(1_001).toNanos());
		assertEquals(1, schedule.status().remainingSeconds());
		assertFalse(schedule.deadlineReached());
		time.set(Duration.ofSeconds(2).toNanos());
		assertTrue(schedule.deadlineReached());
		assertEquals(0, schedule.status().remainingSeconds());
		assertFalse(schedule.status().allowsBusinessOperations());
	}

	@Test
	void rechecksPeriodicallyWithoutSubmittingAnotherCheckWhileOneIsRunning() {
		observe(600, 480);
		advance(29);
		assertFalse(schedule.shouldCheck());
		advance(1);
		assertTrue(schedule.shouldCheck());
		schedule.beginCheck();
		advance(300);
		assertFalse(schedule.shouldCheck());
	}

	@Test
	void anElapsedDeadlineRequiresVerificationEvenBeforeTheNextPeriodicCheck() {
		observe(10, 5);
		advance(10);
		assertEquals(SessionStatus.State.CHECKING, schedule.status().state());
		assertTrue(schedule.shouldCheck());
		assertFalse(schedule.status().allowsBusinessOperations());
	}

	@Test
	void aNewServerObservationCanExtendTheOldDeadline() {
		observe(10, 5);
		advance(10);
		schedule.beginCheck();
		observe(100, 80);
		assertFalse(schedule.deadlineReached());
		assertEquals(SessionStatus.State.ACTIVE, schedule.status().state());
		assertEquals(100, schedule.status().remainingSeconds());
	}

	@Test
	void aFailureSuspendsBusinessOperationsWithoutExtendingTheKnownDeadline() {
		observe(60, 40);
		advance(30);
		schedule.beginCheck();
		schedule.failed();
		assertEquals(SessionStatus.State.UNAVAILABLE, schedule.status().state());
		assertEquals(30, schedule.status().remainingSeconds());
		assertFalse(schedule.status().allowsBusinessOperations());
		advance(30);
		assertTrue(schedule.deadlineReached());
		assertEquals(0, schedule.status().remainingSeconds());
	}

	@Test
	void anInitialFailureDoesNotInventAValidSessionOrRetryEveryTimerTick() {
		schedule.beginCheck();
		schedule.failed();
		assertEquals(SessionStatus.State.UNAVAILABLE, schedule.status().state());
		assertFalse(schedule.status().allowsBusinessOperations());
		assertFalse(schedule.deadlineReached());
		assertFalse(schedule.shouldCheck());
		advance(30);
		assertTrue(schedule.shouldCheck());
	}

	@Test
	void aSuccessfulRetryRestoresAvailabilityAndTheServerDeadlines() {
		observe(300, 180);
		schedule.failed();
		observe(200, 80);
		assertEquals(SessionStatus.State.ACTIVE, schedule.status().state());
		assertTrue(schedule.status().allowsBusinessOperations());
		assertEquals(200, schedule.status().remainingSeconds());
	}

	@Test
	void anAlreadyElapsedServerDeadlineNeverBecomesAnActiveSession() {
		schedule.received(new SessionObservation(
				new SessionExpiration(SERVER_TIME.minusSeconds(1), SERVER_TIME.minusSeconds(121)),
				SERVER_TIME, Duration.ZERO), time.get());
		assertTrue(schedule.deadlineReached());
		assertFalse(schedule.status().allowsBusinessOperations());
	}

	private void observe(long expirationSeconds, long warningSeconds) {
		schedule.received(new SessionObservation(
				new SessionExpiration(SERVER_TIME.plusSeconds(expirationSeconds), SERVER_TIME.plusSeconds(warningSeconds)),
				SERVER_TIME, Duration.ZERO), time.get());
	}

	private void advance(long seconds) {
		time.addAndGet(Duration.ofSeconds(seconds).toNanos());
	}
}

