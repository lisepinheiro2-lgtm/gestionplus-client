package gestion.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

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
import javax.swing.JTextField;
import javax.swing.KeyStroke;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import gestion.ui.raven.RavenStyledButton;

public final class LoginWindow extends JDialog implements AppThemeAware {

	private final JPanel loginPanel;
	private final JPanel formPanel;
	private final JLabel logo;
	private final JLabel windowTitle;
	private final JLabel subtitle;
	private final JLabel usernameLabel;
	private final JLabel passwordLabel;
	private final JLabel availability;
	private final JTextField usernameField;
	private final JPasswordField passwordField;
	private final RavenStyledButton loginButton;
	private final RavenStyledButton backButton;
	private boolean modeSelectionRequested;

	public LoginWindow() {
		super((Frame) null, "Connexion — Gestion+", true);
		loginPanel = new JPanel(new BorderLayout(0, 24));
		formPanel = new JPanel(new GridBagLayout());
		logo = new JLabel();
		windowTitle = new JLabel("Connexion à Gestion+");
		subtitle = new JLabel("Accédez à votre espace de travail.");
		usernameLabel = new JLabel("Identifiant");
		passwordLabel = new JLabel("Mot de passe");
		availability = new JLabel("La connexion n’est pas encore disponible.", JLabel.CENTER);
		usernameField = new JTextField(24);
		passwordField = new JPasswordField(24);
		loginButton = new RavenStyledButton();
		backButton = new RavenStyledButton();

		loginPanel.setBorder(BorderFactory.createEmptyBorder(28, 32, 24, 32));
		windowTitle.setFont(windowTitle.getFont().deriveFont(Font.BOLD, 25f));
		subtitle.setFont(subtitle.getFont().deriveFont(14f));
		availability.setFont(availability.getFont().deriveFont(12f));
		logo.getAccessibleContext().setAccessibleName("Logo Gestion+");
		JPanel heading = new JPanel();
		heading.setOpaque(false);
		heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));

		for (JLabel label : new JLabel[] { logo, windowTitle, subtitle }) {
			label.setAlignmentX(Component.CENTER_ALIGNMENT);
		}

		heading.add(logo);
		heading.add(Box.createVerticalStrut(14));
		heading.add(windowTitle);
		heading.add(Box.createVerticalStrut(8));
		heading.add(subtitle);
		loginPanel.add(heading, BorderLayout.NORTH);

		usernameLabel.setLabelFor(usernameField);
		usernameLabel.setDisplayedMnemonic(KeyEvent.VK_I);
		passwordLabel.setLabelFor(passwordField);
		passwordLabel.setDisplayedMnemonic(KeyEvent.VK_M);
		usernameField.getAccessibleContext().setAccessibleName("Identifiant");
		passwordField.getAccessibleContext().setAccessibleName("Mot de passe");

		for (JTextField field : new JTextField[] { usernameField, passwordField }) {
			Dimension preferred = field.getPreferredSize();
			field.setPreferredSize(new Dimension(Math.max(380, preferred.width), Math.max(44, preferred.height)));
		}

		loginButton.setText("Se connecter");
		loginButton.setEnabled(false);
		loginButton.setToolTipText("La connexion n’est pas encore disponible.");
		loginButton.getAccessibleContext().setAccessibleDescription("Connexion indisponible pour le moment.");
		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.weightx = 1;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.anchor = GridBagConstraints.LINE_START;
		constraints.gridy = 0;
		constraints.insets = new Insets(0, 0, 8, 0);
		formPanel.add(usernameLabel, constraints);
		constraints.gridy = 1;
		constraints.insets = new Insets(0, 0, 18, 0);
		formPanel.add(usernameField, constraints);
		constraints.gridy = 2;
		constraints.insets = new Insets(0, 0, 8, 0);
		formPanel.add(passwordLabel, constraints);
		constraints.gridy = 3;
		constraints.insets = new Insets(0, 0, 20, 0);
		formPanel.add(passwordField, constraints);
		constraints.gridy = 4;
		constraints.insets = new Insets(0, 0, 12, 0);
		formPanel.add(availability, constraints);
		constraints.gridy = 5;
		constraints.insets = new Insets(0, 0, 0, 0);
		formPanel.add(loginButton, constraints);
		loginPanel.add(formPanel, BorderLayout.CENTER);

		backButton.setText("Retour au choix du mode");
		backButton.setMnemonic(KeyEvent.VK_R);
		backButton.putClientProperty("gestion.icon", NavigationIcon.Kind.CHEVRON_LEFT);
		backButton.addActionListener(event -> {
			modeSelectionRequested = true;
			dispose();
		});
		JPanel navigationPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
		navigationPanel.setOpaque(false);
		navigationPanel.add(backButton);
		loginPanel.add(navigationPanel, BorderLayout.SOUTH);
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
		ImageIcon taskbarIcon = new ImageIcon(LoginWindow.class.getResource("/icons/gestion-taskbar.png"));
		setIconImages(java.util.stream.IntStream.of(16, 24, 32, 48, 64, 128, 256)
				.mapToObj(size -> taskbarIcon.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH)).toList());
		applyAppTheme(UIUtils.isDarkMode());
		pack();
		setResizable(false);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
	}

	public void showLogin() {
		modeSelectionRequested = false;
		usernameField.setText("");
		passwordField.setText("");
		applyAppTheme(UIUtils.isDarkMode());
		setVisible(true);
	}

	public boolean isModeSelectionRequested() {
		return modeSelectionRequested;
	}

	@Override
	public void dispose() {
		usernameField.setText("");
		passwordField.setText("");
		super.dispose();
	}

	@Override
	public void applyAppTheme(boolean dark) {
		loginPanel.setBackground(UIUtils.getBackgroundColor());
		formPanel.setBackground(UIUtils.getSurfaceColor());
		formPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(UIUtils.getBorderColor(), 1, true),
				BorderFactory.createEmptyBorder(24, 24, 24, 24)));
		windowTitle.setForeground(UIUtils.getTextColor());
		subtitle.setForeground(UIUtils.getPlaceholderColor());
		usernameLabel.setForeground(UIUtils.getTextColor());
		passwordLabel.setForeground(UIUtils.getTextColor());
		availability.setForeground(UIUtils.getPlaceholderColor());
		logo.setIcon(new FlatSVGIcon(LoginWindow.class.getResource(
				dark ? "/icons/gestion-header-dark.svg" : "/icons/gestion-header-light.svg")).derive(64, 64));
		UIUtils.applyTheme(usernameField);
		UIUtils.applyTheme(passwordField);
		UIUtils.styleActionButton(loginButton, "primary");
		UIUtils.styleActionButton(backButton, "neutral");
		loginButton.setPreferredSize(new Dimension(loginButton.getPreferredSize().width, 44));
		backButton.setPreferredSize(new Dimension(backButton.getPreferredSize().width, 40));
	}
}
