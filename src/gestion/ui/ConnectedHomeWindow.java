package gestion.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;
import java.util.stream.IntStream;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;

import gestion.app.ConnectedApplicationContext;

public final class ConnectedHomeWindow {

	private final ConnectedApplicationContext context;
	private final JFrame frame;
	private final ApplicationHeader header;

	public ConnectedHomeWindow(ConnectedApplicationContext context, Runnable onAccount, Runnable onWindowClose) {
		this.context = Objects.requireNonNull(context, "Le contexte ne peut pas être null.");
		Objects.requireNonNull(onAccount, "L'action du compte ne peut pas être null.");
		Objects.requireNonNull(onWindowClose, "L'action de fermeture ne peut pas être null.");

		frame = new JFrame("Gestion +");
		ImageIcon taskbarIcon = new ImageIcon(ConnectedHomeWindow.class.getResource("/icons/gestion-taskbar.png"));
		frame.setIconImages(IntStream.of(16, 24, 32, 48, 64, 128, 256)
				.mapToObj(size -> taskbarIcon.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH)).toList());
		frame.setMinimumSize(new Dimension(760, 650));
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent event) {
				onWindowClose.run();
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
		frame.add(new HomePanel(null, null, null, null, null), BorderLayout.CENTER);
		UIUtils.applyTheme(frame);
	}

	public void show() {
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
	}

	public void dispose() {
		frame.dispose();
	}
}
