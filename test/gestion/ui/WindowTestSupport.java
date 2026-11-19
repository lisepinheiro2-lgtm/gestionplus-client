package gestion.ui;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

final class WindowTestSupport {

	private WindowTestSupport() {
	}

	static void onEdt(Runnable action) throws Exception {
		if (SwingUtilities.isEventDispatchThread()) {
			action.run();
			return;
		}

		try {
			SwingUtilities.invokeAndWait(action);
		} catch (InvocationTargetException failure) {
			rethrow(failure.getCause());
		}
	}

	static <T extends Component> T component(Container root, Class<T> type, Predicate<T> condition) {
		return descendants(root).filter(type::isInstance).map(type::cast).filter(condition).findFirst()
				.orElseThrow(() -> new AssertionError("Composant introuvable : " + type.getSimpleName()));
	}

	static <T extends Component> T named(Container root, Class<T> type, String name) {
		return component(root, type, candidate -> candidate.getAccessibleContext() != null
				&& name.equals(candidate.getAccessibleContext().getAccessibleName()));
	}

	static void pressEscape(JDialog window) {
		Object key = window.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
				.get(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
		assertNotNull(key);
		Action action = window.getRootPane().getActionMap().get(key);
		assertNotNull(action);
		action.actionPerformed(new ActionEvent(window, ActionEvent.ACTION_PERFORMED, "escape"));
	}

	static <T> T duringModal(JDialog window, Supplier<T> show, Runnable action) {
		AtomicReference<Throwable> failure = new AtomicReference<>();
		Timer guard = new Timer(10_000, event -> {
			failure.compareAndSet(null, new AssertionError("Le dialogue modal ne s'est pas refermé."));
			window.dispose();
		});
		guard.setRepeats(false);
		SwingUtilities.invokeLater(() -> {
			try {
				action.run();
			} catch (Throwable thrown) {
				failure.compareAndSet(null, thrown);
			} finally {
				window.dispose();
			}
		});
		guard.start();

		try {
			T result = show.get();
			if (failure.get() != null) {
				rethrow(failure.get());
			}
			return result;
		} finally {
			guard.stop();
			window.dispose();
		}
	}

	private static Stream<Component> descendants(Component root) {
		return Stream.concat(Stream.of(root), root instanceof Container container
				? Arrays.stream(container.getComponents()).flatMap(WindowTestSupport::descendants) : Stream.empty());
	}

	private static void rethrow(Throwable failure) {
		if (failure instanceof Error error) {
			throw error;
		}
		if (failure instanceof RuntimeException exception) {
			throw exception;
		}
		throw new AssertionError(failure);
	}
}
