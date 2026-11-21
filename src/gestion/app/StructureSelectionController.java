package gestion.app;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

import gestion.app.StructureSelectionState.Phase;
import gestion.net.StructureSummary;

public final class StructureSelectionController implements AutoCloseable {

	private final ConnectedApplicationContext context;
	private final Consumer<StructureSelectionState> onChange;
	private final ExecutorService executor;
	private Future<?> pending;
	private long generation;
	private boolean sessionAvailable;
	private volatile boolean closed;

	public StructureSelectionController(ConnectedApplicationContext context,
			Consumer<StructureSelectionState> onChange) {
		this(context, onChange, Executors.newSingleThreadExecutor());
	}

	StructureSelectionController(ConnectedApplicationContext context, Consumer<StructureSelectionState> onChange,
			ExecutorService executor) {
		this.context = Objects.requireNonNull(context);
		this.onChange = Objects.requireNonNull(onChange);
		this.executor = Objects.requireNonNull(executor);
		context.onClose(this::close);
	}

	public void onSessionStatus(SessionStatus status) {
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(() -> onSessionStatus(status));
			return;
		}
		if (closed) {
			return;
		}
		boolean available = status.allowsBusinessOperations() && context.isSessionAvailable();
		if (!available) {
			sessionAvailable = false;
			cancelPending();
			context.clearStructures();
			publish(Phase.SUSPENDED, null, "Les structures sont indisponibles pendant la vérification de session.");
		} else if (!sessionAvailable) {
			sessionAvailable = true;
			refresh();
		}
	}

	public void refresh() {
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(this::refresh);
			return;
		}
		if (!canRequest()) {
			return;
		}
		UUID preferredID = context.selectedStructure().map(StructureSummary::structureID).orElse(null);
		loadStructures(preferredID, "");
	}

	private void loadStructures(UUID preferredID, String message) {
		cancelPending();
		context.clearStructures();
		publish(Phase.LOADING, null, "Chargement des structures accessibles…");
		long requestGeneration = generation;
		submit(() -> {
			try {
				List<StructureSummary> structures = context.connection().fetchAccessibleStructures();
				SwingUtilities.invokeLater(() -> {
					if (!isCurrent(requestGeneration)) {
						return;
					}
					pending = null;
					context.replaceStructures(structures, preferredID);
					publish(structures.isEmpty() ? Phase.EMPTY : Phase.READY,
							context.selectedStructure().map(StructureSummary::structureID).orElse(null), message);
				});
			} catch (InterruptedException failure) {
				Thread.currentThread().interrupt();
				failed(requestGeneration);
			} catch (IOException | RuntimeException failure) {
				failed(requestGeneration);
			}
		});
	}

	public void selectStructure(UUID structureID) {
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(() -> selectStructure(structureID));
			return;
		}
		if (!canRequest()) {
			return;
		}
		if (structureID != null && context.structures().stream()
				.noneMatch(item -> item.structureID().equals(structureID))) {
			return;
		}
		cancelPending();
		context.clearSelection();
		if (structureID == null) {
			publish(Phase.READY, null, "");
			return;
		}
		publish(Phase.VALIDATING, structureID, "Vérification de l’accès à la structure…");
		long requestGeneration = generation;
		submit(() -> {
			try {
				Optional<StructureSummary> structure = context.connection().fetchAccessibleStructure(structureID);
				SwingUtilities.invokeLater(() -> {
					if (!isCurrent(requestGeneration)) {
						return;
					}
					pending = null;
					if (structure.isEmpty()) {
						loadStructures(null, "Vous n’avez pas accès à cette structure.");
					} else {
						List<StructureSummary> updated = context.structures().stream()
								.map(item -> item.structureID().equals(structureID) ? structure.get() : item).toList();
						context.replaceStructures(updated, structureID);
						publish(Phase.READY, structureID, "");
					}
				});
			} catch (InterruptedException failure) {
				Thread.currentThread().interrupt();
				failed(requestGeneration);
			} catch (IOException | RuntimeException failure) {
				failed(requestGeneration);
			}
		});
	}

	private void submit(Runnable task) {
		try {
			pending = executor.submit(task);
		} catch (RejectedExecutionException failure) {
			failed(generation);
		}
	}

	private void failed(long requestGeneration) {
		SwingUtilities.invokeLater(() -> {
			if (!isCurrent(requestGeneration)) {
				return;
			}
			pending = null;
			context.clearStructures();
			publish(Phase.FAILED, null, "Impossible de charger les structures. Veuillez réessayer.");
		});
	}

	private boolean canRequest() {
		return !closed && sessionAvailable && context.isSessionAvailable();
	}

	private boolean isCurrent(long requestGeneration) {
		return canRequest() && requestGeneration == generation;
	}

	private void publish(Phase phase, UUID selectionID, String message) {
		onChange.accept(new StructureSelectionState(phase, context.structures(), selectionID, message));
	}

	private void cancelPending() {
		generation++;
		if (pending != null) {
			pending.cancel(true);
			pending = null;
		}
	}

	@Override
	public void close() {
		closed = true;
		executor.shutdownNow();
		if (SwingUtilities.isEventDispatchThread()) {
			cancelPending();
		} else {
			SwingUtilities.invokeLater(this::cancelPending);
		}
	}
}
