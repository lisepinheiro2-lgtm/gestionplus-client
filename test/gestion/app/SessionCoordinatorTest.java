package gestion.app;

import static gestion.ui.WindowTestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import gestion.app.ConnectedApplicationContext.SessionState;
import gestion.app.SessionCoordinator.LogoutResult;
import gestion.net.ServerConnection;
import gestion.net.ServerConnection.CurrentUser;

class SessionCoordinatorTest {

	private ControlledConnection connection;
	private ConnectedApplicationContext context;
	private SessionCoordinator coordinator;
	private CompletableFuture<LogoutResult> ended;
	private final AtomicInteger callbacks = new AtomicInteger();
	private final AtomicBoolean callbackOnEdt = new AtomicBoolean();
	private final AtomicReference<SessionState> stateAtCallback = new AtomicReference<>();

	@BeforeEach
	void initialize() {
		connection = new ControlledConnection();
		context = new ConnectedApplicationContext(connection, new CurrentUser("fictitious_session_user"));
		ended = new CompletableFuture<>();
		coordinator = new SessionCoordinator(context, result -> {
			callbacks.incrementAndGet();
			callbackOnEdt.set(SwingUtilities.isEventDispatchThread());
			stateAtCallback.set(context.sessionState());
			ended.complete(result);
		});
	}

