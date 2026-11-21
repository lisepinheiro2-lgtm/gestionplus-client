package gestion.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.Locale;
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextArea;

import gestion.app.SessionStatus;

public final class SessionBanner extends JPanel implements AppThemeAware {

	private final JTextArea message = new JTextArea(2, 36);
	private final JButton retry = new JButton("Réessayer");
	private SessionStatus status = new SessionStatus(SessionStatus.State.CHECKING, 0);

	public SessionBanner(Runnable onRetry) {
		super(new BorderLayout(16, 0));
		Objects.requireNonNull(onRetry);
		message.setEditable(false);
		message.setFocusable(false);
		message.setLineWrap(true);
		message.setWrapStyleWord(true);
		message.setOpaque(false);
		message.getAccessibleContext().setAccessibleName("État de la session");
		retry.setMnemonic(KeyEvent.VK_R);
		retry.getAccessibleContext().setAccessibleName("Réessayer la connexion au serveur");
		retry.addActionListener(event -> onRetry.run());
		add(message, BorderLayout.CENTER);
		add(retry, BorderLayout.EAST);
		showStatus(status);
	}

	public void showStatus(SessionStatus status) {
		this.status = Objects.requireNonNull(status);
		message.setText(switch (status.state()) {
		case ACTIVE -> "";
		case EXPIRING -> "Votre session arrive à échéance dans " + remainingTime(status.remainingSeconds()) + ".";
		case CHECKING -> "Vérification de la session auprès du serveur…";
		case UNAVAILABLE -> "Serveur momentanément inaccessible. Les opérations sont suspendues."
				+ " Réessayez ou utilisez le bouton Compte pour vous déconnecter.";
		case CLOSING -> "Déconnexion en cours…";
		});
		message.setCaretPosition(0);
		retry.setVisible(status.state() == SessionStatus.State.UNAVAILABLE);
		setVisible(status.state() != SessionStatus.State.ACTIVE);
		applyAppTheme(UIUtils.isDarkMode());
		revalidate();
		repaint();
	}

	@Override
	public void applyAppTheme(boolean dark) {
		setBackground(dark ? new Color(33, 42, 56) : Color.WHITE);
		Color accent = switch (status.state()) {
		case EXPIRING -> dark ? new Color(223, 195, 145) : new Color(136, 100, 42);
		case UNAVAILABLE -> dark ? new Color(197, 160, 237) : new Color(120, 69, 174);
		default -> dark ? new Color(159, 174, 194) : new Color(102, 118, 139);
		};
		message.setForeground(accent);
		setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 4, 1, 0, accent),
				BorderFactory.createEmptyBorder(12, 20, 12, 20)));
		UIUtils.styleActionButton(retry, "neutral");
	}

	private static String remainingTime(long seconds) {
		return String.format(Locale.ROOT, "%d min %02d s", seconds / 60, seconds % 60);
	}
}
