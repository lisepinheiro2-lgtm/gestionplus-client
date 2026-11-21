package gestion.ui;

import static gestion.ui.WindowTestSupport.awaitWindow;
import static gestion.ui.WindowTestSupport.component;
import static gestion.ui.WindowTestSupport.named;
import static gestion.ui.WindowTestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;

import javax.swing.JFrame;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import gestion.app.ApplicationContext;
import gestion.app.DemoDataInitializer;

@Tag("desktop")
@Isolated
class DemoIndependenceTest {

	@TempDir
	Path directory;

	@Test
	void theCsvDemoStillOpensItsBusinessScreensWithoutAUsableServerConfiguration() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless(), "Ce parcours nécessite un environnement graphique.");
		String previousUrl = System.getProperty("gestionplus.server.url");
		Set<Window> previousWindows = Set.copyOf(Arrays.asList(Window.getWindows()));
		try {
			System.setProperty("gestionplus.server.url", "not-a-server-url");
			DemoDataInitializer.initialize(directory);
			ApplicationContext demo = new ApplicationContext(directory);
			onEdt(demo::openWindow);
			JFrame frame = awaitWindow(JFrame.class,
					candidate -> !previousWindows.contains(candidate) && "Gestion +".equals(candidate.getTitle()));
			onEdt(() -> {
				HomePanel home = component(frame, HomePanel.class, candidate -> true);
				assertTrue(home.isShowing());
				HomeCardButton personnel = named(home, HomeCardButton.class, "Personnel");
				assertTrue(personnel.isEnabled());
				personnel.doClick(0);
				assertTrue(component(frame, gestion.ui.personnel.PersonnelPanel.class, candidate -> true).isShowing());
			});
		} finally {
			try {
				onEdt(() -> {
					for (Window window : Window.getWindows()) {
						if (!previousWindows.contains(window)) {
							window.dispose();
						}
					}
				});
			} finally {
				if (previousUrl == null) {
					System.clearProperty("gestionplus.server.url");
				} else {
					System.setProperty("gestionplus.server.url", previousUrl);
				}
			}
		}
	}
}

