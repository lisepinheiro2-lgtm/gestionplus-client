package gestion.app;

import static gestion.ui.WindowTestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.SwingUtilities;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import gestion.app.SessionCoordinator.LogoutResult;
import gestion.net.ServerConnection;
import gestion.net.ServerConnection.CurrentUser;
import gestion.net.SessionExpiration;
import gestion.net.SessionObservation;

class SessionMonitoringConcurrencyTest {

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void logoutRemainsResponsiveAndAnOldCheckCannotAffectTheNextAccount(boolean ignoreInterruption) throws Exception {
		BlockingConnection firstConnection = new BlockingConnection(ignoreInterruption);
		BlockingConnection secondConnection = new BlockingConnection(false);
		secondConnection.release.countDown();
		ExecutorService firstChecks = Executors.newSingleThreadExecutor();
		List<SessionStatus> firstStatuses = new ArrayList<>();
		CompletableFuture<LogoutResult> firstEnded = new CompletableFuture<>();
		CompletableFuture<SessionStatus> secondActive = new CompletableFuture<>();
		AtomicInteger endings = new AtomicInteger();

		try (var first = new ConnectedApplicationContext(firstConnection, new CurrentUser("fictitious_account_a"));
				var second = new ConnectedApplicationContext(secondConnection, new CurrentUser("fictitious_account_b"))) {
			SessionCoordinator firstCoordinator = new SessionCoordinator(first, result -> {
				endings.incrementAndGet();
				firstEnded.complete(result);
			}, firstStatuses::add, System::nanoTime, firstChecks);
			SessionCoordinator secondCoordinator = new SessionCoordinator(second, result -> {
			}, status -> {
				if (status.allowsBusinessOperations()) {
					secondActive.complete(status);
				}
			});

			try {
				onEdt(firstCoordinator::startMonitoring);
				assertTrue(firstConnection.started.await(5, TimeUnit.SECONDS));
				assertFalse(firstConnection.onEdt.get());
				AtomicBoolean uiResponsive = new AtomicBoolean();
				onEdt(() -> {
					uiResponsive.set(true);
					firstCoordinator.requestLogout(false);
				});
				assertTrue(uiResponsive.get());
				assertTrue(firstEnded.get(5, TimeUnit.SECONDS).serverConfirmed());
				assertTrue(firstConnection.interrupted.await(5, TimeUnit.SECONDS));
				assertEquals(1, firstConnection.logoutCalls.get());
				onEdt(secondCoordinator::startMonitoring);
				assertEquals(SessionStatus.State.ACTIVE, secondActive.get(5, TimeUnit.SECONDS).state());

				firstConnection.release.countDown();
				assertTrue(firstChecks.awaitTermination(5, TimeUnit.SECONDS));
				onEdt(() -> {
					assertFalse(firstStatuses.stream().anyMatch(SessionStatus::allowsBusinessOperations));
					assertTrue(second.isActive());
					assertEquals("fictitious_account_b", second.user().userName());
					assertEquals(1, endings.get());
				});
			} finally {
				firstConnection.release.countDown();
				secondConnection.release.countDown();
				onEdt(() -> {
					first.close();
					second.close();
				});
			}
		} finally {
			firstChecks.shutdownNow();
			assertTrue(firstChecks.awaitTermination(5, TimeUnit.SECONDS));
		}
	}

	private static final class BlockingConnection extends ServerConnection {
		private final CountDownLatch started = new CountDownLatch(1);
		private final CountDownLatch release = new CountDownLatch(1);
		private final CountDownLatch interrupted = new CountDownLatch(1);
		private final AtomicBoolean onEdt = new AtomicBoolean();
		private final AtomicInteger logoutCalls = new AtomicInteger();
		private final boolean ignoreInterruption;

		private BlockingConnection(boolean ignoreInterruption) {
			super(URI.create("http://127.0.0.1:1"));
			this.ignoreInterruption = ignoreInterruption;
		}

		@Override
		public SessionObservation fetchSessionObservation() throws InterruptedException {
			onEdt.set(SwingUtilities.isEventDispatchThread());
			started.countDown();
			while (release.getCount() != 0) {
				try {
					if (!release.await(5, TimeUnit.SECONDS)) {
						throw new IllegalStateException("La réponse fictive n’a pas été libérée.");
					}
				} catch (InterruptedException failure) {
					interrupted.countDown();
					if (!ignoreInterruption) {
						throw failure;
					}
				}
			}
			Instant serverTime = Instant.parse("2026-11-21T12:00:00Z");
			return new SessionObservation(new SessionExpiration(serverTime.plusSeconds(600), serverTime.plusSeconds(480)),
					serverTime, Duration.ZERO);
		}

		@Override
		public void logout() {
			logoutCalls.incrementAndGet();
		}
	}
}

