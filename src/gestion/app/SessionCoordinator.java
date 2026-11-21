package gestion.app;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import javax.swing.SwingUtilities;
import javax.swing.Timer;

import gestion.net.HttpApiException;
import gestion.net.SessionObservation;

public class SessionCoordinator {

	public enum EndReason {
		LOGOUT, SESSION_REJECTED, LOCAL_EXPIRATION
	}

	public record LogoutResult(boolean serverConfirmed, boolean quitAfter, EndReason reason) {
	}

	private final ExecutorService executor = Executors.newSingleThreadExecutor();
	private final ExecutorService checks;
	private final ConnectedApplicationContext context;
	private final Consumer<LogoutResult> onSessionEnded;
	private final Consumer<SessionStatus> onStatusChanged;
	private final SessionSchedule schedule;
	private final Timer timer;
	private Future<?> pendingCheck;
	private SessionStatus lastStatus;
	private boolean monitoring;
	private boolean ended;
	private boolean quitAfter;
	private long checkGeneration;

	public SessionCoordinator(ConnectedApplicationContext context, Consumer<LogoutResult> onSessionEnded) {
		this(context, onSessionEnded, status -> {
		});
	}

	public SessionCoordinator(ConnectedApplicationContext context, Consumer<LogoutResult> onSessionEnded,
			Consumer<SessionStatus> onStatusChanged) {
		this(context, onSessionEnded, onStatusChanged, System::nanoTime);
	}

	SessionCoordinator(ConnectedApplicationContext context, Consumer<LogoutResult> onSessionEnded,
			Consumer<SessionStatus> onStatusChanged, LongSupplier ticker) {
		this(context, onSessionEnded, onStatusChanged, ticker, Executors.newSingleThreadExecutor());
	}

	SessionCoordinator(ConnectedApplicationContext context, Consumer<LogoutResult> onSessionEnded,
			Consumer<SessionStatus> onStatusChanged, LongSupplier ticker, ExecutorService checks) {
		this.context = Objects.requireNonNull(context, "Le contexte ne peut pas être null.");
		this.onSessionEnded = Objects.requireNonNull(onSessionEnded, "L'action de déconnexion ne peut pas être null.");
		this.onStatusChanged = Objects.requireNonNull(onStatusChanged, "L'action de suivi ne peut pas être nulle.");
		this.checks = Objects.requireNonNull(checks);
		schedule = new SessionSchedule(ticker);
		timer = new Timer(1_000, event -> tick());
		context.onClose(this::releaseResources);
	}

	public void startMonitoring() {
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(this::startMonitoring);
			return;
		}
		if (monitoring || !context.isActive()) {
			return;
		}
		monitoring = true;
		context.connection().setSessionInvalidationHandler(this::sessionRejected);
		timer.start();
		refreshSession();
	}

	public void refreshSession() {
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(this::refreshSession);
			return;
		}
		if (!monitoring || !context.isActive() || pendingCheck != null) {
			return;
		}
		schedule.beginCheck();
		publish(schedule.status());
		long generation = ++checkGeneration;
		try {
			pendingCheck = checks.submit(() -> {
				try {
					SessionObservation observation = context.connection().fetchSessionObservation();
					long receivedAt = schedule.now();
					SwingUtilities.invokeLater(() -> checked(generation, observation, receivedAt, null));
				} catch (InterruptedException failure) {
					Thread.currentThread().interrupt();
					SwingUtilities.invokeLater(() -> checked(generation, null, 0, failure));
				} catch (IOException | RuntimeException failure) {
					SwingUtilities.invokeLater(() -> checked(generation, null, 0, failure));
				}
			});
		} catch (RejectedExecutionException failure) {
			if (context.isActive()) {
				schedule.failed();
				if (schedule.deadlineReached()) {
					endLocally();
				} else {
					publish(schedule.status());
				}
			}
		}
	}

	void tick() {
		if (!context.isActive()) {
			stopMonitoring();
			return;
		}
		if (!monitoring) {
			return;
		}
		if (schedule.shouldCheck()) {
			refreshSession();
		}
		publish(schedule.status());
	}

	private void checked(long generation, SessionObservation observation, long receivedAt, Exception failure) {
		if (!monitoring || !context.isActive() || generation != checkGeneration) {
			return;
		}
		pendingCheck = null;
		if (failure instanceof HttpApiException http && http.status() == 401) {
			sessionRejected();
			return;
		}
		if (failure == null) {
			try {
				schedule.received(Objects.requireNonNull(observation), receivedAt);
			} catch (RuntimeException invalidObservation) {
				failure = invalidObservation;
			}
		}
		if (failure != null) {
			schedule.failed();
		}
		if (schedule.deadlineReached()) {
			endLocally();
			return;
		}
		publish(schedule.status());
	}

	private void sessionRejected() {
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(this::sessionRejected);
			return;
		}
		if (context.tryBeginClosing()) {
			stopMonitoring();
			finishSession(true, EndReason.SESSION_REJECTED);
		}
	}

	private void endLocally() {
		if (!context.tryBeginClosing()) {
			return;
		}
		stopMonitoring();
		finishSession(false, EndReason.LOCAL_EXPIRATION);
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
		if (!context.tryBeginClosing()) {
			return;
		}
		stopMonitoring();
		publish(new SessionStatus(SessionStatus.State.CLOSING, 0));
		try {
			executor.execute(() -> {
				boolean serverConfirmed = attemptLogout();
				SwingUtilities.invokeLater(() -> finishSession(serverConfirmed, EndReason.LOGOUT));
			});
		} catch (RejectedExecutionException failure) {
			finishSession(false, EndReason.LOGOUT);
		}
	}

	private void finishSession(boolean serverConfirmed, EndReason reason) {
		if (ended || context.sessionState() == ConnectedApplicationContext.SessionState.CLOSED) {
			return;
		}
		ended = true;
		context.close();
		onSessionEnded.accept(new LogoutResult(serverConfirmed, quitAfter, reason));
	}

	private void publish(SessionStatus status) {
		context.setAvailable(status.allowsBusinessOperations());
		if (lastStatus != null && status.state() == lastStatus.state()
				&& status.state() != SessionStatus.State.EXPIRING) {
			return;
		}
		if (!status.equals(lastStatus)) {
			lastStatus = status;
			onStatusChanged.accept(status);
		}
	}

	private void stopMonitoring() {
		context.setAvailable(false);
		monitoring = false;
		checkGeneration++;
		timer.stop();
		if (pendingCheck != null) {
			pendingCheck.cancel(true);
			pendingCheck = null;
		}
	}

	private void releaseResources() {
		checks.shutdownNow();
		executor.shutdownNow();
		if (SwingUtilities.isEventDispatchThread()) {
			stopMonitoring();
		} else {
			SwingUtilities.invokeLater(this::stopMonitoring);
		}
	}
}
