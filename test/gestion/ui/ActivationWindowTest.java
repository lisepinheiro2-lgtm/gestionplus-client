package gestion.ui;

import static gestion.ui.WindowTestSupport.component;
import static gestion.ui.WindowTestSupport.named;
import static gestion.ui.WindowTestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JButton;
import javax.swing.JPasswordField;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("desktop")
class ActivationWindowTest {

	@Test
	void openingTheDialogNeverSendsAnSmsAndTheExplicitButtonDoes() throws Exception {
		onEdt(() -> {
			ActivationWindow window = new ActivationWindow(null);
			try {
				AtomicInteger sends = new AtomicInteger();
				window.setActions(sends::incrementAndGet, () -> { });
				assertEquals(0, sends.get());
				named(window, JPasswordField.class, "Lien d’invitation reçu par e-mail").setText(
						"https://example.invalid/activate#invitation=00000000-0000-0000-0000-000000000001&token="
								+ "a".repeat(43));
				component(window, JButton.class, button -> "Envoyer le code SMS".equals(button.getText())).doClick();
				assertEquals(1, sends.get());
				assertTrue(window.isModal());
			} finally {
				window.dispose();
			}
		});
	}

	@Test
	void waitingBusyAndSuccessStatesDisableFurtherSubmissionAndClosingClearsSecrets() throws Exception {
		onEdt(() -> {
			ActivationWindow window = new ActivationWindow(null);
			try {
				window.setActions(() -> { }, () -> { });
				JButton activate = component(window, JButton.class, button -> "Activer mon compte".equals(button.getText()));
				JPasswordField password = named(window, JPasswordField.class, "Mot de passe");
				password.setText("Fictitious-Password-2026!");
				window.setBusy(true);
				assertFalse(activate.isEnabled());
				window.setBusy(false);
				window.retryAfter(Duration.ofSeconds(5));
				assertFalse(activate.isEnabled());
				window.showSuccess();
				assertFalse(activate.isEnabled());
				assertEquals(0, password.getPassword().length);
			} finally {
				window.dispose();
			}
		});
	}
}
