package gestion.app;

import java.util.Objects;
import java.util.UUID;
import gestion.net.ServerConnection.CurrentUser;
import gestion.net.ServerConnection;

public class ConnectedApplicationContext implements AutoCloseable {

	private final UUID contextId = UUID.randomUUID();
	private final ServerConnection connection;
	private final CurrentUser user;
	private SessionState state = SessionState.ACTIVE;

	public ConnectedApplicationContext(ServerConnection connection, CurrentUser user) {
		this.connection = Objects.requireNonNull(connection, "La connexion ne peut pas être null.");
		this.user = Objects.requireNonNull(user, "L'utilisateur ne peut pas être null.");
	}

	public SessionState sessionState() {
		return state;
	}

	public void beginClosing() {

		if (state == SessionState.CLOSED) {
			throw new IllegalStateException("Le contexte est déjà fermé.");
		}

		state = SessionState.CLOSING;
	}

	@Override
	public void close() {

		if (state == SessionState.CLOSED) {
			return;
		}

		connection.close();
		state = SessionState.CLOSED;
	}

	public boolean isActive() {
		return state == SessionState.ACTIVE;
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
