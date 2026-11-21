package gestion.app;

public record SessionStatus(State state, long remainingSeconds) {

	public enum State {
		CHECKING, ACTIVE, EXPIRING, UNAVAILABLE, CLOSING
	}

	public boolean allowsBusinessOperations() {
		return state == State.ACTIVE || state == State.EXPIRING;
	}
}
