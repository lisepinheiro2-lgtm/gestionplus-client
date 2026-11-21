package gestion.ui;

import static gestion.net.LoopbackHttpFixture.json;
import static gestion.ui.WindowTestSupport.awaitWindow;
import static gestion.ui.WindowTestSupport.component;
import static gestion.ui.WindowTestSupport.named;
import static gestion.ui.WindowTestSupport.onEdt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.JTextArea;
import javax.swing.Timer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import gestion.Main;
import gestion.net.LoopbackHttpFixture;
import gestion.net.LoopbackHttpFixture.CapturedRequest;
import gestion.net.LoopbackHttpFixture.Reply;

@Tag("desktop")
@Isolated
class ConnectedSessionLifecycleTest {

	private static final String SERVER_URL_PROPERTY = "gestionplus.server.url";
	private static final String FIRST_USER = "fictitious_account_a";
	private static final String SECOND_USER = "fictitious_account_b";
	private String originalServerUrl;
	private Set<Window> previousWindows;
	private FutureTask<Void> application;
	private Thread applicationThread;

	@BeforeEach
	void rememberTheEnvironment() throws Exception {
		assumeFalse(GraphicsEnvironment.isHeadless(), "Ce parcours nécessite un environnement graphique.");
		originalServerUrl = System.getProperty(SERVER_URL_PROPERTY);
		onEdt(() -> previousWindows = Set.copyOf(Arrays.asList(Window.getWindows())));
	}

	@AfterEach
	void stopTheApplicationAndRestoreTheEnvironment() throws Exception {

		if (previousWindows == null) {
			return;
		}

		Timer cleanup = new Timer(20, event -> closeCreatedWindows(true));
		onEdt(cleanup::start);

		try {

			if (application != null) {
				application.get(15, TimeUnit.SECONDS);
			}

		} finally {

			try {
				onEdt(() -> {
					cleanup.stop();
					closeCreatedWindows(false);
				});

				if (application != null) {
					application.cancel(true);
				}

				if (applicationThread != null) {
					applicationThread.interrupt();
					applicationThread.join(5_000);
					assertFalse(applicationThread.isAlive(), "Le lancement fictif du client ne s'est pas arrêté.");
				}

			} finally {

				if (originalServerUrl == null) {
					System.clearProperty(SERVER_URL_PROPERTY);
				} else {
					System.setProperty(SERVER_URL_PROPERTY, originalServerUrl);
				}

			}

		}

	}

	@Test
	void loginOpensHomeAndLogoutReturnsDirectlyToAFreshLoginForAnotherAccount() throws Exception {
		try (var server = connectedServer(204, FIRST_USER, SECOND_USER)) {
			startApplication(server);
			chooseConnectedMode();
			JFrame firstHome = loginAs(FIRST_USER);
			onEdt(() -> named(firstHome, JButton.class, "Compte utilisateur").doClick(0));
			LoginWindow nextLogin = awaitWindow(LoginWindow.class, candidate -> true);
			assertCleanLogin(nextLogin);
			JFrame secondHome = loginAs(SECOND_USER);
			onEdt(() -> {
				assertNotSame(firstHome, secondHome);
				assertFalse(firstHome.isDisplayable());
				assertEquals(SECOND_USER,
						named(secondHome, JLabel.class, "Utilisateur connecté : " + SECOND_USER).getText());
				secondHome.dispatchEvent(new WindowEvent(secondHome, WindowEvent.WINDOW_CLOSING));
			});
			application.get(5, TimeUnit.SECONDS);
			List<CapturedRequest> requests = businessRequests(server);
			assertEquals(List.of("GET /api/auth/csrf HTTP/1.1", "POST /login HTTP/1.1", "GET /api/auth/me HTTP/1.1",
					"GET /api/auth/csrf HTTP/1.1", "POST /logout HTTP/1.1", "GET /api/auth/csrf HTTP/1.1",
					"POST /login HTTP/1.1", "GET /api/auth/me HTTP/1.1", "GET /api/auth/csrf HTTP/1.1",
					"POST /logout HTTP/1.1"), requests.stream().map(CapturedRequest::requestLine).toList());
			assertTrue(requests.get(1).body().contains("username=" + FIRST_USER));
			assertTrue(requests.get(6).body().contains("username=" + SECOND_USER));
			assertFalse(requests.get(5).headers().containsKey("cookie"));
			assertTrue(requests.get(6).headers().get("cookie").contains("fictitious_anonymous_" + SECOND_USER));
			assertFalse(requests.get(6).headers().get("cookie").contains(FIRST_USER));
			assertTrue(requests.get(7).headers().get("cookie").contains("fictitious_authenticated_" + SECOND_USER));
			assertEquals("fictitious_logout_csrf_" + FIRST_USER, requests.get(4).headers().get("x-csrf-token"));
			assertEquals("fictitious_logout_csrf_" + SECOND_USER, requests.get(9).headers().get("x-csrf-token"));
			assertNoCreatedWindowIsShowing();
		}
	}

