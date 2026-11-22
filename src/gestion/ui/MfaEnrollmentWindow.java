package gestion.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Duration;
import java.util.Objects;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.Timer;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import gestion.net.MfaEnrollmentObservation;
import gestion.net.MfaRecoveryCodes;
import gestion.ui.raven.RavenStyledButton;

public final class MfaEnrollmentWindow extends JDialog implements AppThemeAware {

	private final JPanel content = new JPanel(new BorderLayout(0, 16));
	private final JPanel pages = new JPanel(new CardLayout());
	private final JPanel configuration = new JPanel();
	private final JPanel recovery = new JPanel(new BorderLayout(0, 12));
	private final JLabel logo = new JLabel();
	private final JLabel title = new JLabel("Sécurisez votre compte");
	private final JLabel expiration = new JLabel("Préparation du parcours sécurisé…");
	private final JPasswordField setupKey = new JPasswordField();
	private final char hiddenKey = setupKey.getEchoChar();
	private final JCheckBox reveal = new JCheckBox("Afficher la clé de configuration");
	private final JTextField code = new JTextField();
	private final JTextArea instructions = text("Dans votre application d’authentification, ajoutez un compte par saisie manuelle. Utilisez cette clé avec des codes TOTP à 6 chiffres, renouvelés toutes les 30 secondes.", 4);
	private final JTextArea recoveryInstructions = text("L’authentificateur est activé. Ces dix codes de récupération ne seront affichés qu’une fois. Chaque code est utilisable une seule fois. Conservez-les en lieu sûr, séparément de votre mot de passe. Ne les partagez pas.", 4);
	private final JTextArea recoveryCodes = text("", 10);
	private final JCheckBox saved = new JCheckBox("J’ai conservé mes codes en lieu sûr");
	private final JTextArea status = text("", 4);
	private final JProgressBar progress = new JProgressBar();
	private final RavenStyledButton primary = new RavenStyledButton();
	private final RavenStyledButton back = new RavenStyledButton();
	private final Timer timer = new Timer(250, event -> updateDeadline());
	private Runnable onConfirm;
	private Runnable onRetry;
	private Runnable onCancel;
	private Runnable onExpire;
	private boolean busy;
	private boolean confirming;
	private boolean configured;
	private boolean completed;
	private boolean terminal;
	private boolean warning;
	private boolean expired;
	private long deadline;
	private long retryUntil;

