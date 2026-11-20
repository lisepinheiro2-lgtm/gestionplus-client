package gestion.ui;

import static gestion.ui.WindowTestSupport.component;
import static gestion.ui.WindowTestSupport.named;
import static gestion.ui.WindowTestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.BorderLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import gestion.app.ConnectedApplicationContext;
import gestion.net.ServerConnection;
import gestion.net.ServerConnection.CurrentUser;

@Tag("desktop")
class ConnectedHomeWindowTest {

	private ConnectedApplicationContext context;
	private ConnectedHomeWindow window;
	private JFrame frame;
	private final AtomicInteger accountRequests = new AtomicInteger();
	private final AtomicInteger closeRequests = new AtomicInteger();

	@BeforeEach
	void initialize() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless(), "Ces fenêtres nécessitent un environnement graphique.");
		context = new ConnectedApplicationContext(new ServerConnection(URI.create("http://127.0.0.1:1")),
				new CurrentUser("fictitious_confirmed_user"));
		onEdt(() -> {
			Set<Window> previous = Set.copyOf(Arrays.asList(Window.getWindows()));
			window = new ConnectedHomeWindow(context, accountRequests::incrementAndGet, closeRequests::incrementAndGet);
			frame = Arrays.stream(Window.getWindows()).filter(JFrame.class::isInstance).map(JFrame.class::cast)
					.filter(candidate -> !previous.contains(candidate)).findFirst().orElseThrow();
		});
	}

	@AfterEach
	void closeWindowAndContext() throws Exception {
		onEdt(() -> {

			if (window != null) {
				window.dispose();
			}

			if (context != null) {
				context.close();
			}

		});
	}

	@Test
	void reusesHomePanelAndDisplaysTheConfirmedIdentityInTheSharedHeader() throws Exception {
		onEdt(() -> {
			BorderLayout layout = assertInstanceOf(BorderLayout.class, frame.getContentPane().getLayout());
			assertInstanceOf(HomePanel.class, layout.getLayoutComponent(BorderLayout.CENTER));
			assertInstanceOf(ApplicationHeader.class, layout.getLayoutComponent(BorderLayout.NORTH));
			JLabel identity = named(frame, JLabel.class, "Utilisateur connecté : fictitious_confirmed_user");
			assertEquals("fictitious_confirmed_user", identity.getText());
			assertEquals("Connecté en tant que fictitious_confirmed_user", identity.getToolTipText());
			assertEquals("Gestion +", frame.getTitle());
			assertFalse(frame.getIconImages().isEmpty());
		});
	}

	@Test
	void keepsUnconnectedBusinessCardsDisabledInsteadOfOpeningCsvScreens() throws Exception {
		onEdt(() -> {
			HomePanel home = component(frame, HomePanel.class, candidate -> true);

			for (String title : new String[] { "Personnel", "Clients", "Planning", "Remplacements", "Interventions",
					"Archives" }) {
				HomeCardButton card = named(home, HomeCardButton.class, title);
				assertFalse(card.isEnabled(), title);
				assertFalse(card.isFocusable(), title);
			}

			assertTrue(context.isActive());
			assertEquals(0, accountRequests.get());
			assertEquals(0, closeRequests.get());
		});
	}

	@Test
	void exposesTheAccountActionToKeyboardFocusAndOnlyDelegatesTheRequest() throws Exception {
		onEdt(() -> {
			window.show();
			JButton account = named(frame, JButton.class, "Compte utilisateur");
			assertTrue(account.isEnabled());
			assertTrue(account.isFocusable());
			account.doClick(0);
			assertEquals(1, accountRequests.get());
			assertEquals(0, closeRequests.get());
			assertTrue(context.isActive());
			assertTrue(frame.isShowing());
		});
	}

	@Test
	void closingTheWindowRequestsApplicationClosureWithoutDisposingOrClosingTheContext() throws Exception {
		onEdt(() -> {
			window.show();
			assertEquals(JFrame.DO_NOTHING_ON_CLOSE, frame.getDefaultCloseOperation());
			frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
			assertEquals(1, closeRequests.get());
			assertEquals(0, accountRequests.get());
			assertTrue(frame.isDisplayable());
			assertTrue(context.isActive());
		});
	}

	@Test
	void disposalDoesNotSendLogoutOrInvokeTheUserActions() throws Exception {
		onEdt(() -> {
			window.show();
			window.dispose();
			window.dispose();
			assertFalse(frame.isDisplayable());
			assertEquals(0, accountRequests.get());
			assertEquals(0, closeRequests.get());
			assertTrue(context.isActive());
		});
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void identityAndAccountActionRemainAvailableInBothThemes(boolean dark) throws Exception {
		onEdt(() -> {
			ApplicationHeader header = component(frame, ApplicationHeader.class, candidate -> true);
			HomePanel home = component(frame, HomePanel.class, candidate -> true);
			header.applyAppTheme(dark);
			home.applyAppTheme(dark);
			JLabel identity = named(header, JLabel.class, "Utilisateur connecté : fictitious_confirmed_user");
			assertEquals("fictitious_confirmed_user", identity.getText());
			assertNotNull(identity.getForeground());
			JButton account = named(header, JButton.class, "Compte utilisateur");
			assertTrue(account.isEnabled());
			assertTrue(account.isFocusable());
			account.doClick(0);
			assertEquals(1, accountRequests.get());
		});
	}

	@Test
	void requiresTheContextAndBothUserActionsBeforeCreatingAWindow() throws Exception {
		onEdt(() -> {
			assertThrows(NullPointerException.class, () -> new ConnectedHomeWindow(null, () -> {
			}, () -> {
			}));
			assertThrows(NullPointerException.class, () -> new ConnectedHomeWindow(context, null, () -> {
			}));
			assertThrows(NullPointerException.class, () -> new ConnectedHomeWindow(context, () -> {
			}, null));
		});
	}
}