	@AfterEach
	void releaseAndCloseContext() throws Exception {
		connection.release.countDown();

		try {
			onEdt(() -> {
			});

			if (connection.logoutCalls.get() > 0) {
				ended.get(5, TimeUnit.SECONDS);
			}

		} finally {
			onEdt(context::close);
		}

	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void confirmsLogoutOnlyAfterTheNetworkAttemptAndLocalCleanup(boolean quitAfter) throws Exception {
		onEdt(() -> coordinator.requestLogout(quitAfter));
		assertTrue(connection.started.await(5, TimeUnit.SECONDS));
		onEdt(() -> {
			assertEquals(SessionState.CLOSING, context.sessionState());
			assertFalse(context.isActive());
			assertEquals(0, connection.closeCalls.get());
		});
		assertFalse(ended.isDone());
		connection.release.countDown();
		LogoutResult result = ended.get(5, TimeUnit.SECONDS);
		assertTrue(result.serverConfirmed());
		assertEquals(quitAfter, result.quitAfter());
		assertFalse(connection.logoutOnEdt.get());
		assertTrue(callbackOnEdt.get());
		assertEquals(SessionState.CLOSED, stateAtCallback.get());
		assertEquals(1, connection.closeCalls.get());
		assertEquals(1, callbacks.get());
	}

	@ParameterizedTest
	@EnumSource(value = Failure.class, names = { "IO", "INTERRUPTED", "RUNTIME" })
	void reportsAnUnconfirmedLogoutAndStillCleansUpForEveryTransportFailure(Failure failure) throws Exception {
		connection.failure = failure;
		onEdt(() -> coordinator.requestLogout(false));
		assertTrue(connection.started.await(5, TimeUnit.SECONDS));
		connection.release.countDown();
		LogoutResult result = ended.get(5, TimeUnit.SECONDS);
		assertFalse(result.serverConfirmed());
		assertFalse(result.quitAfter());
		assertTrue(callbackOnEdt.get());
		assertEquals(SessionState.CLOSED, stateAtCallback.get());
		assertEquals(1, connection.logoutCalls.get());
		assertEquals(1, connection.closeCalls.get());
		assertEquals(1, callbacks.get());
	}

	@Test
	void marshalsAnOffEdtRequestWithoutRunningTheNetworkOnTheSwingThread() throws Exception {
		assertFalse(SwingUtilities.isEventDispatchThread());
		coordinator.requestLogout(false);
		assertTrue(connection.started.await(5, TimeUnit.SECONDS));
		onEdt(() -> assertEquals(SessionState.CLOSING, context.sessionState()));
		connection.release.countDown();
		assertTrue(ended.get(5, TimeUnit.SECONDS).serverConfirmed());
		assertFalse(connection.logoutOnEdt.get());
		assertTrue(callbackOnEdt.get());
	}

	@Test
	void repeatedRequestsDuringLogoutDoNotSubmitAnotherNetworkAttempt() throws Exception {
		onEdt(() -> {
			coordinator.requestLogout(false);
			coordinator.requestLogout(false);
			coordinator.requestLogout(false);
		});
		assertTrue(connection.started.await(5, TimeUnit.SECONDS));
		connection.release.countDown();
		assertFalse(ended.get(5, TimeUnit.SECONDS).quitAfter());
		assertEquals(1, connection.logoutCalls.get());
		assertEquals(1, callbacks.get());
	}

	@Test
	void closingTheWindowDuringLogoutKeepsTheQuitRequestWithoutSubmittingAnotherLogout() throws Exception {
		onEdt(() -> coordinator.requestLogout(false));
		assertTrue(connection.started.await(5, TimeUnit.SECONDS));
		onEdt(() -> {
			coordinator.requestLogout(true);
			coordinator.requestLogout(false);
		});
		connection.release.countDown();
		assertTrue(ended.get(5, TimeUnit.SECONDS).quitAfter());
		assertEquals(1, connection.logoutCalls.get());
		assertEquals(1, callbacks.get());
	}

	@Test
	void requestsAfterCompletionDoNotCloseOrNotifyAgain() throws Exception {
		onEdt(() -> coordinator.requestLogout(false));
		assertTrue(connection.started.await(5, TimeUnit.SECONDS));
		connection.release.countDown();
		ended.get(5, TimeUnit.SECONDS);
		onEdt(() -> {
			coordinator.requestLogout(true);
			coordinator.requestLogout(false);
		});
		assertEquals(1, connection.logoutCalls.get());
		assertEquals(1, connection.closeCalls.get());
		assertEquals(1, callbacks.get());
	}

	@Test
	void ignoresAnAlreadyClosedContextWithoutSendingLogout() throws Exception {
		onEdt(() -> {
			context.close();
			coordinator.requestLogout(false);
		});
		assertEquals(0, connection.logoutCalls.get());
		assertEquals(0, callbacks.get());
		assertEquals(1, connection.closeCalls.get());
		assertFalse(ended.isDone());
	}

	@Test
	void requiresTheContextAndTheCompletionAction() {
		assertThrows(NullPointerException.class, () -> new SessionCoordinator(null, result -> {
		}));
		assertThrows(NullPointerException.class, () -> new SessionCoordinator(context, null));
	}

	private enum Failure {
		NONE, IO, INTERRUPTED, RUNTIME
	}

	private static final class ControlledConnection extends ServerConnection {
		private final CountDownLatch started = new CountDownLatch(1);
		private final CountDownLatch release = new CountDownLatch(1);
		private final AtomicInteger logoutCalls = new AtomicInteger();
		private final AtomicInteger closeCalls = new AtomicInteger();
		private final AtomicBoolean logoutOnEdt = new AtomicBoolean();
		private volatile Failure failure = Failure.NONE;

		private ControlledConnection() {
			super(URI.create("http://127.0.0.1:1"));
		}

		@Override
		public void logout() throws IOException, InterruptedException {
			logoutCalls.incrementAndGet();
			logoutOnEdt.set(SwingUtilities.isEventDispatchThread());
			started.countDown();

			if (!release.await(5, TimeUnit.SECONDS)) {
				throw new IOException("La tentative de déconnexion fictive n'a pas été libérée.");
			}

			switch (failure) {
			case NONE:
				return;
			case IO:
				throw new IOException("Panne réseau fictive.");
			case INTERRUPTED:
				throw new InterruptedException("Interruption fictive.");
			case RUNTIME:
				throw new IllegalStateException("Panne interne fictive.");
			}

		}

		@Override
		public void close() {
			closeCalls.incrementAndGet();
			super.close();
		}
	}
}
