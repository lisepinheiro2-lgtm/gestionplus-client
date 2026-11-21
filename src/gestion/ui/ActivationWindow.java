package gestion.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.Timer;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import gestion.net.ActivationInvitation;
import gestion.ui.raven.RavenStyledButton;

public final class ActivationWindow extends JDialog implements AppThemeAware {

	private final JPanel content = new JPanel(new BorderLayout(0, 16));
	private final JPanel form = new JPanel(new GridBagLayout());
	private final JLabel logo = new JLabel();
	private final JLabel title = new JLabel("Activez votre compte Gestion+");
	private final JPasswordField invitation = new JPasswordField(30);
	private final JTextField username = new JTextField(30);
	private final JPasswordField password = new JPasswordField(30);
	private final JPasswordField confirmation = new JPasswordField(30);
	private final JTextField code = new JTextField(6);
	private final JTextArea status = new JTextArea(3, 38);
	private final JProgressBar progress = new JProgressBar();
	private final RavenStyledButton send = new RavenStyledButton();
	private final RavenStyledButton activate = new RavenStyledButton();
	private final RavenStyledButton back = new RavenStyledButton();
	private final Timer retryTimer = new Timer(1000, event -> updateControls());
	private Runnable onSend;
	private Runnable onActivate;
	private boolean busy;
	private boolean completed;
	private long retryUntil;
	private boolean warning;