	@ParameterizedTest
	@CsvSource({ "403,false", "503,false", "503,true" })
	void anUnconfirmedLogoutDisposesHomeAndWarnsBeforeReturningOrQuitting(int status, boolean quitAfter)
			throws Exception {
		try (var server = connectedServer(status, FIRST_USER)) {
			startApplication(server);
			chooseConnectedMode();
			JFrame home = loginAs(FIRST_USER);
			onEdt(() -> {

				if (quitAfter) {
					home.dispatchEvent(new WindowEvent(home, WindowEvent.WINDOW_CLOSING));
				} else {
					named(home, JButton.class, "Compte utilisateur").doClick(0);
				}

			});
			JDialog warning = awaitWindow(JDialog.class,
					candidate -> "Déconnexion non confirmée".equals(candidate.getTitle()));
			onEdt(() -> {
				assertFalse(home.isDisplayable());
				String message = String.valueOf(component(warning, JOptionPane.class, candidate -> true).getMessage());
				assertTrue(message.contains("Le serveur n'a pas confirmé"));
				assertFalse(message.contains(FIRST_USER));
				assertFalse(Arrays.stream(Window.getWindows()).anyMatch(candidate -> candidate.isShowing()
						&& (candidate instanceof LoginWindow || candidate instanceof LaunchSelectorWindow)));
				warning.dispose();
			});

			if (!quitAfter) {
				LoginWindow login = awaitWindow(LoginWindow.class, candidate -> true);
				assertCleanLogin(login);
				onEdt(() -> login.dispatchEvent(new WindowEvent(login, WindowEvent.WINDOW_CLOSING)));
			}

			application.get(5, TimeUnit.SECONDS);
			assertEquals(5, businessRequests(server).size());
			assertNoCreatedWindowIsShowing();
		}
	}

	@Test
	void theLoginBackButtonCanStillOpenTheModeSelectorAfterLogout() throws Exception {
		try (var server = connectedServer(204, FIRST_USER)) {
			startApplication(server);
			chooseConnectedMode();
			JFrame home = loginAs(FIRST_USER);
			onEdt(() -> named(home, JButton.class, "Compte utilisateur").doClick(0));
			LoginWindow login = awaitWindow(LoginWindow.class, candidate -> true);
			onEdt(() -> component(login, JButton.class, button -> "Retour au choix du mode".equals(button.getText()))
					.doClick(0));
			LaunchSelectorWindow selector = awaitWindow(LaunchSelectorWindow.class, candidate -> true);
			onEdt(() -> selector.dispatchEvent(new WindowEvent(selector, WindowEvent.WINDOW_CLOSING)));
			application.get(5, TimeUnit.SECONDS);
			assertEquals(5, businessRequests(server).size());
			assertNoCreatedWindowIsShowing();
		}
	}

	private void startApplication(LoopbackHttpFixture server) {
		startApplication(server.baseUrl());
	}

