package gestion.app;

import static gestion.ui.WindowTestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import gestion.app.SessionCoordinator.EndReason;
import gestion.app.SessionCoordinator.LogoutResult;
import gestion.net.HttpApiException;
import gestion.net.ServerConnection;
import gestion.net.ServerConnection.CurrentUser;
import gestion.net.SessionExpiration;
import gestion.net.SessionObservation;

class SessionMonitoringTest {

	private static final Instant SERVER_TIME = Instant.parse("2026-11-21T12:00:00Z");
	private final AtomicLong time = new AtomicLong();
	private final ManualExecutor checks = new ManualExecutor();
	private final List<SessionStatus> statuses = new ArrayList<>();
	private final AtomicBoolean updatesOnEdt = new AtomicBoolean(true);
	private final AtomicInteger endings = new AtomicInteger();
	private final CompletableFuture<LogoutResult> ended = new CompletableFuture<>();
	private MonitoringConnection connection;
	private ConnectedApplicationContext context;
	private SessionCoordinator coordinator;

	@BeforeEach
	void initialize() {
		connection = new MonitoringConnection();
		connection.observation = observation(300, 180);
		context = new ConnectedApplicationContext(connection, new CurrentUser("fictitious_monitored_user"));
		coordinator = new SessionCoordinator(context, result -> {
			updatesOnEdt.compareAndSet(true, SwingUtilities.isEventDispatchThread());
			endings.incrementAndGet();
			ended.complete(result);
		}, status -> {
			updatesOnEdt.compareAndSet(true, SwingUtilities.isEventDispatchThread());
			statuses.add(status);
		}, time::get, checks);
	}

	@AfterEach
	void closeTheContext() throws Exception {
		onEdt(context::close);
	}

	@Test
	void verifiesTheSessionOffEdtAndDoesNotKeepItAliveThroughTheCurrentUserRoute() throws Exception {
		onEdt(coordinator::startMonitoring);
		onEdt(() -> {
			assertEquals(SessionStatus.State.CHECKING, lastStatus().state());
			assertFalse(context.canStartBusinessOperation());
		});
		runCheck();
		onEdt(() -> {
			assertEquals(SessionStatus.State.ACTIVE, lastStatus().state());
			assertTrue(context.canStartBusinessOperation());
		});
		assertFalse(connection.checkOnEdt.get());
		assertTrue(updatesOnEdt.get());
		assertEquals(1, connection.checkCalls.get());
		assertEquals(0, connection.currentUserCalls.get());
		assertFalse(ended.isDone());
	}

	@Test
	void repeatedRefreshesDoNotOverlapAndClosingCancelsThePendingCheck() throws Exception {
		onEdt(() -> {
			coordinator.startMonitoring();
			coordinator.startMonitoring();
			coordinator.refreshSession();
			coordinator.refreshSession();
			assertEquals(1, checks.tasks.size());
			context.close();
			coordinator.refreshSession();
		});
		assertTrue(checks.isShutdown());
		assertEquals(0, connection.checkCalls.get());
		assertEquals(1, connection.closeCalls.get());
		assertFalse(ended.isDone());
	}

	@Test
	void aNetworkFailureSuspendsTheUiAndAnExplicitRetryRestoresTheSession() throws Exception {
		startActive();
		connection.failure = new IOException("Diagnostic privé fictif.");
		onEdt(coordinator::refreshSession);
		runCheck();
		onEdt(() -> {
			assertEquals(SessionStatus.State.UNAVAILABLE, lastStatus().state());
			assertFalse(lastStatus().allowsBusinessOperations());
			assertFalse(context.canStartBusinessOperation());
			assertTrue(context.isActive());
		});
		assertFalse(ended.isDone());
		connection.failure = null;
		onEdt(() -> {
			coordinator.refreshSession();
			assertEquals(SessionStatus.State.CHECKING, lastStatus().state());
		});
		runCheck();
		onEdt(() -> assertEquals(SessionStatus.State.ACTIVE, lastStatus().state()));
		assertTrue(updatesOnEdt.get());
	}

	@Test
	void anUnavailableInitialCheckDoesNotInventAnExpirationOrDisconnectImmediately() throws Exception {
		connection.failure = new IOException("Panne réseau fictive.");
		onEdt(coordinator::startMonitoring);
		runCheck();
		onEdt(() -> {
			assertEquals(SessionStatus.State.UNAVAILABLE, lastStatus().state());
			assertTrue(context.isActive());
			coordinator.tick();
			assertTrue(checks.tasks.isEmpty());
		});
		assertFalse(ended.isDone());
	}