	public ActivationWindow(JDialog owner) {
		super(owner, "Activation — Gestion+", true);
		content.setBorder(BorderFactory.createEmptyBorder(22, 26, 20, 26));
		title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
		JPanel heading = new JPanel(new BorderLayout(14, 0));
		heading.setOpaque(false);
		heading.add(logo, BorderLayout.WEST);
		heading.add(title, BorderLayout.CENTER);
		content.add(heading, BorderLayout.NORTH);
		addField("Lien d’invitation reçu par e-mail", invitation, 0, KeyEvent.VK_L);
		addField("Identifiant", username, 2, KeyEvent.VK_I);
		addField("Mot de passe", password, 4, KeyEvent.VK_M);
		addField("Confirmer le mot de passe", confirmation, 6, KeyEvent.VK_F);
		addField("Code SMS à 6 chiffres", code, 8, KeyEvent.VK_C);
		invitation.getAccessibleContext().setAccessibleDescription("Collez le lien complet reçu par e-mail. Il reste masqué.");
		send.setText("Envoyer le code SMS");
		send.setMnemonic(KeyEvent.VK_E);
		send.addActionListener(event -> submit(false));
		GridBagConstraints position = new GridBagConstraints();
		position.gridx = 0;
		position.gridy = 10;
		position.fill = GridBagConstraints.HORIZONTAL;
		position.weightx = 1;
		position.insets = new Insets(2, 0, 0, 0);
		form.add(send, position);
		content.add(form, BorderLayout.CENTER);
		status.setEditable(false);
		status.setFocusable(false);
		status.setOpaque(false);
		status.setLineWrap(true);
		status.setWrapStyleWord(true);
		status.getAccessibleContext().setAccessibleName("État de l’activation");
		progress.setIndeterminate(true);
		progress.setVisible(false);
		progress.getAccessibleContext().setAccessibleName("Demande d’activation en cours");
		activate.setText("Activer mon compte");
		activate.setMnemonic(KeyEvent.VK_A);
		activate.addActionListener(event -> submit(true));
		back.setText("Retour à la connexion");
		back.setMnemonic(KeyEvent.VK_R);
		back.addActionListener(event -> dispose());
		JPanel buttons = new JPanel(new java.awt.GridLayout(1, 2, 12, 0));
		buttons.setOpaque(false);
		buttons.add(back);
		buttons.add(activate);
		JPanel feedback = new JPanel(new BorderLayout(0, 8));
		feedback.setOpaque(false);
		feedback.add(status, BorderLayout.CENTER);
		feedback.add(progress, BorderLayout.SOUTH);
		JPanel footer = new JPanel(new BorderLayout(0, 12));
		footer.setOpaque(false);
		footer.add(feedback, BorderLayout.CENTER);
		footer.add(buttons, BorderLayout.SOUTH);
		content.add(footer, BorderLayout.SOUTH);
		getRootPane().setDefaultButton(activate);
		getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeActivation");
		getRootPane().getActionMap().put("closeActivation", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent event) {
				dispose();
			}
		});
		setContentPane(content);
		setIconImage(new ImageIcon(getClass().getResource("/icons/gestion-taskbar.png")).getImage());
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		showMessage("Collez votre invitation, puis demandez le code SMS. Le code est valable au maximum cinq minutes.", false);
		applyAppTheme(UIUtils.isDarkMode());
		pack();
		setResizable(false);
		setLocationRelativeTo(owner);
	}

	public void setActions(Runnable onSend, Runnable onActivate) {
		this.onSend = java.util.Objects.requireNonNull(onSend);
		this.onActivate = java.util.Objects.requireNonNull(onActivate);
		updateControls();
	}

	public ActivationInvitation invitation() {
		char[] supplied = invitation.getPassword();
		try {
			return ActivationInvitation.fromLink(new String(supplied));
		} finally {
			Arrays.fill(supplied, '\0');
		}
	}

	public String username() {
		return username.getText();
	}

	public char[] password() {
		return password.getPassword();
	}

	public String code() {
		return code.getText();
	}

	public void setBusy(boolean busy) {
		this.busy = busy;
		if (busy) {
			showMessage("Demande en cours…", false);
		}
		updateControls();
	}

	public void showMessage(String message, boolean warning) {
		this.warning = warning;
		status.setText(message);
		status.setCaretPosition(0);
		status.setForeground(warning ? UIUtils.getChampagneColor() : UIUtils.getTurquoiseColor());
	}

	public void showSuccess() {
		completed = true;
		clearSecrets();
		invitation.setText("");
		showMessage("Votre compte est activé. Vous pouvez revenir à la connexion.", false);
		updateControls();
		back.requestFocusInWindow();
	}

	public void retryAfter(Duration delay) {
		long seconds = Math.max(1, Math.min(86400, delay.toSeconds()));
		retryUntil = System.nanoTime() + Duration.ofSeconds(seconds).toNanos();
		retryTimer.start();
		updateControls();
	}

	public void clearSecrets() {
		password.setText("");
		confirmation.setText("");
		code.setText("");
	}

	@Override
	public void dispose() {
		retryTimer.stop();
		clearSecrets();
		invitation.setText("");
		super.dispose();
	}

	@Override
	public void applyAppTheme(boolean dark) {
		content.setBackground(UIUtils.getBackgroundColor());
		form.setBackground(UIUtils.getSurfaceColor());
		form.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UIUtils.getBorderColor(), 1, true),
				BorderFactory.createEmptyBorder(16, 18, 16, 18)));
		title.setForeground(UIUtils.getTextColor());
		for (var component : form.getComponents()) {
			if (component instanceof JLabel label) {
				label.setForeground(UIUtils.getTextColor());
			}
		}
		for (JTextField field : List.of(invitation, username, password, confirmation, code)) {
			UIUtils.applyTheme(field);
		}
		logo.setIcon(new FlatSVGIcon(getClass().getResource(dark ? "/icons/gestion-header-dark.svg"
				: "/icons/gestion-header-light.svg")).derive(48, 48));
		UIUtils.styleActionButton(send, "neutral");
		UIUtils.styleActionButton(back, "neutral");
		UIUtils.styleActionButton(activate, "primary");
		status.setForeground(warning ? UIUtils.getChampagneColor() : UIUtils.getTurquoiseColor());
	}

	private void submit(boolean activation) {
		if (busy || completed || retryUntil != 0 && retryUntil - System.nanoTime() > 0 || onSend == null || onActivate == null) {
			return;
		}
		try {
			invitation();
			if (activation) {
				char[] supplied = password.getPassword();
				char[] repeated = confirmation.getPassword();
				try {
					if (username().isBlank() || username().length() > 255 || supplied.length == 0 || supplied.length > 128
							|| !Arrays.equals(supplied, repeated) || !code().matches("[0-9]{6}")) {
						showMessage("Vérifiez l’identifiant, les deux mots de passe et le code à six chiffres.", true);
						return;
					}
				} finally {
					Arrays.fill(supplied, '\0');
					Arrays.fill(repeated, '\0');
				}
			}
			(activation ? onActivate : onSend).run();
		} catch (IllegalArgumentException failure) {
			showMessage("Le lien d’invitation n’est pas valide. Copiez le lien complet reçu par e-mail.", true);
		}
	}

	private void updateControls() {
		boolean waiting = retryUntil != 0 && retryUntil - System.nanoTime() > 0;
		if (!waiting) {
			retryTimer.stop();
		}
		boolean editable = !busy && !completed;
		for (JTextField field : List.of(invitation, username, password, confirmation, code)) {
			field.setEnabled(editable);
		}
		send.setEnabled(editable && !waiting && onSend != null);
		send.setText(waiting ? "Réessayer dans " + Math.max(1, (retryUntil - System.nanoTime()) / 1_000_000_000L) + " s"
				: "Envoyer le code SMS");
		activate.setEnabled(editable && !waiting && onActivate != null);
		progress.setVisible(busy);
	}

	private void addField(String text, JTextField field, int row, int mnemonic) {
		JLabel label = new JLabel(text);
		label.setLabelFor(field);
		label.setDisplayedMnemonic(mnemonic);
		field.getAccessibleContext().setAccessibleName(text);
		field.setPreferredSize(new Dimension(420, 36));
		GridBagConstraints position = new GridBagConstraints();
		position.gridx = 0;
		position.gridy = row;
		position.weightx = 1;
		position.fill = GridBagConstraints.HORIZONTAL;
		position.insets = new Insets(0, 0, 5, 0);
		form.add(label, position);
		position.gridy = row + 1;
		position.insets = new Insets(0, 0, 12, 0);
		form.add(field, position);
	}
}