	private void startApplication(String serverUrl) {
		System.setProperty(SERVER_URL_PROPERTY, serverUrl);
		application = new FutureTask<>(() -> {
			Main.main(new String[0]);
			return null;
		});
		applicationThread = new Thread(application, "fictitious-connected-client");
		applicationThread.setDaemon(true);
		applicationThread.start();
	}

	@ParameterizedTest
	@CsvSource({ "401,session n’est plus valide", "503,Le serveur n’a pas confirmé" })
	void thePublishedDeadlineTriggersVerificationThenReturnsToAFreshLogin(int finalStatus, String notice)
			throws Exception {
		Reply[] normal = repliesFor(204, FIRST_USER);
		Reply[] replies = { normal[0], normal[1], normal[2],
				json(finalStatus, "{\"code\":\"" + (finalStatus == 401 ? "AUTHENTICATION_REQUIRED" : "SERVICE_UNAVAILABLE")
						+ "\"}") };
		Instant baseTime = Instant.parse("2026-11-21T12:00:00Z");
		AtomicLong firstCheck = new AtomicLong(Long.MIN_VALUE);
		try (var server = new LoopbackHttpFixture(request -> {
			if (!isSessionCheck(request)) {
				return null;
			}
			long now = System.nanoTime();
			firstCheck.compareAndSet(Long.MIN_VALUE, now);
			long seconds = Duration.ofNanos(now - firstCheck.get()).toSeconds();
			return seconds < 3 ? sessionReply(baseTime.plusSeconds(seconds), 3 - seconds) : null;
		}, replies)) {
			startApplication(server);
			chooseConnectedMode();
			LoginWindow formerLogin = submitLoginAs(FIRST_USER);
			LoginWindow login = awaitWindow(LoginWindow.class,
					candidate -> candidate != formerLogin
							&& named(candidate, JTextArea.class, "État de la connexion").getText().contains(notice));
			assertCleanLogin(login);
			onEdt(() -> {
				assertFalse(formerLogin.isDisplayable());
				assertFalse(Arrays.stream(Window.getWindows()).anyMatch(candidate -> candidate instanceof JFrame
						&& candidate.isShowing() && "Gestion +".equals(((JFrame) candidate).getTitle())));
				login.dispatchEvent(new WindowEvent(login, WindowEvent.WINDOW_CLOSING));
			});
			application.get(5, TimeUnit.SECONDS);
			assertEquals(List.of("GET /api/auth/csrf HTTP/1.1", "POST /login HTTP/1.1", "GET /api/auth/me HTTP/1.1"),
					businessRequests(server).stream().map(CapturedRequest::requestLine).toList());
			assertTrue(server.requests().stream().filter(ConnectedSessionLifecycleTest::isSessionCheck).count() >= 2);
		}
	}

	@Test
	void anUnavailableStatusSuspendsHomeAndTheRetryRestoresItWithoutAnotherLogin() throws Exception {
		Reply[] normal = repliesFor(204, FIRST_USER);
		Reply[] replies = { normal[0], normal[1], normal[2], normal[4], normal[5] };
		AtomicBoolean recovered = new AtomicBoolean();
		try (var server = new LoopbackHttpFixture(request -> isSessionCheck(request)
				? recovered.get() ? normal[3] : json(503, "{\"code\":\"SERVICE_UNAVAILABLE\"}") : null, replies)) {
			startApplication(server);
			chooseConnectedMode();
			submitLoginAs(FIRST_USER);
			JFrame home = awaitWindow(JFrame.class, candidate -> "Gestion +".equals(candidate.getTitle())
					&& named(candidate, JTextArea.class, "État de la session").getText().contains("inaccessible"));
			onEdt(() -> {
				assertFalse(component(home, HomePanel.class, candidate -> true).isShowing());
				recovered.set(true);
				named(home, JButton.class, "Réessayer la connexion au serveur").doClick(0);
			});
			awaitWindow(JFrame.class, candidate -> candidate == home
					&& component(candidate, HomePanel.class, panel -> true).isShowing());
			onEdt(() -> home.dispatchEvent(new WindowEvent(home, WindowEvent.WINDOW_CLOSING)));
			application.get(5, TimeUnit.SECONDS);
			assertEquals(1, server.requests().stream().filter(request -> request.requestLine().startsWith("POST /login "))
					.count());
			assertTrue(server.requests().stream().filter(ConnectedSessionLifecycleTest::isSessionCheck).count() >= 2);
		}
	}

