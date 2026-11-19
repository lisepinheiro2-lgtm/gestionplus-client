package gestion.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Image;
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
import javax.swing.KeyStroke;

import com.formdev.flatlaf.extras.FlatSVGIcon;

public final class LaunchSelectorWindow extends JDialog implements AppThemeAware {

	private final JPanel selectionPanel;
	private final JPanel buttonsPanel;
	private final JLabel windowTitle;
	private final JLabel subtitle;
	private final JLabel logo;
	private final JLabel availability;
	private final HomeCardButton demoButton;
	private final HomeCardButton connectedButton;
	private LaunchMode selectedMode;

	public LaunchSelectorWindow() {
		super((Frame) null, "Gestion+", true);
		selectionPanel = new JPanel(new BorderLayout(0, 24));
		buttonsPanel = new JPanel(new GridLayout(2, 1, 0, 14));
		windowTitle = new JLabel("Bienvenue sur Gestion+");
		subtitle = new JLabel("Choisissez votre mode de lancement.");
		logo = new JLabel();
		availability = new JLabel("La connexion au serveur est en préparation.");
		demoButton = new HomeCardButton("Démonstration", "Explorez Gestion+ avec des données fictives.",
				NavigationIcon.Kind.HOME, UIUtils.getTextColor(), true);
		connectedButton = new HomeCardButton("Connexion", "Retrouvez votre espace de travail connecté.",
				NavigationIcon.Kind.USER, UIUtils.getTurquoiseColor(), true);

		selectionPanel.setBorder(BorderFactory.createEmptyBorder(28, 32, 24, 32));
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
		selectionPanel.add(heading, BorderLayout.NORTH);
		configureButton(demoButton, KeyEvent.VK_D, LaunchMode.DEMO);
		configureButton(connectedButton, KeyEvent.VK_C, LaunchMode.CONNECTED);
		buttonsPanel.add(demoButton);
		buttonsPanel.add(connectedButton);
		selectionPanel.add(buttonsPanel, BorderLayout.CENTER);
		availability.setHorizontalAlignment(JLabel.CENTER);
		selectionPanel.add(availability, BorderLayout.SOUTH);
		getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeSelector");
		getRootPane().getActionMap().put("closeSelector", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent event) {
				selectedMode = null;
				dispose();
			}
		});

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowOpened(WindowEvent event) {
				demoButton.requestFocusInWindow();
			}
		});

		setContentPane(selectionPanel);
		ImageIcon taskbarIcon = new ImageIcon(LaunchSelectorWindow.class.getResource("/icons/gestion-taskbar.png"));
		setIconImages(java.util.stream.IntStream.of(16, 24, 32, 48, 64, 128, 256)
				.mapToObj(size -> taskbarIcon.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH)).toList());
		applyAppTheme(UIUtils.isDarkMode());
		pack();
		setResizable(false);
		setLocationRelativeTo(null);
		setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
	}

	public LaunchMode askLaunchMode() {
		selectedMode = null;
		applyAppTheme(UIUtils.isDarkMode());
		setVisible(true);
		return selectedMode;
	}

	@Override
	public void applyAppTheme(boolean dark) {
		selectionPanel.setBackground(UIUtils.getBackgroundColor());
		buttonsPanel.setOpaque(false);
		windowTitle.setForeground(UIUtils.getTextColor());
		subtitle.setForeground(UIUtils.getPlaceholderColor());
		availability.setForeground(UIUtils.getPlaceholderColor());
		logo.setIcon(new FlatSVGIcon(LaunchSelectorWindow.class.getResource(
				dark ? "/icons/gestion-header-dark.svg" : "/icons/gestion-header-light.svg")).derive(64, 64));
		demoButton.applyAppTheme(dark);
		connectedButton.applyAppTheme(dark);
	}

	private void configureButton(HomeCardButton button, int mnemonic, LaunchMode mode) {
		button.setPreferredSize(new Dimension(490, 120));
		button.setMnemonic(mnemonic);
		button.addActionListener(event -> {
			selectedMode = mode;
			dispose();
		});
		button.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "chooseMode");
		button.getActionMap().put("chooseMode", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent event) {
				button.doClick();
			}
		});
	}
}