	@ParameterizedTest
	@ValueSource(ints = { 403, 503 })
	void permissionAndAvailabilityFailuresAreNotTreatedAsExpiredSessions(int status) throws Exception {
		startActive();
		connection.failure = new HttpApiException(status, status == 403 ? "FORBIDDEN" : "SERVICE_UNAVAILABLE");
		onEdt(coordinator::refreshSession);
		runCheck();
		onEdt(() -> {
			assertTrue(context.isActive());
			assertEquals(SessionStatus.State.UNAVAILABLE, lastStatus().state());
		});
		assertFalse(ended.isDone());
		assertEquals(0, connection.logoutCalls.get());
	}

	@Test
	void warnsAtThePublishedThresholdWithoutSendingAHeartbeat() throws Exception {
		connection.observation = observation(20, 10);
		startActive();
		advance(10);
		onEdt(() -> {
			coordinator.tick();
			assertEquals(SessionStatus.State.EXPIRING, lastStatus().state());
			assertEquals(10, lastStatus().remainingSeconds());
			assertTrue(lastStatus().allowsBusinessOperations());
			assertTrue(checks.tasks.isEmpty());
		});
		assertEquals(1, connection.checkCalls.get());
	}

	@Test
	void anElapsedDeadlineSuspendsAccessAndAcceptsAnExtensionConfirmedByTheServer() throws Exception {
		connection.observation = observation(10, 5);
		startActive();
		advance(10);
		connection.observation = observation(300, 180);
		onEdt(() -> {
			coordinator.tick();
			assertEquals(SessionStatus.State.CHECKING, lastStatus().state());
			assertFalse(lastStatus().allowsBusinessOperations());
		});
		runCheck();
		onEdt(() -> {
			assertEquals(SessionStatus.State.ACTIVE, lastStatus().state());
			assertTrue(context.isActive());
		});
		assertFalse(ended.isDone());
	}

	@Test
	void anUnverifiableElapsedDeadlineClosesLocallyWithoutClaimingRemoteLogout() throws Exception {
		connection.observation = observation(10, 5);
		startActive();
		advance(10);
		connection.failure = new IOException("Panne réseau fictive.");
		onEdt(coordinator::tick);
		runCheck();
		LogoutResult result = ended.get(5, TimeUnit.SECONDS);
		assertEquals(EndReason.LOCAL_EXPIRATION, result.reason());
		assertFalse(result.serverConfirmed());
		assertFalse(result.quitAfter());
		assertEquals(0, connection.logoutCalls.get());
		assertEquals(1, connection.closeCalls.get());
		assertFalse(context.isActive());
		assertTrue(checks.isShutdown());
	}

	@Test
	void aRejectedStatusCheckEndsTheSessionWithoutAnAdditionalLogoutAttempt() throws Exception {
		startActive();
		connection.failure = new HttpApiException(401, "AUTHENTICATION_REQUIRED");
		onEdt(coordinator::refreshSession);
		runCheck();
		LogoutResult result = ended.get(5, TimeUnit.SECONDS);
		assertEquals(EndReason.SESSION_REJECTED, result.reason());
		assertTrue(result.serverConfirmed());
		assertEquals(0, connection.logoutCalls.get());
		assertEquals(1, connection.closeCalls.get());
		assertTrue(updatesOnEdt.get());
	}

	@Test
	void a401FromAnotherProtectedRequestAlsoEndsTheSessionOnlyOnce() throws Exception {
		startActive();
		connection.invalidation.run();
		connection.invalidation.run();
		LogoutResult result = ended.get(5, TimeUnit.SECONDS);
		onEdt(() -> {
			coordinator.tick();
			coordinator.refreshSession();
		});
		assertEquals(EndReason.SESSION_REJECTED, result.reason());
		assertEquals(1, endings.get());
		assertEquals(0, connection.logoutCalls.get());
		assertEquals(1, connection.closeCalls.get());
	}

	@Test
	void aQueuedPositiveCheckCannotRestoreTheUiAfterLogoutHasStarted() throws Exception {
		onEdt(coordinator::startMonitoring);
		CountDownLatch occupied = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		SwingUtilities.invokeLater(() -> {
			occupied.countDown();
			try {
				if (!release.await(5, TimeUnit.SECONDS)) {
					throw new AssertionError("L’EDT fictif n’a pas été libéré.");
				}
				coordinator.requestLogout(false);
			} catch (InterruptedException failure) {
				Thread.currentThread().interrupt();
			}
		});
		try {
			assertTrue(occupied.await(5, TimeUnit.SECONDS));
			checks.runNext();
		} finally {
			release.countDown();
		}
		LogoutResult result = ended.get(5, TimeUnit.SECONDS);
		onEdt(() -> assertFalse(statuses.stream().anyMatch(SessionStatus::allowsBusinessOperations)));
		assertEquals(EndReason.LOGOUT, result.reason());
		assertEquals(1, connection.logoutCalls.get());
		assertEquals(1, endings.get());
	}

