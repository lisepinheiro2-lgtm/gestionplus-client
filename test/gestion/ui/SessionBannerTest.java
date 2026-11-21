package gestion.ui;

import static gestion.ui.WindowTestSupport.named;
import static gestion.ui.WindowTestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JButton;
import javax.swing.JTextArea;

import org.junit.jupiter.api.Test;

import gestion.app.SessionStatus;

class SessionBannerTest {

	@Test
	void warnsWithTheRemainingTimeWithoutPresentingADialogOrAHiddenRenewalAction() throws Exception {
		onEdt(() -> {
			SessionBanner banner = new SessionBanner(() -> {
			});
			banner.showStatus(new SessionStatus(SessionStatus.State.EXPIRING, 119));
			assertTrue(banner.isVisible());
			assertEquals("Votre session arrive à échéance dans 1 min 59 s.",
					named(banner, JTextArea.class, "État de la session").getText());
			assertFalse(named(banner, JButton.class, "Réessayer la connexion au serveur").isVisible());
		});
	}

	@Test
	void exposesAKeyboardAccessibleRetryOnlyWhileTheServerIsUnavailable() throws Exception {
		AtomicInteger attempts = new AtomicInteger();
		onEdt(() -> {
			SessionBanner banner = new SessionBanner(attempts::incrementAndGet);
			JButton retry = named(banner, JButton.class, "Réessayer la connexion au serveur");
			assertFalse(retry.isVisible());
			banner.showStatus(new SessionStatus(SessionStatus.State.UNAVAILABLE, 120));
			assertTrue(retry.isVisible());
			assertTrue(retry.isFocusable());
			assertTrue(retry.getMnemonic() != 0);
			retry.doClick(0);
			assertEquals(1, attempts.get());
			banner.showStatus(new SessionStatus(SessionStatus.State.ACTIVE, 600));
			assertFalse(banner.isVisible());
			assertFalse(retry.isVisible());
		});
	}

	@Test
	void preservesTheWarningAndReadableColorsWhenTheThemeChanges() throws Exception {
		onEdt(() -> {
			SessionBanner banner = new SessionBanner(() -> {
			});
			banner.showStatus(new SessionStatus(SessionStatus.State.EXPIRING, 60));
			JTextArea message = named(banner, JTextArea.class, "État de la session");
			banner.applyAppTheme(false);
			Color light = message.getForeground();
			assertNotEquals(banner.getBackground(), light);
			banner.applyAppTheme(true);
			assertNotEquals(light, message.getForeground());
			assertNotEquals(banner.getBackground(), message.getForeground());
			assertTrue(message.getText().contains("1 min 00 s"));
		});
	}
}

