package gestion.app;

import java.util.Objects;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import gestion.net.ServerConnection.CurrentUser;
import gestion.net.ServerConnection;

public class ConnectedApplicationContext implements AutoCloseable {

	private final UUID contextId = UUID.randomUUID();
	private final ServerConnection connection;
	private final CurrentUser user;
	private volatile SessionState state = SessionState.ACTIVE;
	private volatile boolean available;
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
			actions = List.copyOf(closeActions);
			closeActions.clear();
		}
		try {
			for (Runnable action : actions) {
				action.run();
			}
		} finally {
			connection.close();
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

	public boolean canStartBusinessOperation() {
		return isActive() && available;
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