	@Test
	void closingOneContextDoesNotStopOrChangeAnotherAccountsMonitor() throws Exception {
		startActive();
		MonitoringConnection otherConnection = new MonitoringConnection();
		otherConnection.observation = observation(500, 380);
		ManualExecutor otherChecks = new ManualExecutor();
		List<SessionStatus> otherStatuses = new ArrayList<>();
		try (var other = new ConnectedApplicationContext(otherConnection, new CurrentUser("fictitious_account_b"))) {
			SessionCoordinator otherCoordinator = new SessionCoordinator(other, result -> {
			}, otherStatuses::add, time::get, otherChecks);
			onEdt(() -> {
				otherCoordinator.startMonitoring();
				context.close();
				coordinator.tick();
			});
			otherChecks.runNext();
			onEdt(() -> {
				assertEquals(SessionStatus.State.ACTIVE, otherStatuses.getLast().state());
				assertTrue(other.isActive());
				assertEquals("fictitious_account_b", other.user().userName());
			});
			assertEquals(0, otherConnection.closeCalls.get());
		}
	}

	private void startActive() throws Exception {
		onEdt(coordinator::startMonitoring);
		runCheck();
	}

	private void runCheck() throws Exception {
		assertFalse(SwingUtilities.isEventDispatchThread());
		checks.runNext();
		onEdt(() -> {
		});
	}

	private SessionStatus lastStatus() {
		return statuses.getLast();
	}

	private void advance(long seconds) {
		time.addAndGet(Duration.ofSeconds(seconds).toNanos());
	}

	private static SessionObservation observation(long expirationSeconds, long warningSeconds) {
		return new SessionObservation(
				new SessionExpiration(SERVER_TIME.plusSeconds(expirationSeconds), SERVER_TIME.plusSeconds(warningSeconds)),
				SERVER_TIME, Duration.ZERO);
	}

	private static final class MonitoringConnection extends ServerConnection {
		private final AtomicInteger checkCalls = new AtomicInteger();
		private final AtomicInteger currentUserCalls = new AtomicInteger();
		private final AtomicInteger logoutCalls = new AtomicInteger();
		private final AtomicInteger closeCalls = new AtomicInteger();
		private final AtomicBoolean checkOnEdt = new AtomicBoolean();
		private SessionObservation observation;
		private IOException failure;
		private volatile Runnable invalidation = () -> {
		};

		private MonitoringConnection() {
			super(URI.create("http://127.0.0.1:1"));
		}

		@Override
		public SessionObservation fetchSessionObservation() throws IOException {
			checkCalls.incrementAndGet();
			checkOnEdt.set(SwingUtilities.isEventDispatchThread());
			if (failure != null) {
				throw failure;
			}
			return observation;
		}

		@Override
		public java.util.Optional<String> fetchCurrentUser() {
			currentUserCalls.incrementAndGet();
			return java.util.Optional.of("fictitious_monitored_user");
		}

		@Override
		public void setSessionInvalidationHandler(Runnable handler) {
			invalidation = handler;
		}

		@Override
		public void logout() {
			logoutCalls.incrementAndGet();
		}

		@Override
		public void close() {
			closeCalls.incrementAndGet();
			super.close();
			invalidation = () -> {
			};
		}
	}

	private static final class ManualExecutor extends AbstractExecutorService {
		private final Queue<Runnable> tasks = new ArrayDeque<>();
		private boolean shutdown;

		void runNext() {
			Runnable next = tasks.poll();
			if (next == null) {
				throw new AssertionError("Aucune vérification fictive n’est en attente.");
			}
			next.run();
		}

		@Override
		public void execute(Runnable command) {
			if (shutdown) {
				throw new java.util.concurrent.RejectedExecutionException();
			}
			tasks.add(command);
		}

		@Override
		public void shutdown() {
			shutdown = true;
		}

		@Override
		public List<Runnable> shutdownNow() {
			shutdown = true;
			List<Runnable> pending = List.copyOf(tasks);
			tasks.clear();
			return pending;
		}

		@Override
		public boolean isShutdown() {
			return shutdown;
		}

		@Override
		public boolean isTerminated() {
			return shutdown && tasks.isEmpty();
		}

		@Override
		public boolean awaitTermination(long timeout, TimeUnit unit) {
			return isTerminated();
		}
	}
}

