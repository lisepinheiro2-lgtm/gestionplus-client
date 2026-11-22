package gestion.ui;

import static gestion.ui.WindowTestSupport.component;
import static gestion.ui.WindowTestSupport.named;
import static gestion.ui.WindowTestSupport.onEdt;
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
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import gestion.app.MfaEnrollmentController;
import gestion.net.HttpApiException;
import gestion.net.MfaEnrollment;
import gestion.net.MfaEnrollmentObservation;
import gestion.net.MfaRecoveryCodes;
import gestion.net.ServerConnection;

@Tag("desktop")
class MfaEnrollmentControllerTest {

	private final FictitiousConnection connection = new FictitiousConnection();
	private MfaEnrollmentWindow window;
	private MfaEnrollmentController controller;
	private JButton primary;
	private JTextField code;
	private JTextArea status;

	@AfterEach
	void close() throws Exception {
		connection.release.countDown();
		onEdt(() -> {
			if (controller != null) {
				controller.close();
			}
			if (window != null) {
				window.dispose();
			}
		});
		assertTrue(connection.closed.await(5, TimeUnit.SECONDS));
	}

	@Test
	void opensOnceOffTheEdtAndKeepsThePersonalKeyMaskedUntilAnExplicitAction() throws Exception {
		prepare();
		await(() -> code.isEnabled());
		onEdt(() -> {
			JPasswordField key = named(window, JPasswordField.class, "Clé de configuration personnelle");
			assertEquals("A".repeat(32), new String(key.getPassword()));
			assertTrue(key.getEchoChar() != '\0');
			component(window, JCheckBox.class, check -> check.getText().startsWith("Afficher la clé")).doClick();
			assertEquals('\0', key.getEchoChar());
		});
		assertEquals(1, connection.openings);
		assertEquals(1, connection.beginnings);
		assertFalse(connection.networkOnEdt);
	}

	@Test
	void confirmsOnlyOnceAndRequiresAcknowledgementBeforeReturningWithTheRecoveryCodes() throws Exception {
		prepare();
		confirm();
		assertTrue(connection.confirmationStarted.await(5, TimeUnit.SECONDS));
		onEdt(() -> {
			assertFalse(primary.isEnabled());
			primary.doClick();
		});
		connection.release.countDown();
		await(() -> window.isCompleted());
		onEdt(() -> {
			assertEquals("", code.getText());
			assertEquals(0, named(window, JPasswordField.class, "Clé de configuration personnelle").getPassword().length);
			assertFalse(primary.isEnabled());
			assertEquals(10, named(window, JTextArea.class, "Codes de récupération personnels").getText().lines().count());
			component(window, JCheckBox.class, check -> check.getText().startsWith("J’ai conservé")).doClick();
			assertTrue(primary.isEnabled());
			primary.doClick();
			assertFalse(window.isDisplayable());
			assertEquals("", named(window, JTextArea.class, "Codes de récupération personnels").getText());
		});
		assertEquals(1, connection.confirmations);
		assertFalse(connection.networkOnEdt);
	}

	@Test
	void rejectsNonAsciiOrIncompleteCodesWithoutSendingAnyRequest() throws Exception {
		prepare();
		await(() -> code.isEnabled());
		onEdt(() -> {
			code.setText("１２３４５６");
			primary.doClick();
			assertTrue(status.getText().contains("exactement six chiffres"));
		});
		assertEquals(0, connection.confirmations);
	}

	@Test
	void anExplicitRetryOnlyReadsTheExistingEnrollmentWithoutReusingTheProof() throws Exception {
		connection.beginFailure = new IOException("fictitious private detail");
		prepare();
		await(() -> status.getText().startsWith("Impossible de charger"));
		connection.beginFailure = null;
		onEdt(primary::doClick);
		await(() -> code.isEnabled());
		assertEquals(1, connection.openings);
		assertEquals(2, connection.beginnings);
	}

	@Test
	void anUnknownOpeningOutcomeCanOnlyRecoverAnExistingCookieContext() throws Exception {
		connection.openFailure = new IOException("fictitious connection lost after opening");
		prepare();
		await(() -> status.getText().startsWith("Impossible de charger"));
		onEdt(primary::doClick);
		await(() -> code.isEnabled());
		assertEquals(1, connection.openings);
		assertEquals(1, connection.beginnings);
	}

	@Test
	void aRejectedTotpClearsTheCodeAndAllowsOnlyAManualNewSubmission() throws Exception {
		connection.confirmFailure = new HttpApiException(403, "FORBIDDEN");
		prepare();
		confirm();
		connection.release.countDown();
		await(() -> status.getText().startsWith("Vérification refusée"));
		onEdt(() -> {
			assertEquals("", code.getText());
			assertTrue(primary.isEnabled());
		});
		assertEquals(1, connection.confirmations);
	}

	@Test
	void aRateLimitDisablesSubmissionWithoutResettingTheEnrollment() throws Exception {
		connection.confirmFailure = new HttpApiException(429, "TOO_MANY_REQUESTS", Duration.ofSeconds(30));
		prepare();
		confirm();
		connection.release.countDown();
		await(() -> status.getText().startsWith("Trop de demandes"));
		onEdt(() -> assertFalse(primary.isEnabled()));
		assertEquals(1, connection.openings);
		assertEquals(1, connection.beginnings);
		assertEquals(1, connection.confirmations);
	}

