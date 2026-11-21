package gestion.app;

import java.util.Objects;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import gestion.net.StructureSummary;
import gestion.net.ServerConnection.CurrentUser;
import gestion.net.ServerConnection;

public class ConnectedApplicationContext implements AutoCloseable {

	private final UUID contextId = UUID.randomUUID();
	private final ServerConnection connection;
	private final CurrentUser user;
	private volatile SessionState state = SessionState.ACTIVE;
	private volatile boolean available;
	private List<StructureSummary> structures = List.of();
	private StructureSummary selectedStructure;
	private final List<Runnable> closeActions = new ArrayList<>();

	public ConnectedApplicationContext(ServerConnection connection, CurrentUser user) {
		this.connection = Objects.requireNonNull(connection, "La connexion ne peut pas être null.");
		this.user = Objects.requireNonNull(user, "L'utilisateur ne peut pas être null.");
	}

	public SessionState sessionState() {
		return state;
	}

	public synchronized void beginClosing() {

		if (state == SessionState.CLOSED) {
			throw new IllegalStateException("Le contexte est déjà fermé.");
		}

		state = SessionState.CLOSING;
		clearStructures();
	}

	synchronized boolean tryBeginClosing() {
		if (!isActive()) {
			return false;
		}
		beginClosing();
		return true;
	}

	@Override
	public void close() {
		List<Runnable> actions;
		synchronized (this) {
			if (state == SessionState.CLOSED) {
				return;
			}
			state = SessionState.CLOSED;
			available = false;
			clearStructures();
			actions = List.copyOf(closeActions);
			closeActions.clear();
		}
		RuntimeException failure = null;
		try {
			for (Runnable action : actions) {
				try {
					action.run();
				} catch (RuntimeException exception) {
					if (failure == null) {
						failure = exception;
					} else {
						failure.addSuppressed(exception);
					}
				}
			}
		} finally {
			connection.close();
		}
		if (failure != null) {
			throw failure;
		}
	}

	void onClose(Runnable action) {
		Objects.requireNonNull(action);
		synchronized (this) {
			if (state != SessionState.CLOSED) {
				closeActions.add(action);
				return;
			}
		}
		action.run();
	}

	public boolean isActive() {
		return state == SessionState.ACTIVE;
	}

	public synchronized boolean canStartBusinessOperation() {
		return isSessionAvailable() && selectedStructure != null;
	}

	public boolean isSessionAvailable() {
		return isActive() && available;
	}

	public synchronized List<StructureSummary> structures() {
		return structures;
	}

	public synchronized Optional<StructureSummary> selectedStructure() {
		return Optional.ofNullable(selectedStructure);
	}

	synchronized void replaceStructures(List<StructureSummary> structures, UUID preferredID) {
		if (!isSessionAvailable()) {
			return;
		}
		this.structures = List.copyOf(structures);
		selectedStructure = this.structures.size() == 1 ? this.structures.getFirst()
				: this.structures.stream().filter(item -> item.structureID().equals(preferredID))
						.findFirst().orElse(null);
	}

	synchronized void clearSelection() {
		selectedStructure = null;
	}

	synchronized void clearStructures() {
		structures = List.of();
		selectedStructure = null;
	}

	void setAvailable(boolean available) {
		this.available = available;
	}

	public UUID contextId() {
		return contextId;

	}

	public CurrentUser user() {
		return user;

	}

	public ServerConnection connection() {
		return connection;
	}

	public enum SessionState {
		ACTIVE, CLOSING, CLOSED
	}

}
