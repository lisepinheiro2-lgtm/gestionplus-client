package gestion.ui;

import static gestion.ui.WindowTestSupport.component;
import static gestion.ui.WindowTestSupport.duringModal;
import static gestion.ui.WindowTestSupport.named;
import static gestion.ui.WindowTestSupport.onEdt;
import static gestion.ui.WindowTestSupport.pressEscape;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JButton;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("desktop")
class LoginWindowTest {

	private static final String FICTITIOUS_PASSWORD = "FictitiousPassword123!";

	private final AtomicInteger submissions = new AtomicInteger();
	private LoginWindow window;
	private JTextField username;
	private JPasswordField password;
	private JTextField totp;
	private JButton submit;
	private JButton back;
	private JTextArea status;
	private JProgressBar progress;

	@BeforeEach
	void initialize() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless(), "Ces dialogues nécessitent un environnement graphique.");
		onEdt(() -> {
			window = new LoginWindow();
			username = named(window, JTextField.class, "Identifiant");
			password = named(window, JPasswordField.class, "Mot de passe");
			totp = named(window, JTextField.class, "Code d’authentification");
			submit = component(window, JButton.class, button -> "Se connecter".equals(button.getText()));
			back = component(window, JButton.class, button -> "Retour au choix du mode".equals(button.getText()));
			status = named(window, JTextArea.class, "État de la connexion");
			progress = named(window, JProgressBar.class, "Connexion en cours");
		});
	}

	@AfterEach
	void closeWindow() throws Exception {

		if (window != null) {
			onEdt(window::dispose);
		}

	}

	@Test
	void keepsSubmissionUnavailableUntilAnActionIsConnected() throws Exception {
		onEdt(() -> {
			assertTrue(window.isModal());
			assertFalse(submit.isEnabled());
			assertTrue(back.isEnabled());
			assertTrue(totp.isVisible());
			assertFalse(totp.getAccessibleContext().getAccessibleDescription().isBlank());
			assertTrue(password.getEchoChar() != 0);
			assertFalse(progress.isVisible());
			assertEquals("La connexion n’est pas encore disponible.", status.getText());
		});
	}

	@Test
	void submitsAllThreeValuesOnTheSwingThreadWithoutChangingLeadingZeroes() throws Exception {
		onEdt(() -> {
			window.setLoginAction(() -> {
				assertTrue(SwingUtilities.isEventDispatchThread());
				assertEquals("fictitious_user", window.getUsername());
				assertEquals("001234", window.getTotpCode());
				char[] supplied = window.getPassword();

				try {
					assertArrayEquals(FICTITIOUS_PASSWORD.toCharArray(), supplied);
				} finally {
					Arrays.fill(supplied, '\0');
				}

				submissions.incrementAndGet();
			});
			setInputs("fictitious_user", FICTITIOUS_PASSWORD, "001234");
			submit.doClick(0);
			assertEquals(1, submissions.get());
		});
	}

	@Test
	void acceptsAnEmptyOptionalTotpCode() throws Exception {
		onEdt(() -> {
			window.setLoginAction(submissions::incrementAndGet);
			setInputs("fictitious_user", FICTITIOUS_PASSWORD, "");
			submit.doClick(0);
			assertEquals(1, submissions.get());
			assertEquals("", window.getTotpCode());
		});
	}

	@Test
	void acceptsTheDocumentedUsernameAndPasswordLengthBoundaries() throws Exception {
		onEdt(() -> {
			window.setLoginAction(submissions::incrementAndGet);
			setInputs("u".repeat(255), "p".repeat(128), "000123");
			submit.doClick(0);
			assertEquals(1, submissions.get());
			assertEquals(255, window.getUsername().length());
			assertEquals(128, window.getPassword().length);
		});
	}

	@Test
	void refusesBlankUsernamesBeforeCallingTheAction() throws Exception {
		onEdt(() -> {
			window.setLoginAction(submissions::incrementAndGet);

			for (String value : List.of("", "   ", "\t")) {
				setInputs(value, FICTITIOUS_PASSWORD, "123456");
				submit.doClick(0);
				assertEquals(0, submissions.get());
				assertEquals("Saisissez votre identifiant.", status.getText());
				assertFalse(progress.isVisible());
			}

		});
	}

	@Test
	void refusesAnOversizedUsernameWithoutTruncatingIt() throws Exception {
		onEdt(() -> {
			window.setLoginAction(submissions::incrementAndGet);
			String value = "u".repeat(256);
			setInputs(value, FICTITIOUS_PASSWORD, "123456");
			submit.doClick(0);
			assertEquals(0, submissions.get());
			assertEquals(value, window.getUsername());
			assertEquals("L’identifiant ne doit pas dépasser 255 caractères.", status.getText());
			assertTrue(username.isEnabled());
		});
	}

	@Test
	void refusesAnEmptyPasswordBeforeCallingTheAction() throws Exception {
		onEdt(() -> {
			window.setLoginAction(submissions::incrementAndGet);
			setInputs("fictitious_user", "", "123456");
			submit.doClick(0);
			assertEquals(0, submissions.get());
			assertEquals("Saisissez votre mot de passe.", status.getText());
			assertTrue(submit.isEnabled());
		});
	}

	@Test
	void refusesAnOversizedPasswordWithoutTruncatingOrClearingIt() throws Exception {
		onEdt(() -> {
			window.setLoginAction(submissions::incrementAndGet);
			String value = "p".repeat(129);
			setInputs("fictitious_user", value, "123456");
			submit.doClick(0);
			assertEquals(0, submissions.get());
			assertArrayEquals(value.toCharArray(), window.getPassword());
			assertEquals("Le mot de passe ne doit pas dépasser 128 caractères.", status.getText());
			assertTrue(password.isEnabled());
		});
	}

	@Test
	void refusesMalformedTotpCodesWithoutSilentlyRewritingThem() throws Exception {
		onEdt(() -> {
			window.setLoginAction(submissions::incrementAndGet);

			for (String value : List.of("12345", "1234567", "12a456", "１２３４５６", " 12345", "12345 ")) {
				setInputs("fictitious_user", FICTITIOUS_PASSWORD, value);
				submit.doClick(0);
				assertEquals(0, submissions.get(), value);
				assertEquals(value, window.getTotpCode());
				assertEquals("Le code d’authentification doit contenir exactement 6 chiffres.", status.getText());
				assertTrue(totp.isEnabled());
			}

		});
	}

	@Test
	void blocksDuplicateSubmissionsAndEditingWhileWaiting() throws Exception {
		onEdt(() -> {
			window.setLoginAction(submissions::incrementAndGet);
			setInputs("fictitious_user", FICTITIOUS_PASSWORD, "123456");
			submit.doClick(0);
			assertEquals(1, submissions.get());
			assertFalse(username.isEnabled());
			assertFalse(password.isEnabled());
			assertFalse(totp.isEnabled());
			assertFalse(submit.isEnabled());
			assertFalse(back.isEnabled());
			assertTrue(progress.isVisible());
			assertTrue(progress.isIndeterminate());
			assertEquals("Connexion en cours…", status.getText());
			submit.doClick(0);
			assertEquals(1, submissions.get());
		});
	}

	@Test
	void rejectionClearsSensitiveFieldsKeepsTheUsernameAndAllowsAnotherAttempt() throws Exception {
		onEdt(() -> {
			window.setLoginAction(submissions::incrementAndGet);
			setInputs("fictitious_user", FICTITIOUS_PASSWORD, "123456");
			submit.doClick(0);
			window.showAuthenticationRejected();
			assertEquals("Identifiant, mot de passe ou code d’authentification incorrect.", status.getText());
			assertEquals("fictitious_user", window.getUsername());
			assertSensitiveFieldsEmpty();
			assertTrue(username.isEnabled());
			assertTrue(password.isEnabled());
			assertTrue(totp.isEnabled());
			assertTrue(submit.isEnabled());
			assertTrue(back.isEnabled());
			assertFalse(progress.isIndeterminate());
			setInputs("fictitious_user", FICTITIOUS_PASSWORD, "654321");
			submit.doClick(0);
			assertEquals(2, submissions.get());
		});
	}

	@Test
	void serverErrorDisplaysPlainTextAndRestoresTheFormWithoutSensitiveValues() throws Exception {
		onEdt(() -> {
			window.setLoginAction(submissions::incrementAndGet);
			setInputs("fictitious_user", FICTITIOUS_PASSWORD, "123456");
			submit.doClick(0);
			window.showError("Serveur indisponible. <Réessayez plus tard>.");
			assertEquals("Serveur indisponible. <Réessayez plus tard>.", status.getText());
			assertSensitiveFieldsEmpty();
			assertTrue(submit.isEnabled());
			assertFalse(progress.isVisible());
		});
	}

	@Test
	void successClearsSensitiveFieldsAndBlocksAnotherSubmission() throws Exception {
		onEdt(() -> {
			window.setLoginAction(submissions::incrementAndGet);
			setInputs("fictitious_user", FICTITIOUS_PASSWORD, "123456");
			submit.doClick(0);
			window.showSuccess();
			assertEquals("Connexion réussie.", status.getText());
			assertSensitiveFieldsEmpty();
			assertFalse(submit.isEnabled());
			assertFalse(username.isEnabled());
			assertFalse(password.isEnabled());
			assertFalse(totp.isEnabled());
			assertFalse(progress.isVisible());
			assertTrue(window.isDisplayable());
			submit.doClick(0);
			assertEquals(1, submissions.get());
		});
	}

	@Test
	void callbackFailureRestoresTheFormWithoutDisplayingItsDetails() throws Exception {
		onEdt(() -> {
			window.setLoginAction(() -> {
				throw new IllegalStateException("Fictitious private diagnostic");
			});
			setInputs("fictitious_user", FICTITIOUS_PASSWORD, "123456");
			submit.doClick(0);
			assertEquals("Impossible de démarrer la connexion. Veuillez réessayer.", status.getText());
			assertSensitiveFieldsEmpty();
			assertTrue(submit.isEnabled());
			assertFalse(progress.isVisible());
		});
	}

	@Test
	void removingTheActionDisablesSubmissionAgain() throws Exception {
		onEdt(() -> {
			window.setLoginAction(submissions::incrementAndGet);
			assertTrue(submit.isEnabled());
			window.setLoginAction(null);
			assertFalse(submit.isEnabled());
			assertEquals("La connexion n’est pas encore disponible.", status.getText());
		});
	}

	@Test
	void passwordGetterReturnsAnIndependentCharacterArray() throws Exception {
		onEdt(() -> {
			setInputs("fictitious_user", FICTITIOUS_PASSWORD, "123456");
			char[] copy = window.getPassword();
			Arrays.fill(copy, '\0');
			char[] current = window.getPassword();

			try {
				assertArrayEquals(FICTITIOUS_PASSWORD.toCharArray(), current);
			} finally {
				Arrays.fill(current, '\0');
			}

		});
	}

	@Test
	void enterInATextFieldUsesTheSubmissionAction() throws Exception {
		onEdt(() -> {
			window.setLoginAction(submissions::incrementAndGet);
			setInputs("fictitious_user", FICTITIOUS_PASSWORD, "001234");
			totp.postActionEvent();
			assertEquals(1, submissions.get());
		});
	}

	@Test
	void returningToModeSelectionClearsAllFieldsAndDisposesTheDialog() throws Exception {
		onEdt(() -> {
			setInputs("fictitious_user", FICTITIOUS_PASSWORD, "123456");
			back.doClick(0);
			assertTrue(window.isModeSelectionRequested());
			assertEquals("", window.getUsername());
			assertSensitiveFieldsEmpty();
			assertFalse(window.isDisplayable());
		});
	}

	@Test
	void windowClosingClearsAllFieldsWithoutRequestingAnotherMode() throws Exception {
		onEdt(() -> {
			setInputs("fictitious_user", FICTITIOUS_PASSWORD, "123456");
			window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING));
			assertFalse(window.isModeSelectionRequested());
			assertEquals("", window.getUsername());
			assertSensitiveFieldsEmpty();
			assertFalse(window.isDisplayable());
		});
	}

	@Test
	void escapeClearsAllFieldsAndClosesTheDialog() throws Exception {
		onEdt(() -> {
			setInputs("fictitious_user", FICTITIOUS_PASSWORD, "123456");
			pressEscape(window);
			assertEquals("", window.getUsername());
			assertSensitiveFieldsEmpty();
			assertFalse(window.isModeSelectionRequested());
			assertFalse(window.isDisplayable());
		});
	}

	@Test
	void lateResultsAfterClosingDoNotReopenTheDialog() throws Exception {
		onEdt(() -> {
			window.setLoginAction(submissions::incrementAndGet);
			setInputs("fictitious_user", FICTITIOUS_PASSWORD, "123456");
			submit.doClick(0);
			window.dispose();
			window.showSuccess();
			window.showAuthenticationRejected();
			window.showError("Résultat tardif fictif");
			assertFalse(window.isDisplayable());
			assertFalse(window.isVisible());
			assertSensitiveFieldsEmpty();
		});
	}

	@Test
	void reopeningResetsThePreviousModeRequestAndCredentials() throws Exception {
		onEdt(() -> {
			window.setLoginAction(submissions::incrementAndGet);
			setInputs("fictitious_user", FICTITIOUS_PASSWORD, "123456");
			back.doClick(0);
			assertTrue(window.isModeSelectionRequested());
			duringModal(window, () -> {
				window.showLogin();
				return null;
			}, () -> {
				assertFalse(window.isModeSelectionRequested());
				assertEquals("", window.getUsername());
				assertSensitiveFieldsEmpty();
				assertTrue(submit.isEnabled());
				assertFalse(progress.isVisible());
				pressEscape(window);
			});
		});
	}

	private void setInputs(String usernameValue, String passwordValue, String totpValue) {
		username.setText(usernameValue);
		password.setText(passwordValue);
		totp.setText(totpValue);
	}

	private void assertSensitiveFieldsEmpty() {
		assertEquals(0, window.getPassword().length);
		assertEquals("", window.getTotpCode());
	}
}
