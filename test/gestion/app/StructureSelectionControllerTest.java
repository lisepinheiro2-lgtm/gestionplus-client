package gestion.app;

import static gestion.ui.WindowTestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import gestion.app.StructureSelectionState.Phase;
import gestion.net.ServerConnection;
import gestion.net.ServerConnection.CurrentUser;
import gestion.net.StructureSummary;

class StructureSelectionControllerTest {

	private static final StructureSummary FIRST = new StructureSummary(UUID.randomUUID(), "Fictitious first");
	private static final StructureSummary SECOND = new StructureSummary(UUID.randomUUID(), "Fictitious second");
	private final ManualExecutor executor = new ManualExecutor();
	private final StructureConnection connection = new StructureConnection();
	private final ConnectedApplicationContext context = new ConnectedApplicationContext(connection,
			new CurrentUser("fictitious_structure_session"));
	private final List<StructureSelectionState> changes = new ArrayList<>();
	private final AtomicBoolean notificationsOnEdt = new AtomicBoolean(true);
	private StructureSelectionController controller;

	@BeforeEach
	void initialize() {
		controller = new StructureSelectionController(context, state -> {
			notificationsOnEdt.compareAndSet(true, SwingUtilities.isEventDispatchThread());
			changes.add(state);
		}, executor);
	}

	@AfterEach
	void closeContext() throws Exception {
		onEdt(context::close);
	}

	@Test
	void doesNotRequestStructuresUntilTheSessionIsConfirmed() throws Exception {
		onEdt(controller::refresh);
		assertTrue(executor.tasks.isEmpty());
		activate();
		assertEquals(1, executor.tasks.size());
		onEdt(() -> assertEquals(Phase.LOADING, last().phase()));
	}

	@Test
	void anEmptyListIsAnExplicitStateWithNoBusinessScope() throws Exception {
		activate();
		runRequest();
		onEdt(() -> {
			assertEquals(Phase.EMPTY, last().phase());
			assertTrue(context.structures().isEmpty());
			assertFalse(context.canStartBusinessOperation());
		});
		assertTrue(notificationsOnEdt.get());
		assertFalse(connection.networkOnEdt.get());
	}

	@Test
	void oneStructureIsSelectedAutomaticallyAndCountdownUpdatesDoNotReloadIt() throws Exception {
		load(List.of(FIRST));
		onEdt(() -> {
			assertEquals(FIRST, context.selectedStructure().orElseThrow());
			controller.onSessionStatus(new SessionStatus(SessionStatus.State.EXPIRING, 90));
			controller.onSessionStatus(new SessionStatus(SessionStatus.State.EXPIRING, 89));
			assertTrue(context.canStartBusinessOperation());
		});
		assertTrue(executor.tasks.isEmpty());
		assertEquals(1, connection.listCalls.get());
	}

	@Test
	void multipleChoicesClearTheOldScopeBeforeVerifyingTheNextChoice() throws Exception {
		load(List.of(FIRST, SECOND));
		onEdt(() -> assertTrue(context.selectedStructure().isEmpty()));
		select(FIRST);
		onEdt(() -> {
			assertEquals(FIRST, context.selectedStructure().orElseThrow());
			controller.selectStructure(SECOND.structureID());
			assertTrue(context.selectedStructure().isEmpty());
			assertFalse(context.canStartBusinessOperation());
			assertEquals(Phase.VALIDATING, last().phase());
		});
		runRequest();
		onEdt(() -> assertEquals(SECOND, context.selectedStructure().orElseThrow()));
	}

	@Test
	void onlyTheLatestSelectionCanPublishEvenIfTheFormerReplyIsAlreadyQueued() throws Exception {
		load(List.of(FIRST, SECOND));
		onEdt(() -> {
			controller.selectStructure(FIRST.structureID());
			Thread worker = new Thread(executor.tasks.remove(), "fictitious-late-structure-response");
			worker.start();
			try {
				worker.join(2_000);
			} catch (InterruptedException failure) {
				throw new AssertionError(failure);
			}
			assertFalse(worker.isAlive());
			controller.selectStructure(SECOND.structureID());
		});
		runRequest();
		onEdt(() -> assertEquals(SECOND, context.selectedStructure().orElseThrow()));
	}

	@Test
	void aRemovedAccessRefreshesTheListAndUsesASingleNonRevealingMessage() throws Exception {
		load(List.of(FIRST, SECOND));
		connection.detail = Optional.empty();
		onEdt(() -> controller.selectStructure(FIRST.structureID()));
		runRequest();
		connection.structures = List.of(SECOND);
		runRequest();
		onEdt(() -> {
			assertEquals(List.of(SECOND), context.structures());
			assertEquals(SECOND, context.selectedStructure().orElseThrow());
			assertEquals("Vous n’avez pas accès à cette structure.", last().message());
		});
	}

	@Test
	void refreshingRevalidatesTheScopeAndDoesNotKeepARemovedSelection() throws Exception {
		load(List.of(FIRST, SECOND));
		select(FIRST);
		connection.structures = List.of();
		onEdt(() -> {
			controller.refresh();
			assertTrue(context.selectedStructure().isEmpty());
			assertTrue(context.structures().isEmpty());
		});
		runRequest();
		onEdt(() -> assertEquals(Phase.EMPTY, last().phase()));
	}

