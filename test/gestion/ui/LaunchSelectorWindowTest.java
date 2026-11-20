package gestion.ui;

import static gestion.ui.WindowTestSupport.component;
import static gestion.ui.WindowTestSupport.duringModal;
import static gestion.ui.WindowTestSupport.onEdt;
import static gestion.ui.WindowTestSupport.pressEscape;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("desktop")
class LaunchSelectorWindowTest {

	private LaunchSelectorWindow window;
	private JButton demoButton;
	private JButton connectedButton;

	@BeforeEach
	void initialize() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless(), "Ces dialogues nécessitent un environnement graphique.");
		onEdt(() -> {
			window = new LaunchSelectorWindow();
			demoButton = component(window, JButton.class,
					button -> "Démonstration".equals(button.getAccessibleContext().getAccessibleName()));
			connectedButton = component(window, JButton.class,
					button -> "Connexion".equals(button.getAccessibleContext().getAccessibleName()));
		});
	}

	@AfterEach
	void closeWindow() throws Exception {

		if (window != null) {
			onEdt(window::dispose);
		}

	}

	@Test
	void startsModalWithTwoAccessibleModeChoices() throws Exception {
		onEdt(() -> {
			assertTrue(window.isModal());
			assertEquals(JDialog.DISPOSE_ON_CLOSE, window.getDefaultCloseOperation());
			assertTrue(demoButton.isEnabled());
			assertTrue(connectedButton.isEnabled());
			assertEquals(KeyEvent.VK_D, demoButton.getMnemonic());
			assertEquals(KeyEvent.VK_C, connectedButton.getMnemonic());
			assertFalse(demoButton.getAccessibleContext().getAccessibleDescription().isBlank());
			assertFalse(connectedButton.getAccessibleContext().getAccessibleDescription().isBlank());
		});
	}

	@Test
	void demoChoiceReturnsDemoAndDisposesTheDialog() throws Exception {
		onEdt(() -> {
			assertEquals(LaunchMode.DEMO, duringModal(window, window::askLaunchMode, () -> demoButton.doClick(0)));
			assertFalse(window.isDisplayable());
		});
	}

	@Test
	void connectedChoiceReturnsConnectedAndDisposesTheDialog() throws Exception {
		onEdt(() -> {
			assertEquals(LaunchMode.CONNECTED,
					duringModal(window, window::askLaunchMode, () -> connectedButton.doClick(0)));
			assertFalse(window.isDisplayable());
		});
	}

	@Test
	void closingTheWindowReturnsNoMode() throws Exception {
		onEdt(() -> assertNull(duringModal(window, window::askLaunchMode,
				() -> window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING)))));
	}

	@Test
	void escapeReturnsNoMode() throws Exception {
		onEdt(() -> assertNull(duringModal(window, window::askLaunchMode, () -> pressEscape(window))));
	}

	@Test
	void enterOnAChoiceUsesTheSameModeSelection() throws Exception {
		onEdt(() -> {
			Object key = connectedButton.getInputMap(JComponent.WHEN_FOCUSED)
					.get(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
			assertNotNull(key);
			var action = connectedButton.getActionMap().get(key);
			assertNotNull(action);
			assertEquals(LaunchMode.CONNECTED, duringModal(window, window::askLaunchMode,
					() -> action
							.actionPerformed(new ActionEvent(connectedButton, ActionEvent.ACTION_PERFORMED, "enter"))));
		});
	}

	@Test
	void reopeningThenClosingDoesNotReuseThePreviousChoice() throws Exception {
		onEdt(() -> {
			assertEquals(LaunchMode.DEMO, duringModal(window, window::askLaunchMode, () -> demoButton.doClick(0)));
			assertNull(duringModal(window, window::askLaunchMode, () -> pressEscape(window)));
		});
	}
}
