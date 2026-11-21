package gestion.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
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

import com.formdev.flatlaf.extras.FlatSVGIcon;

import gestion.ui.raven.RavenStyledButton;

public final class LoginWindow extends JDialog implements AppThemeAware {

	private static final int MAX_USERNAME_LENGTH = 255;
	private static final int MAX_PASSWORD_LENGTH = 128;
	private static final int TOTP_LENGTH = 6;

	private final JPanel loginPanel;
	private final JPanel formPanel;
	private final JLabel logo;
	private final JLabel windowTitle;
	private final JLabel subtitle;
	private final JLabel usernameLabel;
	private final JLabel passwordLabel;
	private final JLabel totpLabel;
	private final JLabel totpHint;
	private final JTextArea statusMessage;
	private String sessionNotice;
	private final JProgressBar progressBar;
	private final JTextField usernameField;
	private final JPasswordField passwordField;
	private final JTextField totpField;
	private final RavenStyledButton loginButton;
	private final RavenStyledButton backButton;
	private Runnable loginAction;
	private DisplayState displayState = DisplayState.UNAVAILABLE;
	private boolean modeSelectionRequested;

	private enum DisplayState {
		UNAVAILABLE, READY, SUBMITTING, ERROR, SUCCESS
	}

	public LoginWindow() {
		super((Frame) null, "Connexion — Gestion+", true);
		loginPanel = new JPanel(new BorderLayout(0, 20));
		formPanel = new JPanel(new GridBagLayout());
		logo = new JLabel();
		windowTitle = new JLabel("Connexion à Gestion+");
		subtitle = new JLabel("Accédez à votre espace de travail.");
		usernameLabel = new JLabel("Identifiant");
		passwordLabel = new JLabel("Mot de passe");
		totpLabel = new JLabel("Code d’authentification");
		totpHint = new JLabel("6 chiffres, si la double authentification est activée.");
		statusMessage = new JTextArea(2, 30);
		progressBar = new JProgressBar();
		usernameField = new JTextField(24);
		passwordField = new JPasswordField(24);
		totpField = new JTextField(TOTP_LENGTH);
		loginButton = new RavenStyledButton();
		backButton = new RavenStyledButton();

		loginPanel.setBorder(BorderFactory.createEmptyBorder(24, 28, 20, 28));
		windowTitle.setFont(windowTitle.getFont().deriveFont(Font.BOLD, 25f));
		subtitle.setFont(subtitle.getFont().deriveFont(14f));
		totpHint.setFont(totpHint.getFont().deriveFont(12f));
		logo.getAccessibleContext().setAccessibleName("Logo Gestion+");
		JPanel heading = new JPanel(new BorderLayout(16, 0));
		heading.setOpaque(false);
		JPanel words = new JPanel();
		words.setOpaque(false);
		words.setLayout(new BoxLayout(words, BoxLayout.Y_AXIS));
		windowTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
		subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
		words.add(Box.createVerticalGlue());
		words.add(windowTitle);
		words.add(Box.createVerticalStrut(6));
		words.add(subtitle);
		words.add(Box.createVerticalGlue());
		heading.add(logo, BorderLayout.WEST);
		heading.add(words, BorderLayout.CENTER);
		loginPanel.add(heading, BorderLayout.NORTH);

		usernameLabel.setLabelFor(usernameField);
		usernameLabel.setDisplayedMnemonic(KeyEvent.VK_I);
		passwordLabel.setLabelFor(passwordField);
		passwordLabel.setDisplayedMnemonic(KeyEvent.VK_M);
		totpLabel.setLabelFor(totpField);
		totpLabel.setDisplayedMnemonic(KeyEvent.VK_C);
		usernameField.getAccessibleContext().setAccessibleName("Identifiant");
		passwordField.getAccessibleContext().setAccessibleName("Mot de passe");
		totpField.getAccessibleContext().setAccessibleName("Code d’authentification");
		totpField.getAccessibleContext()
				.setAccessibleDescription("Code à six chiffres de votre application d’authentification, si requis.");
		totpField.setToolTipText("Code à six chiffres de votre application d’authentification, si requis.");

		for (JTextField field : new JTextField[] { usernameField, passwordField, totpField }) {
			Dimension preferred = field.getPreferredSize();
			field.setPreferredSize(new Dimension(Math.max(380, preferred.width), Math.max(40, preferred.height)));
			field.addActionListener(event -> loginButton.doClick());
		}

		loginButton.setText("Se connecter");
		loginButton.setMnemonic(KeyEvent.VK_S);
		loginButton.addActionListener(event -> submitLogin());
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.weightx = 1;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.anchor = GridBagConstraints.LINE_START;
		constraints.gridy = 0;
		constraints.insets = new Insets(0, 0, 6, 0);
		formPanel.add(usernameLabel, constraints);
		constraints.gridy = 1;
		constraints.insets = new Insets(0, 0, 14, 0);
		formPanel.add(usernameField, constraints);
		constraints.gridy = 2;
		constraints.insets = new Insets(0, 0, 6, 0);
		formPanel.add(passwordLabel, constraints);
		constraints.gridy = 3;
		constraints.insets = new Insets(0, 0, 14, 0);
		formPanel.add(passwordField, constraints);
		constraints.gridy = 4;
		constraints.insets = new Insets(0, 0, 6, 0);
		formPanel.add(totpLabel, constraints);
		constraints.gridy = 5;
		formPanel.add(totpField, constraints);
		constraints.gridy = 6;
		constraints.insets = new Insets(0, 0, 0, 0);
		formPanel.add(totpHint, constraints);
		loginPanel.add(formPanel, BorderLayout.CENTER);

		backButton.setText("Retour au choix du mode");
		backButton.setMnemonic(KeyEvent.VK_R);
		backButton.putClientProperty("gestion.icon", NavigationIcon.Kind.CHEVRON_LEFT);
		backButton.addActionListener(event -> {
			modeSelectionRequested = true;
			dispose();
		});
		JPanel navigationPanel = new JPanel(new GridLayout(1, 2, 12, 0));
		navigationPanel.setOpaque(false);
		navigationPanel.add(backButton);
		navigationPanel.add(loginButton);
		statusMessage.setFont(subtitle.getFont().deriveFont(12f));
		statusMessage.setEditable(false);
		statusMessage.setFocusable(false);
		statusMessage.setLineWrap(true);
		statusMessage.setWrapStyleWord(true);
		statusMessage.setOpaque(false);
		statusMessage.setBorder(null);
		statusMessage.getAccessibleContext().setAccessibleName("État de la connexion");
		progressBar.setBorderPainted(false);
		progressBar.getAccessibleContext().setAccessibleName("Connexion en cours");
		JPanel progressPanel = new JPanel(new BorderLayout());
		progressPanel.setOpaque(false);
		progressPanel.setPreferredSize(new Dimension(0, 4));
		progressPanel.add(progressBar, BorderLayout.CENTER);
		JPanel statusPanel = new JPanel(new BorderLayout(0, 8));
		statusPanel.setOpaque(false);
		statusPanel.add(statusMessage, BorderLayout.CENTER);
		statusPanel.add(progressPanel, BorderLayout.SOUTH);
		JPanel footer = new JPanel(new BorderLayout(0, 12));
		footer.setOpaque(false);
		footer.add(statusPanel, BorderLayout.CENTER);
		footer.add(navigationPanel, BorderLayout.SOUTH);
		loginPanel.add(footer, BorderLayout.SOUTH);
		getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeLogin");
		getRootPane().getActionMap().put("closeLogin", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent event) {
				dispose();
			}
		});

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowOpened(WindowEvent event) {
				usernameField.requestFocusInWindow();
			}
		});

		setContentPane(loginPanel);
		getRootPane().setDefaultButton(loginButton);
		ImageIcon taskbarIcon = new ImageIcon(LoginWindow.class.getResource("/icons/gestion-taskbar.png"));
		setIconImages(java.util.stream.IntStream.of(16, 24, 32, 48, 64, 128, 256)
				.mapToObj(size -> taskbarIcon.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH)).toList());
		resetDisplay();
		applyAppTheme(UIUtils.isDarkMode());
		pack();
		setResizable(false);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
	}

	public void showLogin() {
		modeSelectionRequested = false;
		usernameField.setText("");
		clearSensitiveFields();
		resetDisplay();
		if (sessionNotice != null) {
			setDisplayState(DisplayState.ERROR, sessionNotice);
			sessionNotice = null;
		}
		applyAppTheme(UIUtils.isDarkMode());
		setVisible(true);
	}

	public void setSessionNotice(String message) {
		sessionNotice = Objects.requireNonNull(message);
	}

	public boolean isModeSelectionRequested() {
		return modeSelectionRequested;
	}

	public void setLoginAction(Runnable loginAction) {
		this.loginAction = loginAction;
		resetDisplay();
	}

	public String getUsername() {
		return usernameField.getText();
	}

	public char[] getPassword() {
		return passwordField.getPassword();
	}

	public String getTotpCode() {
		return totpField.getText();
	}

	public void showSubmitting() {

		if (isDisplayable()) {
			setDisplayState(DisplayState.SUBMITTING, "Connexion en cours…");
		}

	}

	public void showAuthenticationRejected() {
		showError("Identifiant, mot de passe ou code d’authentification incorrect.");
	}

	public void showError(String message) {
		Objects.requireNonNull(message, "Le message ne peut pas être null.");

		if (isDisplayable()) {
			clearSensitiveFields();
			setDisplayState(DisplayState.ERROR, message);
			passwordField.requestFocusInWindow();
		}

	}

	public void showSuccess() {

		if (isDisplayable()) {
			clearSensitiveFields();
			setDisplayState(DisplayState.SUCCESS, "Connexion réussie.");
		}

	}

	@Override
	public void dispose() {
		usernameField.setText("");
		clearSensitiveFields();
		super.dispose();
	}

	@Override
	public void applyAppTheme(boolean dark) {
		loginPanel.setBackground(UIUtils.getBackgroundColor());
		formPanel.setBackground(UIUtils.getSurfaceColor());
		formPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(UIUtils.getBorderColor(), 1, true),
				BorderFactory.createEmptyBorder(20, 20, 20, 20)));
		windowTitle.setForeground(UIUtils.getTextColor());
		subtitle.setForeground(UIUtils.getPlaceholderColor());
		usernameLabel.setForeground(UIUtils.getTextColor());
		passwordLabel.setForeground(UIUtils.getTextColor());
		totpLabel.setForeground(UIUtils.getTextColor());
		totpHint.setForeground(UIUtils.getPlaceholderColor());
		updateStatusColor();
		progressBar.setForeground(UIUtils.getTurquoiseColor());
		progressBar.setBackground(UIUtils.getBorderColor());
		logo.setIcon(new FlatSVGIcon(LoginWindow.class.getResource(
				dark ? "/icons/gestion-header-dark.svg" : "/icons/gestion-header-light.svg")).derive(56, 56));
		UIUtils.applyTheme(usernameField);
		UIUtils.applyTheme(passwordField);
		UIUtils.applyTheme(totpField);
		UIUtils.styleActionButton(loginButton, "primary");
		UIUtils.styleActionButton(backButton, "neutral");
		loginButton.setPreferredSize(new Dimension(loginButton.getPreferredSize().width, 40));
		backButton.setPreferredSize(new Dimension(backButton.getPreferredSize().width, 40));
	}

	private void submitLogin() {

		if (loginAction == null || displayState == DisplayState.SUBMITTING || displayState == DisplayState.SUCCESS) {
			return;
		}

		if (usernameField.getText().isBlank()) {
			showInputError(usernameField, "Saisissez votre identifiant.");
			return;
		}

		if (usernameField.getDocument().getLength() > MAX_USERNAME_LENGTH) {
			showInputError(usernameField, "L’identifiant ne doit pas dépasser 255 caractères.");
			return;
		}

		if (passwordField.getDocument().getLength() == 0) {
			showInputError(passwordField, "Saisissez votre mot de passe.");
			return;
		}

		if (passwordField.getDocument().getLength() > MAX_PASSWORD_LENGTH) {
			showInputError(passwordField, "Le mot de passe ne doit pas dépasser 128 caractères.");
			return;
		}

		String totp = totpField.getText();

		if (!totp.isEmpty() && !totp.matches("[0-9]{" + TOTP_LENGTH + "}")) {
			showInputError(totpField, "Le code d’authentification doit contenir exactement 6 chiffres.");
			return;
		}

		showSubmitting();

		try {
			loginAction.run();
		} catch (RuntimeException failure) {
			showError("Impossible de démarrer la connexion. Veuillez réessayer.");
		}

	}

	private void showInputError(JTextField field, String message) {
		setDisplayState(DisplayState.ERROR, message);
		field.requestFocusInWindow();
	}

	private void resetDisplay() {
		setDisplayState(loginAction == null ? DisplayState.UNAVAILABLE : DisplayState.READY,
				loginAction == null ? "La connexion n’est pas encore disponible."
						: "Saisissez vos informations de connexion.");
	}

	private void setDisplayState(DisplayState state, String message) {
		displayState = state;
		statusMessage.setText(message);
		statusMessage.setCaretPosition(0);
		boolean editable = state != DisplayState.SUBMITTING && state != DisplayState.SUCCESS;
		usernameField.setEnabled(editable);
		passwordField.setEnabled(editable);
		totpField.setEnabled(editable);
		backButton.setEnabled(editable);
		loginButton.setEnabled(loginAction != null && editable);
		loginButton.setText(state == DisplayState.SUBMITTING ? "Connexion…"
				: state == DisplayState.SUCCESS ? "Connecté" : "Se connecter");
		loginButton.setToolTipText(loginAction == null ? "La connexion n’est pas encore disponible." : null);
		loginButton.getAccessibleContext().setAccessibleDescription(message);
		progressBar.setIndeterminate(state == DisplayState.SUBMITTING);
		progressBar.setVisible(state == DisplayState.SUBMITTING);
		updateStatusColor();
	}

	private void updateStatusColor() {
		statusMessage.setForeground(switch (displayState) {
		case ERROR -> UIUtils.getChampagneColor();
		case SUBMITTING, SUCCESS -> UIUtils.getTurquoiseColor();
		default -> UIUtils.getPlaceholderColor();
		});
	}

	private void clearSensitiveFields() {
		passwordField.setText("");
		totpField.setText("");
	}
}