	@Test
	void aLoginRefusalKeepsTheFormResponsiveWithoutOpeningAConnectedHome() throws Exception {
		try (var server = new LoopbackHttpFixture(json(200, csrfBody("fictitious_login_csrf")),
				json(401, "{\"code\":\"AUTHENTICATION_REQUIRED\"}"))) {
			startApplication(server);
			chooseConnectedMode();
			LoginWindow login = submitLoginAs(FIRST_USER);
			awaitWindow(LoginWindow.class, candidate -> candidate == login
					&& named(candidate, JTextArea.class, "État de la connexion").getText().contains("incorrect"));
			onEdt(() -> {
				assertEquals(0, login.getPassword().length);
				assertTrue(component(login, JButton.class, button -> "Se connecter".equals(button.getText())).isEnabled());
				assertFalse(Arrays.stream(Window.getWindows()).anyMatch(candidate -> candidate instanceof JFrame
						&& candidate.isShowing() && "Gestion +".equals(((JFrame) candidate).getTitle())));
				login.dispatchEvent(new WindowEvent(login, WindowEvent.WINDOW_CLOSING));
			});
			application.get(5, TimeUnit.SECONDS);
			assertEquals(2, server.requests().size());
		}
	}

	@Test
	void aRealConnectionFailureKeepsTheLoginFormUsableAndAllowsReturningToTheSelector() throws Exception {
		try (var server = new LoopbackHttpFixture()) {
			String unavailableUrl = server.baseUrl();
			server.close();
			startApplication(unavailableUrl);
			chooseConnectedMode();
			LoginWindow login = submitLoginAs(FIRST_USER);
			awaitWindow(LoginWindow.class, candidate -> candidate == login
					&& named(candidate, JTextArea.class, "État de la connexion").getText().contains("Impossible de contacter"));
			onEdt(() -> component(login, JButton.class, button -> "Retour au choix du mode".equals(button.getText()))
					.doClick(0));
			LaunchSelectorWindow selector = awaitWindow(LaunchSelectorWindow.class, candidate -> true);
			onEdt(() -> selector.dispatchEvent(new WindowEvent(selector, WindowEvent.WINDOW_CLOSING)));
			application.get(5, TimeUnit.SECONDS);
			assertNoCreatedWindowIsShowing();
		}
	}

	private void chooseConnectedMode() throws Exception {
		LaunchSelectorWindow selector = awaitWindow(LaunchSelectorWindow.class, candidate -> true);
		onEdt(() -> named(selector, JButton.class, "Connexion").doClick(0));
	}

	private JFrame loginAs(String userName) throws Exception {
		LoginWindow login = submitLoginAs(userName);
		JFrame home = awaitWindow(JFrame.class, candidate -> "Gestion +".equals(candidate.getTitle())
				&& component(candidate, HomePanel.class, panel -> true).isShowing());
		onEdt(() -> {
			assertFalse(login.isDisplayable());
			assertEquals(userName, named(home, JLabel.class, "Utilisateur connecté : " + userName).getText());
			assertTrue(component(home, HomePanel.class, candidate -> true).isShowing());
		});
		return home;
	}

	private LoginWindow submitLoginAs(String userName) throws Exception {
		LoginWindow login = awaitWindow(LoginWindow.class, candidate -> true);
		onEdt(() -> {
			named(login, JTextField.class, "Identifiant").setText(userName);
			named(login, JPasswordField.class, "Mot de passe").setText("FictitiousPassword123!");
			named(login, JTextField.class, "Code d’authentification").setText("001234");
			component(login, JButton.class, button -> "Se connecter".equals(button.getText())).doClick(0);
		});
		return login;
	}

