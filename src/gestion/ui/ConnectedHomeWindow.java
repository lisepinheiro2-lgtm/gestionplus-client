package gestion.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;
import java.util.stream.IntStream;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import gestion.app.ConnectedApplicationContext;
import gestion.app.SessionStatus;

public final class ConnectedHomeWindow {

	private final ConnectedApplicationContext context;
	private final JFrame frame;
	private final ApplicationHeader header;
	private final SessionBanner sessionBanner;
	private final CardLayout pages = new CardLayout();
	private final JPanel content = new JPanel(pages);

	public ConnectedHomeWindow(ConnectedApplicationContext context, Runnable onAccount, Runnable onWindowClose,
			Runnable onRetry) {
		this.context = Objects.requireNonNull(context, "Le contexte ne peut pas être null.");
		Objects.requireNonNull(onAccount, "L'action du compte ne peut pas être null.");
		Objects.requireNonNull(onWindowClose, "L'action de fermeture ne peut pas être null.");
		Objects.requireNonNull(onRetry, "L'action de vérification ne peut pas être nulle.");

		frame = new JFrame("Gestion +");
		ImageIcon taskbarIcon = new ImageIcon(ConnectedHomeWindow.class.getResource("/icons/gestion-taskbar.png"));
		frame.setIconImages(IntStream.of(16, 24, 32, 48, 64, 128, 256)
				.mapToObj(size -> taskbarIcon.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH)).toList());
		frame.setMinimumSize(new Dimension(760, 650));
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter() {
			private boolean activated;

			@Override
			public void windowClosing(WindowEvent event) {
				onWindowClose.run();
			}

			@Override
			public void windowActivated(WindowEvent event) {
				if (activated) {
					onRetry.run();
				}
				activated = true;
			}
		});

		JButton homeButton = new JButton("Accueil");
		homeButton.setPreferredSize(new Dimension(120, 40));

		header = new ApplicationHeader(frame, homeButton);
		header.setPageTitle("Accueil");
		header.setIdentity(context.user().userName());
		header.setAccountAction(onAccount);

		frame.setLayout(new BorderLayout());
		frame.add(header, BorderLayout.NORTH);
		sessionBanner = new SessionBanner(onRetry);
		content.add(new HomePanel(null, null, null, null, null), "home");
		JPanel suspended = new JPanel(new BorderLayout());
		JLabel suspendedMessage = new JLabel("L’accès connecté est suspendu pendant la vérification.", JLabel.CENTER);
		suspended.add(suspendedMessage, BorderLayout.CENTER);
		content.add(suspended, "suspended");
		JPanel body = new JPanel(new BorderLayout());
		body.add(sessionBanner, BorderLayout.NORTH);
		body.add(content, BorderLayout.CENTER);
		frame.add(body, BorderLayout.CENTER);
		showSessionStatus(new SessionStatus(SessionStatus.State.CHECKING, 0));
		UIUtils.applyTheme(frame);
	}

	public void show() {
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}

	public void dispose() {
		header.setIdentity("");
		frame.dispose();
	}

	public void showSessionStatus(SessionStatus status) {
		if (!context.isActive() && status.state() != SessionStatus.State.CLOSING) {
			return;
		}
		sessionBanner.showStatus(status);
		pages.show(content, status.allowsBusinessOperations() ? "home" : "suspended");
	}
}
