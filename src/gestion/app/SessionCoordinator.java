package gestion.app;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

public class SessionCoordinator {

	public record LogoutResult(boolean serverConfirmed, boolean quitAfter) {
	}

	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private final ConnectedApplicationContext context;
	private final Consumer<LogoutResult> onSessionEnded;
	private boolean quitAfter;

	public SessionCoordinator(ConnectedApplicationContext context, Consumer<LogoutResult> onSessionEnded) {
		this.context = Objects.requireNonNull(context, "Le contexte ne peut pas être null.");
		this.onSessionEnded = Objects.requireNonNull(onSessionEnded, "L'action de déconnexion ne peut pas être null.");
	}

	private boolean attemptLogout() {

		try {
			context.connection().logout();
			return true;
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return false;
		} catch (IOException | RuntimeException exception) {
			return false;
		}

	}

	public void requestLogout(boolean quitAfter) {

		if (!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(() -> requestLogout(quitAfter));
			return;
		}

		if (context.sessionState() == ConnectedApplicationContext.SessionState.CLOSED) {
			return;
		}

		this.quitAfter |= quitAfter;

		if (!context.isActive()) {
			return;
		}

		context.beginClosing();

		executor.execute(() -> {
			boolean serverConfirmed = attemptLogout();
			SwingUtilities.invokeLater(() -> finishLogout(serverConfirmed));
		});
	}

	private void finishLogout(boolean serverConfirmed) {

		context.close();
		executor.shutdownNow();
		onSessionEnded.accept(new LogoutResult(serverConfirmed, quitAfter));

	}
}
