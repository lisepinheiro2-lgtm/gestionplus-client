package gestion.app;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import gestion.app.ConnectedApplicationContext.SessionState;
import gestion.net.ServerConnection;
import gestion.net.ServerConnection.CurrentUser;

class ConnectedApplicationContextTest {

	private RecordingConnection connection;
	private ConnectedApplicationContext context;
	private CurrentUser user;

	@BeforeEach
	void initialize() {
		connection = new RecordingConnection();
		user = new CurrentUser("fictitious_session_user");
		context = new ConnectedApplicationContext(connection, user);
	}

	@AfterEach
	void closeContext() {
		context.close();
	}

	@Test
	void startsActiveWithTheConfirmedIdentityAndTheTransferredConnection() {
		assertTrue(context.isActive());
		assertEquals(SessionState.ACTIVE, context.sessionState());
		assertSame(connection, context.connection());
		assertSame(user, context.user());
		assertNotNull(context.contextId());
	}

	@Test
	void businessOperationsRequireBothServerAvailabilityAndAnActiveContext() {
		assertFalse(context.canStartBusinessOperation());
		context.setAvailable(true);
		context.replaceStructures(java.util.List.of(new gestion.net.StructureSummary(java.util.UUID.randomUUID(),
				"Fictitious structure")), null);
		assertTrue(context.canStartBusinessOperation());
		context.setAvailable(false);
		assertFalse(context.canStartBusinessOperation());
		context.setAvailable(true);
		context.beginClosing();
		assertFalse(context.canStartBusinessOperation());
		context.close();
		assertFalse(context.canStartBusinessOperation());
	}

	@Test
	void requiresBothTheConnectionAndTheConfirmedIdentity() {
		assertThrows(NullPointerException.class, () -> new ConnectedApplicationContext(null, user));
		assertThrows(NullPointerException.class, () -> new ConnectedApplicationContext(connection, null));
	}

	@Test
	void enteringClosingBlocksTheActiveStateWithoutClosingTheTransportOrSendingLogout() {
		context.beginClosing();
		assertFalse(context.isActive());
		assertEquals(SessionState.CLOSING, context.sessionState());
		assertEquals(0, connection.closeCalls.get());
		assertEquals(0, connection.logoutCalls.get());
		assertDoesNotThrow(context::beginClosing);
		assertEquals(SessionState.CLOSING, context.sessionState());
	}

	@Test
	void closingAnActiveContextReleasesTheTransportExactlyOnceWithoutRemoteLogout() {
		context.close();
		context.close();
		assertFalse(context.isActive());
		assertEquals(SessionState.CLOSED, context.sessionState());
		assertEquals(1, connection.closeCalls.get());
		assertEquals(0, connection.logoutCalls.get());
	}

	@Test
	void closingAfterBeginClosingAlsoReleasesTheTransportOnlyOnce() {
		context.beginClosing();
		context.close();
		context.close();
		assertEquals(SessionState.CLOSED, context.sessionState());
		assertEquals(1, connection.closeCalls.get());
		assertEquals(0, connection.logoutCalls.get());
	}

	@Test
	void aClosedContextCannotReturnToClosing() {
		context.close();
		assertThrows(IllegalStateException.class, context::beginClosing);
		assertEquals(SessionState.CLOSED, context.sessionState());
		assertEquals(1, connection.closeCalls.get());
	}

	@Test
	void theReleasedConnectionCannotStartAnotherRequest() {
		context.close();
		assertThrows(IOException.class, connection::fetchCurrentUser);
	}

	@Test
	void closingTheFirstAccountDoesNotCloseOrReplaceTheSecondAccount() {
		try (var other = new ConnectedApplicationContext(new RecordingConnection(),
				new CurrentUser("fictitious_second_user"))) {
			assertNotEquals(context.contextId(), other.contextId());
			assertNotEquals(context.connection(), other.connection());
			context.beginClosing();
			context.close();
			assertTrue(other.isActive());
			assertEquals(SessionState.ACTIVE, other.sessionState());
			assertEquals("fictitious_second_user", other.user().userName());
			assertEquals(0, ((RecordingConnection) other.connection()).closeCalls.get());
		}
	}

	private static final class RecordingConnection extends ServerConnection {
		private final AtomicInteger closeCalls = new AtomicInteger();
		private final AtomicInteger logoutCalls = new AtomicInteger();

		private RecordingConnection() {
			super(URI.create("http://127.0.0.1:1"));
		}

		@Override
		public void logout() {
			logoutCalls.incrementAndGet();
		}

		@Override
		public void close() {
			closeCalls.incrementAndGet();
			super.close();
		}
	}

	@Test
	void closesItsRegisteredSessionResourcesOnceBeforeReleasingTheConnection() {
		AtomicInteger released = new AtomicInteger();
		context.onClose(() -> {
			assertEquals(SessionState.CLOSED, context.sessionState());
			assertEquals(0, connection.closeCalls.get());
			released.incrementAndGet();
		});
		context.close();
		context.close();
		assertEquals(1, released.get());
		assertEquals(1, connection.closeCalls.get());
	}

	@Test
	void registeringAResourceAfterClosureReleasesItImmediately() {
		context.close();
		AtomicInteger released = new AtomicInteger();
		context.onClose(released::incrementAndGet);
		assertEquals(1, released.get());
		assertEquals(1, connection.closeCalls.get());
	}
}