	private void assertCleanLogin(LoginWindow login) throws Exception {
		onEdt(() -> {
			assertEquals("", login.getUsername());
			assertEquals(0, login.getPassword().length);
			assertEquals("", login.getTotpCode());
			assertTrue(component(login, JButton.class, button -> "Se connecter".equals(button.getText())).isEnabled());
			assertFalse(Arrays.stream(Window.getWindows())
					.anyMatch(candidate -> candidate instanceof LaunchSelectorWindow && candidate.isShowing()));
		});
	}

	private void assertNoCreatedWindowIsShowing() throws Exception {
		onEdt(() -> assertFalse(Arrays.stream(Window.getWindows())
				.anyMatch(window -> !previousWindows.contains(window) && window.isShowing())));
	}

	private void closeCreatedWindows(boolean requestClosure) {

		for (Window window : Window.getWindows()) {

			if (previousWindows.contains(window) || !window.isDisplayable()) {
				continue;
			}

			if (requestClosure && window instanceof JFrame frame
					&& frame.getDefaultCloseOperation() == JFrame.DO_NOTHING_ON_CLOSE) {
				window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING));
			} else {
				window.dispose();
			}

		}

	}

	private static Reply[] repliesFor(int logoutStatus, String... userNames) {
		List<Reply> replies = new ArrayList<>();

		for (String userName : userNames) {
			replies.add(new Reply(200, csrfBody("fictitious_login_csrf_" + userName), Map.of("Set-Cookie",
					"JSESSIONID=fictitious_anonymous_" + userName + "; Path=/; HttpOnly; SameSite=Lax")));
			replies.add(new Reply(200, "", Map.of("Set-Cookie",
					"JSESSIONID=fictitious_authenticated_" + userName + "; Path=/; HttpOnly; SameSite=Lax")));
			replies.add(json(200, "{\"userName\":\"" + userName + "\"}"));
			replies.add(sessionReply(Instant.now(), 1_800));
			replies.add(json(200, csrfBody("fictitious_logout_csrf_" + userName)));
			replies.add(json(logoutStatus, logoutStatus == 204 ? ""
					: "{\"code\":\"" + (logoutStatus == 403 ? "FORBIDDEN" : "SERVICE_UNAVAILABLE") + "\"}"));
		}

		return replies.toArray(Reply[]::new);
	}

	private static String csrfBody(String token) {
		return "{\"headerName\":\"X-CSRF-TOKEN\",\"parameterName\":\"_csrf\",\"token\":\"" + token + "\"}";
	}

	private static Reply sessionReply(Instant serverTime, long lifetimeSeconds) {
		String body = "{\"expiresAt\":\"" + serverTime.plusSeconds(lifetimeSeconds) + "\",\"warnAt\":\""
				+ serverTime.plusSeconds(Math.max(0, lifetimeSeconds - 120)) + "\"}";
		String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(serverTime.atZone(ZoneOffset.UTC));
		return new Reply(200, body, Map.of("Date", date));
	}

	private static LoopbackHttpFixture connectedServer(int logoutStatus, String... userNames) throws Exception {
		Reply[] normal = repliesFor(logoutStatus, userNames);
		List<Reply> replies = new ArrayList<>();
		for (int index = 0; index < normal.length; index++) {
			if (index % 6 != 3) {
				replies.add(normal[index]);
			}
		}
		Reply observation = sessionReply(Instant.now(), 1_800);
		return new LoopbackHttpFixture(request -> isSessionCheck(request) ? observation : null,
				replies.toArray(Reply[]::new));
	}

	private static boolean isSessionCheck(CapturedRequest request) {
		return "GET /api/auth/session HTTP/1.1".equals(request.requestLine());
	}

	private static List<CapturedRequest> businessRequests(LoopbackHttpFixture server) throws Exception {
		return server.requests().stream().filter(request -> !isSessionCheck(request)).toList();
	}
}
