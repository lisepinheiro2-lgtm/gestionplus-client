package gestion.ui;

import static gestion.ui.WindowTestSupport.named;
import static gestion.ui.WindowTestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.swing.JButton;
import javax.swing.JLabel;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApplicationHeaderIdentityTest {

	@ParameterizedTest
	@ValueSource(strings = { "fictitious_user", "<html><b>Fictitious administrator</b></html>",
			"<HTML><i>Fictitious user</i></HTML>", "<html>Fictitious<br>user", "" })
	void displaysTheExactIdentityWithoutCreatingAnHtmlRenderer(String identity) throws Exception {
		onEdt(() -> {
			var header = new ApplicationHeader(null, new JButton("Accueil"));
			header.setIdentity(identity);
			JLabel label = named(header, JLabel.class, "Utilisateur connecté : " + identity);
			assertEquals(identity, label.getText());
			assertEquals("Connecté en tant que " + identity, label.getToolTipText());
			assertNull(label.getClientProperty("html"));
		});
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void preservesPlainTextRenderingAcrossThemeChangesAndIdentityReplacement(boolean dark) throws Exception {
		onEdt(() -> {
			var header = new ApplicationHeader(null, new JButton("Accueil"));
			String identity = "<html><b>Fictitious first account</b></html>";
			header.setIdentity(identity);
			header.applyAppTheme(dark);
			JLabel label = named(header, JLabel.class, "Utilisateur connecté : " + identity);
			assertEquals(identity, label.getText());
			assertNull(label.getClientProperty("html"));
			header.setIdentity("fictitious_second_account");
			assertEquals("fictitious_second_account", label.getText());
			assertNull(label.getClientProperty("html"));
			header.setIdentity("");
			assertEquals("", label.getText());
			assertNull(label.getClientProperty("html"));
		});
	}
}
