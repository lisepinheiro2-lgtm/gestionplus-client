package gestion.ui;

import static gestion.ui.WindowTestSupport.component;
import static gestion.ui.WindowTestSupport.named;
import static gestion.ui.WindowTestSupport.onEdt;
import static gestion.ui.WindowTestSupport.awaitWindow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JButton;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import gestion.app.ActivationController;
import gestion.net.ActivationInvitation;
import gestion.net.ActivationResult;
import gestion.net.HttpApiException;
import gestion.net.MfaEnrollment;
import gestion.net.MfaEnrollmentObservation;
import gestion.net.ServerConnection;

@Tag("desktop")
class ActivationControllerTest {

	private ActivationWindow window;
	private ActivationController controller;
	private final FictitiousConnection connection = new FictitiousConnection();
	private JButton send;
	private JButton activate;
	private JTextArea status;

	@BeforeEach
	void prepare() throws Exception {
		onEdt(() -> {
			window = new ActivationWindow(null);
			controller = new ActivationController(window, connection);
			named(window, JPasswordField.class, "Lien d’invitation reçu par e-mail").setText(
					"https://example.invalid/activate#invitation=00000000-0000-0000-0000-000000000001&token="
							+ "a".repeat(43));
			send = component(window, JButton.class, button -> "Envoyer le code SMS".equals(button.getText()));
			activate = component(window, JButton.class, button -> "Activer mon compte".equals(button.getText()));
			status = named(window, JTextArea.class, "État de l’activation");
		});
	}

	@AfterEach
	void close() throws Exception {
		connection.release.countDown();
		onEdt(() -> {
			controller.close();
			window.dispose();
		});
	}

	@Test
	void waitsOffTheEdtAndOnlySendsOnceDespiteRepeatedClicks() throws Exception {
		onEdt(send::doClick);
		assertTrue(connection.started.await(5, TimeUnit.SECONDS));
		onEdt(() -> {
			assertFalse(send.isEnabled());
			send.doClick();
		});
		assertFalse(connection.calledOnEdt);
		connection.release.countDown();
		awaitMessage("Code SMS demandé");
		assertEquals(1, connection.requests);
	}

	@Test
	void successfulActivationWipesTheWorkerPasswordAndOffersOnlyTheReturnToLogin() throws Exception {
		onEdt(() -> {
			named(window, JTextField.class, "Identifiant").setText("fictitious_user");
			named(window, JPasswordField.class, "Mot de passe").setText("Fictitious-Password-2026!");
			named(window, JPasswordField.class, "Confirmer le mot de passe").setText("Fictitious-Password-2026!");
			named(window, JTextField.class, "Code SMS à 6 chiffres").setText("000042");
			activate.doClick();
		});
		assertTrue(connection.started.await(5, TimeUnit.SECONDS));
		connection.release.countDown();
		awaitMessage("Votre compte est activé");
		assertFalse(connection.calledOnEdt);
		for (char value : connection.suppliedPassword) {
			assertEquals('\0', value);
		}
		onEdt(() -> assertFalse(activate.isEnabled()));
	}

	@Test
	void aRateLimitKeepsTheServerMessagePrivateAndDisablesAutomaticRetry() throws Exception {
		connection.failure = new HttpApiException(429, "TOO_MANY_REQUESTS", Duration.ofSeconds(5));
		connection.release.countDown();
		onEdt(send::doClick);
		awaitMessage("Trop de demandes");
		onEdt(() -> assertFalse(send.isEnabled()));
		assertEquals(1, connection.requests);
	}

	@Test
	void closingCancelsTheRequestAndDoesNotReopenTheDisposedWindow() throws Exception {
		onEdt(send::doClick);
		assertTrue(connection.started.await(5, TimeUnit.SECONDS));
		onEdt(() -> {
			controller.close();
			window.dispose();
		});
		connection.release.countDown();
		assertTrue(connection.closed);
		onEdt(() -> assertFalse(window.isDisplayable()));
	}

	@Test
	void transfersTheActivationCookieContextToMfaWithoutInterruptingItsLogoutOnReturn() throws Exception {
		connection.result = new ActivationResult("p".repeat(22));
		connection.release.countDown();
		onEdt(() -> {
			named(window, JTextField.class, "Identifiant").setText("fictitious_user");
			named(window, JPasswordField.class, "Mot de passe").setText("Fictitious-Password-2026!");
			named(window, JPasswordField.class, "Confirmer le mot de passe").setText("Fictitious-Password-2026!");
			named(window, JTextField.class, "Code SMS à 6 chiffres").setText("000042");
			activate.doClick();
		});
		MfaEnrollmentWindow enrollment = awaitWindow(MfaEnrollmentWindow.class, candidate -> true);
		try {
			onEdt(enrollment::dispose);
			assertTrue(connection.logoutStarted.await(5, TimeUnit.SECONDS));
			assertFalse(connection.closed);
			assertFalse(connection.logoutOnEdt);
			onEdt(() -> assertFalse(window.isDisplayable()));
		} finally {
			connection.logoutRelease.countDown();
		}
		assertTrue(connection.transportClosed.await(5, TimeUnit.SECONDS));
	}

	private void awaitMessage(String expected) throws Exception {
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		AtomicBoolean found = new AtomicBoolean();
		while (!found.get() && System.nanoTime() < deadline) {
			onEdt(() -> found.set(status.getText().contains(expected)));
			Thread.sleep(10);
		}
		assertTrue(found.get(), "Le résultat attendu n’a pas été affiché.");
	}

	private static final class FictitiousConnection extends ServerConnection {
		private final CountDownLatch started = new CountDownLatch(1);
		private final CountDownLatch release = new CountDownLatch(1);
		private volatile int requests;
		private volatile boolean calledOnEdt;
		private volatile boolean closed;
		private volatile IOException failure;
		private volatile char[] suppliedPassword;
		private volatile ActivationResult result = new ActivationResult(null);
		private final CountDownLatch logoutStarted = new CountDownLatch(1);
		private final CountDownLatch logoutRelease = new CountDownLatch(1);
		private final CountDownLatch transportClosed = new CountDownLatch(1);
		private volatile boolean logoutOnEdt;

		private FictitiousConnection() {
			super(URI.create("http://127.0.0.1:1"));
		}

		@Override
		public void requestActivationChallenge(ActivationInvitation invitation) throws IOException, InterruptedException {
			exchange();
		}

		@Override
		public ActivationResult completeActivation(ActivationInvitation invitation, String userName, char[] password, String code)
				throws IOException, InterruptedException {
			suppliedPassword = password;
			exchange();
			return result;
		}

		@Override
		public void openMfaEnrollment(String proof) {
		}

		@Override
		public MfaEnrollmentObservation beginMfaEnrollment() {
			return new MfaEnrollmentObservation(new MfaEnrollment(UUID.randomUUID(), "A".repeat(32),
					Instant.now().plusSeconds(300)), Duration.ofMinutes(5));
		}

		@Override
		public void logout() throws InterruptedException {
			logoutOnEdt = SwingUtilities.isEventDispatchThread();
			logoutStarted.countDown();
			logoutRelease.await(5, TimeUnit.SECONDS);
		}

		private void exchange() throws IOException, InterruptedException {
			requests++;
			calledOnEdt = SwingUtilities.isEventDispatchThread();
			started.countDown();
			if (!release.await(5, TimeUnit.SECONDS)) {
				throw new IOException("La réponse fictive n’a pas été libérée.");
			}
			if (failure != null) {
				throw failure;
			}
		}

		@Override
		public void close() {
			closed = true;
			super.close();
			transportClosed.countDown();
		}
	}
}
