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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JButton;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import javax.swing.text.Segment;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import gestion.app.LoginController;
import gestion.app.LoginController.Outcome;
import gestion.net.LoginStatus;
import gestion.net.ServerConnection;
import gestion.net.ServerConnection.LoginResult;

@Tag("desktop")
class LoginControllerTest {

	private static final String FICTITIOUS_PASSWORD = "FictitiousPassword123!";
	private static final String CONNECTION_ERROR = "Impossible de contacter le serveur. Vérifiez votre connexion.";
	private LoginWindow window;
	private LoginController controller;
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
			onEdt(() -> {

				if (controller != null) {
					controller.close();
				}

				window.dispose();
			});
		}

	}

	@Test
	void capturesCredentialsOnTheSwingThreadAndRunsTheNetworkInTheBackground() throws Exception {
		AtomicBoolean backgroundAccess = new AtomicBoolean();
		AtomicBoolean backgroundNetwork = new AtomicBoolean();
		AtomicReference<String> receivedUsername = new AtomicReference<>();
		AtomicReference<String> receivedTotp = new AtomicReference<>();
		AtomicReference<String> receivedPassword = new AtomicReference<>();
		AtomicReference<char[]> passwordBuffer = new AtomicReference<>();
		connect((userName, suppliedPassword, code) -> {
			backgroundNetwork.set(!SwingUtilities.isEventDispatchThread());
			receivedUsername.set(userName);
			receivedPassword.set(new String(suppliedPassword));
			receivedTotp.set(code);
			passwordBuffer.set(suppliedPassword);
			return new LoginResult(LoginStatus.AUTHENTICATED, userName);
		});
		onEdt(() -> {
			username.setDocument(new TrackingDocument(backgroundAccess));
			password.setDocument(new TrackingDocument(backgroundAccess));
			totp.setDocument(new TrackingDocument(backgroundAccess));
			status.setDocument(new TrackingDocument(backgroundAccess));
			setInputs();
			submit.doClick(0);
		});
		awaitStatus("Connexion réussie.");
		assertTrue(backgroundNetwork.get());
		assertFalse(backgroundAccess.get());
		assertEquals("fictitious_user", receivedUsername.get());
		assertEquals(FICTITIOUS_PASSWORD, receivedPassword.get());
		assertEquals("001234", receivedTotp.get());
		assertArrayEquals(new char[FICTITIOUS_PASSWORD.length()], passwordBuffer.get());
		onEdt(() -> {
			assertEquals(0, window.getPassword().length);
			assertEquals("", window.getTotpCode());
			assertFalse(submit.isEnabled());
			assertFalse(progress.isVisible());
		});
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"REJECTED|Identifiant, mot de passe ou code d’authentification incorrect.",
			"CSRF_REJECTED|La session de sécurité a expiré. Réessayez.",
			"UNAVAILABLE|Le serveur est indisponible. Réessayez plus tard.",
			"FAILED|Impossible de contacter le serveur. Vérifiez votre connexion." })
	void restoresTheFormForEveryRefusal(LoginStatus refusal, String expectedMessage) throws Exception {
		connect((userName, suppliedPassword, code) -> new LoginResult(refusal, null));
		onEdt(() -> {
			setInputs();
			submit.doClick(0);
		});
		awaitStatus(expectedMessage);
		onEdt(() -> {
			assertEquals("fictitious_user", window.getUsername());
			assertEquals(0, window.getPassword().length);
			assertEquals("", window.getTotpCode());
			assertTrue(submit.isEnabled());
			assertTrue(back.isEnabled());
			assertFalse(progress.isVisible());
		});
	}

	@Test
	void anUnexpectedFailureRestoresTheFormAndAllowsAnotherAttempt() throws Exception {
		AtomicInteger attempts = new AtomicInteger();
		connect((userName, suppliedPassword, code) -> {

			if (attempts.incrementAndGet() == 1) {
				throw new IllegalArgumentException("Fictitious private diagnostic");
			}

			return new LoginResult(LoginStatus.AUTHENTICATED, userName);
		});
		onEdt(() -> {
			setInputs();
			submit.doClick(0);
		});
		awaitStatus(CONNECTION_ERROR);
		onEdt(() -> {
			assertTrue(submit.isEnabled());
			assertFalse(progress.isVisible());
			setInputs();
			submit.doClick(0);
		});
		awaitStatus("Connexion réussie.");
		assertEquals(2, attempts.get());
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void aMissingResultOrStatusDoesNotLeaveTheFormSubmitting(boolean missingStatus) throws Exception {
		connect((userName, suppliedPassword, code) -> missingStatus ? new LoginResult(null, null) : null);
		onEdt(() -> {
			setInputs();
			submit.doClick(0);
		});
		awaitStatus(CONNECTION_ERROR);
		onEdt(() -> {
			assertTrue(submit.isEnabled());
			assertFalse(progress.isVisible());
		});
	}

	@Test
	void verifiedSuccessReturnsTheAuthenticatedOutcome() throws Exception {
		connect((userName, suppliedPassword, code) -> new LoginResult(LoginStatus.AUTHENTICATED, userName));
		FutureTask<Outcome> modal = openModal();
		onEdt(() -> {
			setInputs();
			submit.doClick(0);
		});
		awaitStatus("Connexion réussie.");
		onEdt(() -> window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING)));
		assertEquals(Outcome.AUTHENTICATED, modal.get(5, TimeUnit.SECONDS));
	}

	@Test
	void closingTheWindowReturnsClosedInsteadOfRequestingAnotherMode() throws Exception {
		connect((userName, suppliedPassword, code) -> new LoginResult(LoginStatus.REJECTED, null));
		onEdt(() -> assertEquals(Outcome.CLOSED, duringModal(window, controller::showLogin,
				() -> window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING)))));
	}

	@Test
	void escapeReturnsClosedInsteadOfRequestingAnotherMode() throws Exception {
		connect((userName, suppliedPassword, code) -> new LoginResult(LoginStatus.REJECTED, null));
		onEdt(() -> assertEquals(Outcome.CLOSED,
				duringModal(window, controller::showLogin, () -> pressEscape(window))));
	}

	@Test
	void theBackButtonReturnsTheModeSelectionOutcome() throws Exception {
		connect((userName, suppliedPassword, code) -> new LoginResult(LoginStatus.REJECTED, null));
		onEdt(() -> assertEquals(Outcome.MODE_SELECTION,
				duringModal(window, controller::showLogin, () -> back.doClick(0))));
	}

	@Test
	void closingDuringLoginInterruptsTheAttemptAndIgnoresLateSuccess() throws Exception {
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch interrupted = new CountDownLatch(1);
		CountDownLatch allowFinish = new CountDownLatch(1);
		CountDownLatch finished = new CountDownLatch(1);
		connect((userName, suppliedPassword, code) -> {
			started.countDown();

			try {
				boolean cancelled = false;

				while (true) {

					try {
						allowFinish.await();
						break;
					} catch (InterruptedException failure) {
						cancelled = true;
						interrupted.countDown();
					}

				}

				if (cancelled) {
					Thread.currentThread().interrupt();
				}

				return new LoginResult(LoginStatus.AUTHENTICATED, userName);
			} finally {
				finished.countDown();
			}

		});
		FutureTask<Outcome> modal = openModal();

		try {
			onEdt(() -> {
				setInputs();
				submit.doClick(0);
			});
			assertTrue(started.await(5, TimeUnit.SECONDS), "La tentative réseau n'a pas démarré.");
			onEdt(() -> pressEscape(window));
			assertEquals(Outcome.CLOSED, modal.get(5, TimeUnit.SECONDS));
			assertTrue(interrupted.await(5, TimeUnit.SECONDS), "La tentative réseau n'a pas été interrompue.");
			allowFinish.countDown();
			assertTrue(finished.await(5, TimeUnit.SECONDS), "La tentative réseau ne s'est pas terminée.");
			onEdt(() -> {
				assertFalse(window.isDisplayable());
				assertEquals("Connexion en cours…", status.getText());
			});
		} finally {
			allowFinish.countDown();
		}

	}

	private void connect(LoginOperation operation) throws Exception {
		ServerConnection connection = new ServerConnection() {
			@Override
			public LoginResult login(String userName, char[] suppliedPassword, String totpCode) {
				return operation.login(userName, suppliedPassword, totpCode);
			}
		};
		onEdt(() -> controller = new LoginController(window, connection));
	}

	private FutureTask<Outcome> openModal() throws Exception {
		FutureTask<Outcome> modal = new FutureTask<>(controller::showLogin);
		SwingUtilities.invokeLater(modal);
		onEdt(() -> assertTrue(window.isShowing()));
		return modal;
	}

	private void setInputs() {
		username.setText("fictitious_user");
		password.setText(FICTITIOUS_PASSWORD);
		totp.setText("001234");
	}

	private void awaitStatus(String expected) throws Exception {
		CountDownLatch observed = new CountDownLatch(1);
		DocumentListener listener = new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent event) {
				checkStatus();
			}

			@Override
			public void removeUpdate(DocumentEvent event) {
				checkStatus();
			}

			@Override
			public void changedUpdate(DocumentEvent event) {
				checkStatus();
			}

			private void checkStatus() {

				if (expected.equals(status.getText())) {
					observed.countDown();
				}

			}
		};
		onEdt(() -> {
			status.getDocument().addDocumentListener(listener);

			if (expected.equals(status.getText())) {
				observed.countDown();
			}

		});

		try {
			assertTrue(observed.await(5, TimeUnit.SECONDS), "État de connexion attendu non affiché : " + expected);
		} finally {
			onEdt(() -> status.getDocument().removeDocumentListener(listener));
		}

	}

	@FunctionalInterface
	private interface LoginOperation {
		LoginResult login(String userName, char[] password, String totpCode);
	}

	private static final class TrackingDocument extends PlainDocument {
		private final AtomicBoolean backgroundAccess;

		private TrackingDocument(AtomicBoolean backgroundAccess) {
			this.backgroundAccess = backgroundAccess;
		}

		@Override
		public String getText(int offset, int length) throws BadLocationException {
			recordAccess();
			return super.getText(offset, length);
		}

		@Override
		public void getText(int offset, int length, Segment target) throws BadLocationException {
			recordAccess();
			super.getText(offset, length, target);
		}

		@Override
		public void insertString(int offset, String text, AttributeSet attributes) throws BadLocationException {
			recordAccess();
			super.insertString(offset, text, attributes);
		}

		@Override
		public void remove(int offset, int length) throws BadLocationException {
			recordAccess();
			super.remove(offset, length);
		}

		private void recordAccess() {

			if (!SwingUtilities.isEventDispatchThread()) {
				backgroundAccess.set(true);
			}

		}
	}
}
