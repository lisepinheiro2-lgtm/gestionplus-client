package gestion.app;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.SwingUtilities;

public class SessionCoordinator {

	private ExecutorService executor = Executors.newSingleThreadExecutor();
	private final ConnectedApplicationContext context;
	private Runnable onSessionEnded;
	private boolean lastLogoutConfirmed;

	public SessionCoordinator(ConnectedApplicationContext context, Runnable onSessionEnded) {
		this.context = Objects.requireNonNull(context, "Le contexte ne peut pas être null.");
		Objects.requireNonNull(onSessionEnded, "L'action de déconection ne peut pas être null.");
	}

	private boolean attemptLogout() {

		try {
			context.connection().logout();
			return true;
		} catch (IOException | InterruptedException e) {

			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}

			return false;
		}

	}

	public void requestLogout(boolean quitAfter) {

		if (!context.isActive()) {
			return;
		}

		context.beginClosing();

		executor.submit(() -> {
			boolean serverConfirmed = attemptLogout();
			SwingUtilities.invokeLater(() -> finishLogout(serverConfirmed, quitAfter));
		});
	}

	private void finishLogout(boolean serverConfirmed, boolean quitAfter) {

		lastLogoutConfirmed = serverConfirmed;
		context.close();
		executor.shutdownNow();

		if (quitAfter) {
			System.exit(0);
		} else {
			onSessionEnded.run();
		}

	}
}