	@Test
	void aNetworkFailureRemovesTheScopeAndAnExplicitRetryCanRecoverIt() throws Exception {
		load(List.of(FIRST));
		connection.failure = new IOException("Fictitious private network diagnostic");
		onEdt(controller::refresh);
		runRequest();
		onEdt(() -> {
			assertEquals(Phase.FAILED, last().phase());
			assertFalse(last().message().contains("diagnostic"));
			assertFalse(context.canStartBusinessOperation());
		});
		connection.failure = null;
		onEdt(controller::refresh);
		runRequest();
		onEdt(() -> assertEquals(FIRST, context.selectedStructure().orElseThrow()));
	}

	@Test
	void suspensionCancelsRequestsAndRecoveryRequiresAFreshList() throws Exception {
		load(List.of(FIRST));
		onEdt(() -> {
			context.setAvailable(false);
			controller.onSessionStatus(new SessionStatus(SessionStatus.State.UNAVAILABLE, 120));
			assertTrue(context.structures().isEmpty());
			assertEquals(Phase.SUSPENDED, last().phase());
		});
		connection.structures = List.of(SECOND);
		activate();
		runRequest();
		onEdt(() -> assertEquals(SECOND, context.selectedStructure().orElseThrow()));
	}

	@Test
	void closureDropsAQueuedResponseInsteadOfRepopulatingAClosedSession() throws Exception {
		connection.structures = List.of(FIRST);
		activate();
		onEdt(() -> {
			Thread worker = new Thread(executor.tasks.remove(), "fictitious-closed-session-response");
			worker.start();
			try {
				worker.join(2_000);
			} catch (InterruptedException failure) {
				throw new AssertionError(failure);
			}
			assertFalse(worker.isAlive());
			context.close();
		});
		onEdt(() -> {
			assertTrue(context.structures().isEmpty());
			assertTrue(context.selectedStructure().isEmpty());
		});
		assertTrue(executor.isShutdown());
	}

	@Test
	void aForeignIdentifierNeverStartsARequestOrBecomesSelected() throws Exception {
		load(List.of(FIRST, SECOND));
		onEdt(() -> controller.selectStructure(UUID.randomUUID()));
		assertTrue(executor.tasks.isEmpty());
		assertTrue(context.selectedStructure().isEmpty());
		assertEquals(0, connection.detailCalls.get());
	}

	private void activate() throws Exception {
		onEdt(() -> {
			context.setAvailable(true);
			controller.onSessionStatus(new SessionStatus(SessionStatus.State.ACTIVE, 300));
		});
	}

	private void load(List<StructureSummary> structures) throws Exception {
		connection.structures = structures;
		activate();
		runRequest();
	}

	private void select(StructureSummary structure) throws Exception {
		onEdt(() -> controller.selectStructure(structure.structureID()));
		runRequest();
	}

	private void runRequest() throws Exception {
		executor.tasks.remove().run();
		onEdt(() -> {
		});
	}

	private StructureSelectionState last() {
		return changes.getLast();
	}

	private static final class StructureConnection extends ServerConnection {
		private List<StructureSummary> structures = List.of();
		private Optional<StructureSummary> detail;
		private IOException failure;
		private final AtomicInteger listCalls = new AtomicInteger();
		private final AtomicInteger detailCalls = new AtomicInteger();
		private final AtomicBoolean networkOnEdt = new AtomicBoolean();

		private StructureConnection() {
			super(URI.create("http://127.0.0.1:1"));
		}

		@Override
		public List<StructureSummary> fetchAccessibleStructures() throws IOException {
			networkOnEdt.compareAndSet(false, SwingUtilities.isEventDispatchThread());
			listCalls.incrementAndGet();
			if (failure != null) {
				throw failure;
			}
			return structures;
		}

		@Override
		public Optional<StructureSummary> fetchAccessibleStructure(UUID id) {
			networkOnEdt.compareAndSet(false, SwingUtilities.isEventDispatchThread());
			detailCalls.incrementAndGet();
			return detail != null ? detail : structures.stream().filter(item -> item.structureID().equals(id))
					.findFirst();
		}
	}

	private static final class ManualExecutor extends AbstractExecutorService {
		private final Queue<Runnable> tasks = new ArrayDeque<>();
		private boolean shutdown;

		@Override
		public void execute(Runnable task) {
			tasks.add(task);
		}

		@Override
		public void shutdown() {
			shutdown = true;
		}

		@Override
		public List<Runnable> shutdownNow() {
			shutdown = true;
			List<Runnable> remaining = List.copyOf(tasks);
			tasks.clear();
			return remaining;
		}

		@Override
		public boolean isShutdown() {
			return shutdown;
		}

		@Override
		public boolean isTerminated() {
			return shutdown;
		}

		@Override
		public boolean awaitTermination(long timeout, TimeUnit unit) {
			return shutdown;
		}
	}
}