	public MfaEnrollmentWindow(JDialog owner) {
		super(owner, "Authentificateur — Gestion+", true);
		content.setBorder(BorderFactory.createEmptyBorder(22, 26, 20, 26));
		title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
		JPanel heading = new JPanel(new BorderLayout(14, 0));
		heading.setOpaque(false);
		heading.add(logo, BorderLayout.WEST);
		heading.add(title, BorderLayout.CENTER);
		content.add(heading, BorderLayout.NORTH);
		configuration.setLayout(new BoxLayout(configuration, BoxLayout.Y_AXIS));
		instructions.setAlignmentX(LEFT_ALIGNMENT);
		instructions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
		configuration.add(instructions);
		JLabel keyLabel = new JLabel("Clé de configuration personnelle");
		keyLabel.setLabelFor(setupKey);
		setupKey.setEditable(false);
		setupKey.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		setupKey.setPreferredSize(new Dimension(420, 36));
		setupKey.setAlignmentX(LEFT_ALIGNMENT);
		setupKey.getAccessibleContext().setAccessibleName("Clé de configuration personnelle");
		configuration.add(keyLabel);
		configuration.add(setupKey);
		reveal.setMnemonic(KeyEvent.VK_A);
		reveal.addActionListener(event -> setupKey.setEchoChar(reveal.isSelected() ? '\0' : hiddenKey));
		configuration.add(reveal);
		configuration.add(javax.swing.Box.createVerticalStrut(14));
		JLabel codeLabel = new JLabel("Code de l’authentificateur à 6 chiffres");
		codeLabel.setLabelFor(code);
		codeLabel.setDisplayedMnemonic(KeyEvent.VK_C);
		code.getAccessibleContext().setAccessibleName(codeLabel.getText());
		code.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		code.setPreferredSize(new Dimension(420, 36));
		code.setAlignmentX(LEFT_ALIGNMENT);
		configuration.add(codeLabel);
		configuration.add(code);
		recovery.add(recoveryInstructions, BorderLayout.NORTH);
		recoveryCodes.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
		recoveryCodes.setFocusable(true);
		recoveryCodes.getAccessibleContext().setAccessibleName("Codes de récupération personnels");
		recovery.add(recoveryCodes, BorderLayout.CENTER);
		saved.setMnemonic(KeyEvent.VK_J);
		saved.addActionListener(event -> updateControls());
		recovery.add(saved, BorderLayout.SOUTH);
		pages.add(configuration, "configuration");
		pages.add(recovery, "recovery");
		content.add(pages, BorderLayout.CENTER);
		progress.setIndeterminate(true);
		progress.setVisible(false);
		progress.getAccessibleContext().setAccessibleName("Enrôlement en cours");
		status.getAccessibleContext().setAccessibleName("État de l’enrôlement MFA");
		expiration.getAccessibleContext().setAccessibleName("Échéance de l’enrôlement MFA");
		primary.setMnemonic(KeyEvent.VK_V);
		primary.addActionListener(event -> submit());
		back.setText("Annuler et revenir");
		back.setMnemonic(KeyEvent.VK_R);
		back.addActionListener(event -> requestClose());
		JPanel buttons = new JPanel(new GridLayout(1, 2, 12, 0));
		buttons.setOpaque(false);
		buttons.add(back);
		buttons.add(primary);
		JPanel feedback = new JPanel(new BorderLayout(0, 8));
		feedback.setOpaque(false);
		feedback.add(expiration, BorderLayout.NORTH);
		feedback.add(status, BorderLayout.CENTER);
		feedback.add(progress, BorderLayout.SOUTH);
		JPanel footer = new JPanel(new BorderLayout(0, 12));
		footer.setOpaque(false);
		footer.add(feedback, BorderLayout.CENTER);
		footer.add(buttons, BorderLayout.SOUTH);
		content.add(footer, BorderLayout.SOUTH);
		getRootPane().setDefaultButton(primary);
		getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeEnrollment");
		getRootPane().getActionMap().put("closeEnrollment", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent event) {
				requestClose();
			}
		});
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent event) {
				requestClose();
			}
		});
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		setContentPane(content);
		setIconImage(new ImageIcon(getClass().getResource("/icons/gestion-taskbar.png")).getImage());
		applyAppTheme(UIUtils.isDarkMode());
		updateControls();
		pack();
		setResizable(false);
		setLocationRelativeTo(owner);
	}

	public void setActions(Runnable onConfirm, Runnable onRetry, Runnable onCancel, Runnable onExpire) {
		this.onConfirm = Objects.requireNonNull(onConfirm);
		this.onRetry = Objects.requireNonNull(onRetry);
		this.onCancel = Objects.requireNonNull(onCancel);
		this.onExpire = Objects.requireNonNull(onExpire);
		deadline = System.nanoTime() + Duration.ofMinutes(10).toNanos();
		timer.start();
		updateControls();
	}

	public String code() {
		return code.getText();
	}

	public void setBusy(boolean busy, boolean confirming) {
		this.busy = busy;
		this.confirming = busy && confirming;
		if (busy) {
			showMessage(confirming ? "Vérification du code…" : "Ouverture du parcours sécurisé…", false);
		}
		updateControls();
	}

	public void showEnrollment(MfaEnrollmentObservation observation) {
		setupKey.setText(observation.enrollment().setupKey());
		configured = true;
		deadline = Math.min(deadline, System.nanoTime() + observation.remaining().toNanos());
		showMessage("Cette clé est personnelle. Ajoutez-la à votre authentificateur, puis saisissez le code qu’il affiche.", false);
		updateDeadline();
		updateControls();
		code.requestFocusInWindow();
	}

	public void showRecoveryCodes(MfaRecoveryCodes codes) {
		completed = true;
		expired = false;
		busy = false;
		confirming = false;
		timer.stop();
		clearConfiguration();
		recoveryCodes.setText(String.join("\n", codes.codes()));
		recoveryCodes.setCaretPosition(0);
		((CardLayout) pages.getLayout()).show(pages, "recovery");
		expiration.setText("Configuration terminée — codes affichés une seule fois");
		showMessage("Aucune sauvegarde automatique. À la connexion, attendez un nouveau code TOTP : le code de confirmation est déjà utilisé.", false);
		updateControls();
		saved.requestFocusInWindow();
	}

	public void showMessage(String message, boolean warning) {
		this.warning = warning;
		status.setText(message);
		status.setCaretPosition(0);
		status.setForeground(warning ? UIUtils.getChampagneColor() : UIUtils.getTurquoiseColor());
	}

	public void showTerminal(String message) {
		terminal = true;
		busy = false;
		confirming = false;
		timer.stop();
		clearConfiguration();
		expiration.setText("Parcours fermé");
		showMessage(message, true);
		updateControls();
	}

	public void clearCode() {
		code.setText("");
	}

	public void retryAfter(Duration delay) {
		retryUntil = System.nanoTime() + Duration.ofSeconds(Math.max(1, Math.min(86400, delay.toSeconds()))).toNanos();
		updateControls();
	}

	public boolean isCompleted() {
		return completed;
	}

	@Override
	public void dispose() {
		timer.stop();
		clearConfiguration();
		recoveryCodes.setText("");
		saved.setSelected(false);
		super.dispose();
	}

	@Override
	public void applyAppTheme(boolean dark) {
		content.setBackground(UIUtils.getBackgroundColor());
		pages.setBackground(UIUtils.getSurfaceColor());
		pages.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UIUtils.getBorderColor(), 1, true),
				BorderFactory.createEmptyBorder(16, 18, 16, 18)));
		configuration.setOpaque(false);
		recovery.setOpaque(false);
		title.setForeground(UIUtils.getTextColor());
		expiration.setForeground(UIUtils.getPlaceholderColor());
		for (var component : configuration.getComponents()) {
			if (component instanceof JLabel label) {
				label.setForeground(UIUtils.getTextColor());
			}
		}
		for (JTextArea area : java.util.List.of(instructions, recoveryInstructions, recoveryCodes)) {
			area.setForeground(UIUtils.getTextColor());
		}
		for (JCheckBox check : java.util.List.of(reveal, saved)) {
			check.setOpaque(false);
			check.setForeground(UIUtils.getTextColor());
		}
		UIUtils.applyTheme(setupKey);
		UIUtils.applyTheme(code);
		logo.setIcon(new FlatSVGIcon(getClass().getResource(dark ? "/icons/gestion-header-dark.svg"
				: "/icons/gestion-header-light.svg")).derive(48, 48));
		UIUtils.styleActionButton(back, "neutral");
		UIUtils.styleActionButton(primary, "primary");
		status.setForeground(warning ? UIUtils.getChampagneColor() : UIUtils.getTurquoiseColor());
	}

	private void submit() {
		if (!primary.isEnabled()) {
			return;
		}
		if (completed) {
			onCancel.run();
		} else if (configured) {
			if (!code().matches("[0-9]{6}")) {
				showMessage("Le code de l’authentificateur doit contenir exactement six chiffres.", true);
				return;
			}
			onConfirm.run();
		} else {
			onRetry.run();
		}
	}

	private void requestClose() {
		if (completed && !saved.isSelected() && JOptionPane.showConfirmDialog(this,
				"Vos codes ne seront plus affichés. Voulez-vous quitter sans confirmer leur conservation ?",
				"Codes de récupération", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
			return;
		}
		if (confirming && JOptionPane.showConfirmDialog(this,
				"Une confirmation est en cours. Quitter peut empêcher la réception des codes de récupération. Quitter quand même ?",
				"Vérification en cours", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
			return;
		}
		if (!completed && !terminal && !confirming && JOptionPane.showConfirmDialog(this,
				"Quitter interrompt l’enrôlement. Votre compte est activé ; une reprise après fermeture peut nécessiter votre administrateur. Quitter ?",
				"Interrompre la configuration", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
			return;
		}
		if (onCancel != null) {
			onCancel.run();
		} else {
			dispose();
		}
	}

	private void clearConfiguration() {
		setupKey.setText("");
		setupKey.setEchoChar(hiddenKey);
		reveal.setSelected(false);
		clearCode();
	}

	private void updateDeadline() {
		if (completed || terminal) {
			return;
		}
		long remaining = deadline - System.nanoTime();
		if (remaining <= 0 && !expired) {
			expired = true;
			clearConfiguration();
			onExpire.run();
		}
		if (!terminal) {
			long seconds = Math.max(0, remaining / 1_000_000_000L);
			expiration.setText("Fin du parcours dans " + seconds / 60 + " min " + seconds % 60 + " s");
			updateControls();
		}
	}

	private void updateControls() {
		boolean waiting = retryUntil - System.nanoTime() > 0;
		setupKey.setEnabled(configured && !terminal && !expired);
		reveal.setEnabled(configured && !terminal && !expired);
		code.setEnabled(configured && !busy && !terminal && !expired && !waiting);
		primary.setText(completed ? "Retour à la connexion" : configured ? "Confirmer le code" : "Réessayer");
		primary.setEnabled(onConfirm != null && !busy && !terminal && !expired && !waiting && (!completed || saved.isSelected()));
		back.setVisible(!completed);
		back.setText(terminal ? "Retour à la connexion" : "Annuler et revenir");
		progress.setVisible(busy);
	}

	private static JTextArea text(String value, int rows) {
		JTextArea area = new JTextArea(value, rows, 39);
		area.setEditable(false);
		area.setFocusable(false);
		area.setOpaque(false);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		return area;
	}
}