	@Test
	void anExpiredContextClearsTheKeyAndOffersNoProofOrSmsReuse() throws Exception {
		connection.remaining = Duration.ZERO;
		prepare();
		await(() -> status.getText().startsWith("Le parcours a expiré"));
		onEdt(() -> {
			assertFalse(primary.isEnabled());
			assertEquals(0, named(window, JPasswordField.class, "Clé de configuration personnelle").getPassword().length);
			assertFalse(code.isEnabled());
		});
		assertEquals(0, connection.confirmations);
	}

	@Test
	void aLostConfirmationResponseNeverClaimsSuccessOrAutomaticallyResendsTheTotp() throws Exception {
		connection.confirmFailure = new IOException("fictitious lost confirmation response");
		prepare();
		confirm();
		connection.release.countDown();
		await(() -> status.getText().startsWith("Le résultat de la confirmation est incertain"));
		onEdt(() -> {
			assertFalse(window.isCompleted());
			assertFalse(primary.isEnabled());
			assertEquals(0, named(window, JPasswordField.class, "Clé de configuration personnelle").getPassword().length);
		});
		assertEquals(1, connection.confirmations);
	}

	@Test
	void aServerAcceptedConfirmationStillShowsItsCodesWhenTheLocalDeadlinePassesInFlight() throws Exception {
		connection.remaining = Duration.ofMillis(700);
		prepare();
		confirm();
		assertTrue(connection.confirmationStarted.await(5, TimeUnit.SECONDS));
		await(() -> status.getText().startsWith("L’échéance est atteinte"));
		connection.release.countDown();
		await(() -> window.isCompleted());
		onEdt(() -> {
			component(window, JCheckBox.class, check -> check.getText().startsWith("J’ai conservé")).doClick();
			assertTrue(primary.isEnabled());
		});
	}

	@Test
	void closingDropsLateResultsAndAttemptsLogoutAwayFromTheEdt() throws Exception {
		prepare();
		confirm();
		assertTrue(connection.confirmationStarted.await(5, TimeUnit.SECONDS));
		onEdt(() -> {
			controller.close();
			window.dispose();
		});
		connection.release.countDown();
		assertTrue(connection.closed.await(5, TimeUnit.SECONDS));
		onEdt(() -> {
			assertFalse(window.isDisplayable());
			assertEquals("", named(window, JTextArea.class, "Codes de récupération personnels").getText());
		});
		assertEquals(1, connection.logouts);
		assertFalse(connection.networkOnEdt);
	}

	private void prepare() throws Exception {
		onEdt(() -> {
			window = new MfaEnrollmentWindow(null);
			controller = new MfaEnrollmentController(window, connection, "p".repeat(22));
			primary = component(window, JButton.class, button -> "Réessayer".equals(button.getText()));
			code = named(window, JTextField.class, "Code de l’authentificateur à 6 chiffres");
			status = named(window, JTextArea.class, "État de l’enrôlement MFA");
		});
	}

	private void confirm() throws Exception {
		await(() -> code.isEnabled());
		onEdt(() -> {
			code.setText("000042");
			primary.doClick();
		});
	}

	private void await(BooleanSupplier condition) throws Exception {
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		AtomicBoolean found = new AtomicBoolean();
		while (!found.get() && System.nanoTime() < deadline) {
			onEdt(() -> found.set(condition.getAsBoolean()));
			Thread.sleep(10);
		}
		assertTrue(found.get(), "L’état MFA attendu n’a pas été atteint.");
	}

	private static final class FictitiousConnection extends ServerConnection {
		private volatile int openings;
		private volatile int beginnings;
		private volatile int confirmations;
		private volatile int logouts;
		private volatile boolean networkOnEdt;
		private volatile Duration remaining = Duration.ofMinutes(5);
		private volatile IOException openFailure;
		private volatile IOException beginFailure;
		private volatile IOException confirmFailure;
		private final CountDownLatch confirmationStarted = new CountDownLatch(1);
		private final CountDownLatch release = new CountDownLatch(1);
		private final CountDownLatch closed = new CountDownLatch(1);

		private FictitiousConnection() {
			super(URI.create("http://127.0.0.1:1"));
		}

		@Override
		public void openMfaEnrollment(String proof) throws IOException {
			networkOnEdt |= SwingUtilities.isEventDispatchThread();
			openings++;
			if (openFailure != null) {
				throw openFailure;
			}
		}

		@Override
		public MfaEnrollmentObservation beginMfaEnrollment() throws IOException {
			networkOnEdt |= SwingUtilities.isEventDispatchThread();
			beginnings++;
			if (beginFailure != null) {
				throw beginFailure;
			}
			return new MfaEnrollmentObservation(new MfaEnrollment(UUID.randomUUID(), "A".repeat(32), Instant.now().plus(remaining)), remaining);
		}

		@Override
		public MfaRecoveryCodes completeMfaEnrollment(UUID enrollmentID, String code) throws IOException, InterruptedException {
			networkOnEdt |= SwingUtilities.isEventDispatchThread();
			confirmations++;
			confirmationStarted.countDown();
			if (!release.await(5, TimeUnit.SECONDS)) {
				throw new IOException("La confirmation fictive n’a pas été libérée.");
			}
			if (confirmFailure != null) {
				throw confirmFailure;
			}
			return new MfaRecoveryCodes(IntStream.range(0, 10).mapToObj(index -> "fictitious" + index + "x".repeat(11)).toList());
		}

		@Override
		public void logout() {
			networkOnEdt |= SwingUtilities.isEventDispatchThread();
			logouts++;
		}

		@Override
		public void close() {
			super.close();
			closed.countDown();
		}
	}
}
