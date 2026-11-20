package gestion.net;

import static gestion.net.LoopbackHttpFixture.json;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.CookieManager;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import gestion.net.LoopbackHttpFixture.CapturedRequest;
import gestion.net.LoopbackHttpFixture.Reply;

class ServerSessionTest {

	private static final String TOKEN = "{\"headerName\":\"X-CSRF-TOKEN\",\"token\":\"fictitious_csrf_token\"}";
	private static final String EXPIRATION = "{\"expiresAt\":\"2026-11-20T16:30:00.123456789Z\","
			+ "\"warnAt\":\"2026-11-20T16:28:00.123456789Z\"}";

	@Test
	void readsTheServerDeadlinesAsInstantsWithoutLosingPrecision() throws Exception {
		try (var server = new LoopbackHttpFixture(json(200, EXPIRATION));
				var connection = connectionFor(server.baseUrl())) {
			SessionExpiration expiration = connection.fetchSessionExpiration();
			assertEquals(Instant.parse("2026-11-20T16:30:00.123456789Z"), expiration.expiresAt());
			assertEquals(Instant.parse("2026-11-20T16:28:00.123456789Z"), expiration.warnAt());
			assertEquals("GET /api/auth/session HTTP/1.1", server.requests().getFirst().requestLine());
		}
	}

	@Test
	void acceptsAdditionalSessionFieldsWithoutChangingTheDeadlines() throws Exception {
		String body = EXPIRATION.substring(0, EXPIRATION.length() - 1) + ",\"additionalField\":\"fictitious_value\"}";
		try (var server = new LoopbackHttpFixture(json(200, body));
				var connection = connectionFor(server.baseUrl())) {
			assertEquals(Instant.parse("2026-11-20T16:30:00.123456789Z"),
					connection.fetchSessionExpiration().expiresAt());
		}
	}

	@Test
	void keepsAnElapsedDeadlineForTheSessionOwnerToHandle() throws Exception {
		String body = "{\"expiresAt\":\"2020-01-01T10:00:00Z\",\"warnAt\":\"2020-01-01T09:58:00Z\"}";
		try (var server = new LoopbackHttpFixture(json(200, body));
				var connection = connectionFor(server.baseUrl())) {
			assertEquals(Instant.parse("2020-01-01T10:00:00Z"), connection.fetchSessionExpiration().expiresAt());
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"null",
			"{}",
			"not-json",
			"{\"expiresAt\":null,\"warnAt\":null}",
			"{\"expiresAt\":\"not-an-instant\",\"warnAt\":\"2026-11-20T16:28:00Z\"}",
			"{\"expiresAt\":\"2026-11-20T16:30:00Z\"}",
			"{\"expiresAt\":\"2026-11-20T16:30:00Z\",\"warnAt\":\"2026-11-20T16:31:00Z\"}" })
	void rejectsMalformedOrIncoherentSessionDeadlines(String body) throws Exception {
		try (var server = new LoopbackHttpFixture(json(200, body));
				var connection = connectionFor(server.baseUrl())) {
			assertThrows(IOException.class, connection::fetchSessionExpiration);
		}
	}

	@ParameterizedTest
	@CsvSource({ "401,AUTHENTICATION_REQUIRED", "403,FORBIDDEN", "503,SERVICE_UNAVAILABLE" })
	void exposesTheHttpStatusAndPublicErrorCodeForSessionFailures(int status, String code) throws Exception {
		String body = "{\"code\":\"" + code + "\",\"detail\":\"fictitious_private_diagnostic\"}";
		try (var server = new LoopbackHttpFixture(json(status, body));
				var connection = connectionFor(server.baseUrl())) {
			HttpApiException failure = assertThrows(HttpApiException.class, connection::fetchSessionExpiration);
			assertEquals(status, failure.status());
			assertEquals(code, failure.code().orElseThrow());
			assertEquals("Erreur HTTP : " + status, failure.getMessage());
			assertFalse(failure.getMessage().contains("fictitious_private_diagnostic"));
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "null", "not-json", "{}" })
	void preservesTheStatusWhenTheErrorBodyCannotBeDecoded(String body) throws Exception {
		try (var server = new LoopbackHttpFixture(json(503, body));
				var connection = connectionFor(server.baseUrl())) {
			HttpApiException failure = assertThrows(HttpApiException.class, connection::fetchSessionExpiration);
			assertEquals(503, failure.status());
			assertTrue(failure.code().isEmpty());
		}
	}

	@Test
	void preservesAnUnknownPublicErrorCodeWithoutDisplayingTheResponseBody() throws Exception {
		try (var server = new LoopbackHttpFixture(json(403, "{\"code\":\"FUTURE_PUBLIC_CODE\"}"));
				var connection = connectionFor(server.baseUrl())) {
			HttpApiException failure = assertThrows(HttpApiException.class, connection::fetchSessionExpiration);
			assertEquals("FUTURE_PUBLIC_CODE", failure.code().orElseThrow());
			assertEquals("Erreur HTTP : 403", failure.getMessage());
		}
	}

	@Test
	void refreshesTheCsrfTokenAfterLoginBeforeLoggingOutWithTheAuthenticatedCookie() throws Exception {
		Reply anonymous = new Reply(200, TOKEN,
				Map.of("Set-Cookie", "JSESSIONID=fictitious_anonymous; Path=/; HttpOnly; SameSite=Lax"));
		Reply authenticated = new Reply(200, "",
				Map.of("Set-Cookie", "JSESSIONID=fictitious_authenticated; Path=/; HttpOnly; SameSite=Lax"));
		String freshToken = "{\"headerName\":\"X-CSRF-TOKEN\",\"token\":\"fictitious_fresh_token\"}";
		try (var server = new LoopbackHttpFixture(anonymous, authenticated,
				json(200, "{\"userName\":\"fictitious_user\"}"), json(200, freshToken), json(204, ""));
				var connection = connectionFor(server.baseUrl())) {
			assertEquals(LoginStatus.AUTHENTICATED,
					connection.login("fictitious_user", "FictitiousPassword123!".toCharArray(), "").status());
			connection.logout();
			List<CapturedRequest> requests = server.requests();
			assertEquals(List.of("GET /api/auth/csrf HTTP/1.1", "POST /login HTTP/1.1",
					"GET /api/auth/me HTTP/1.1", "GET /api/auth/csrf HTTP/1.1", "POST /logout HTTP/1.1"),
					requests.stream().map(CapturedRequest::requestLine).toList());
			assertEquals("fictitious_fresh_token", requests.get(4).headers().get("x-csrf-token"));
			assertTrue(requests.get(4).headers().get("cookie").contains("JSESSIONID=fictitious_authenticated"));
			assertEquals("", requests.get(4).body());
		}
	}

	@Test
	void obtainsAnAnonymousCsrfContextAfterTheFormerSessionIsRefused() throws Exception {
		Reply formerSession = new Reply(200, TOKEN,
				Map.of("Set-Cookie", "JSESSIONID=fictitious_former_session; Path=/; HttpOnly; SameSite=Lax"));
		Reply anonymous = new Reply(200, TOKEN,
				Map.of("Set-Cookie", "JSESSIONID=fictitious_new_anonymous; Path=/; HttpOnly; SameSite=Lax"));
		try (var server = new LoopbackHttpFixture(formerSession,
				json(401, "{\"code\":\"AUTHENTICATION_REQUIRED\"}"), anonymous, json(204, ""));
				var connection = connectionFor(server.baseUrl())) {
			connection.fetchCsrfToken();
			connection.logout();
			List<CapturedRequest> requests = server.requests();
			assertEquals(4, requests.size());
			assertEquals("POST /logout HTTP/1.1", requests.get(3).requestLine());
			assertTrue(requests.get(3).headers().get("cookie").contains("JSESSIONID=fictitious_new_anonymous"));
			assertFalse(requests.get(3).headers().get("cookie").contains("JSESSIONID=fictitious_former_session"));
		}
	}

	@Test
	void doesNotRetryARefusedCsrfContextIndefinitely() throws Exception {
		try (var server = new LoopbackHttpFixture(json(401, "{}"), json(401, "{}"));
				var connection = connectionFor(server.baseUrl())) {
			assertEquals(401, assertThrows(HttpApiException.class, connection::logout).status());
			assertEquals(List.of("GET /api/auth/csrf HTTP/1.1", "GET /api/auth/csrf HTTP/1.1"),
					server.requests().stream().map(CapturedRequest::requestLine).toList());
		}
	}

	@Test
	void doesNotSendLogoutWhenTheCsrfServiceIsUnavailable() throws Exception {
		try (var server = new LoopbackHttpFixture(json(503, "{\"code\":\"SERVICE_UNAVAILABLE\"}"));
				var connection = connectionFor(server.baseUrl())) {
			assertEquals(503, assertThrows(HttpApiException.class, connection::logout).status());
			assertEquals(1, server.requests().size());
		}
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"{\"headerName\":\"Invalid Header\",\"token\":\"fictitious_token\"}",
			"{\"headerName\":\"Invalid:Header\",\"token\":\"fictitious_token\"}",
			"{\"headerName\":\"X-CSRF-TOKEN\",\"token\":\"fictitious\\r\\nvalue\"}" })
	void rejectsAnInvalidCsrfHeaderBeforeSendingLogout(String body) throws Exception {
		try (var server = new LoopbackHttpFixture(json(200, body));
				var connection = connectionFor(server.baseUrl())) {
			assertEquals("En-tête CSRF invalide.", assertThrows(IOException.class, connection::logout).getMessage());
			assertEquals(1, server.requests().size());
		}
	}

	@ParameterizedTest
	@ValueSource(ints = { 200, 401, 403, 500, 503 })
	void reportsAnUnconfirmedLogoutInsteadOfClaimingSuccess(int status) throws Exception {
		try (var server = new LoopbackHttpFixture(json(200, TOKEN), json(status, "{}"));
				var connection = connectionFor(server.baseUrl())) {
			assertEquals(status, assertThrows(HttpApiException.class, connection::logout).status());
			assertEquals(2, server.requests().size());
		}
	}

	@Test
	void keepsTheContextPathAndRemovesRedundantTrailingSlashes() throws Exception {
		try (var server = new LoopbackHttpFixture(json(200, EXPIRATION), json(200, TOKEN), json(204, ""));
				var connection = connectionFor(server.baseUrl() + "/fictitious-context///")) {
			connection.fetchSessionExpiration();
			connection.logout();
			assertEquals(List.of("GET /fictitious-context/api/auth/session HTTP/1.1",
					"GET /fictitious-context/api/auth/csrf HTTP/1.1", "POST /fictitious-context/logout HTTP/1.1"),
					server.requests().stream().map(CapturedRequest::requestLine).toList());
		}
	}

	@Test
	void doesNotFollowARedirectToAnotherServer() throws Exception {
		Reply redirect = new Reply(302, "", Map.of("Location", "https://fictitious-other-server.invalid/session"));
		try (var server = new LoopbackHttpFixture(redirect);
				var connection = connectionFor(server.baseUrl())) {
			assertEquals(302, assertThrows(HttpApiException.class, connection::fetchSessionExpiration).status());
			assertEquals(1, server.requests().size());
		}
	}

	@Test
	void closingIsIdempotentAndRejectsFurtherRequestsLocally() throws Exception {
		try (var server = new LoopbackHttpFixture(json(200, TOKEN));
				var connection = connectionFor(server.baseUrl())) {
			connection.fetchCsrfToken();
			connection.close();
			connection.close();
			assertEquals("La connexion au serveur est fermée.",
					assertThrows(IOException.class, connection::fetchCsrfToken).getMessage());
			assertEquals("La connexion au serveur est fermée.",
					assertThrows(IOException.class, connection::fetchCurrentUser).getMessage());
			assertEquals("La connexion au serveur est fermée.",
					assertThrows(IOException.class, connection::fetchSessionExpiration).getMessage());
			assertEquals("La connexion au serveur est fermée.",
					assertThrows(IOException.class, connection::logout).getMessage());
			assertEquals(LoginStatus.FAILED,
					connection.login("fictitious_user", "FictitiousPassword123!".toCharArray(), "").status());
			assertEquals(1, server.requests().size());
		}
	}

	@Test
	void closingClearsCookiesAndRejectsCookiesFromLateResponses() throws Exception {
		Reply csrf = new Reply(200, TOKEN,
				Map.of("Set-Cookie", "JSESSIONID=fictitious_session; Path=/; HttpOnly; SameSite=Lax"));
		try (var server = new LoopbackHttpFixture(csrf);
				var connection = connectionFor(server.baseUrl())) {
			connection.fetchCsrfToken();
			CookieManager cookies = cookies(connection);
			assertFalse(cookies.getCookieStore().getCookies().isEmpty());
			connection.close();
			assertTrue(cookies.getCookieStore().getCookies().isEmpty());
			cookies.put(URI.create(server.baseUrl()), Map.of("Set-Cookie", List.of("JSESSIONID=fictitious_late; Path=/")));
			assertTrue(cookies.getCookieStore().getCookies().isEmpty());
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "http://fictitious-server.invalid", "ftp://fictitious-server.invalid",
			"https://fictitious-server.invalid?unexpected=true", "https://fictitious-server.invalid#fragment",
			"https://fictitious_user:fictitious_password@fictitious-server.invalid", "/relative-server" })
	void rejectsUnsafeOrAmbiguousServerAddresses(String address) {
		assertThrows(IllegalArgumentException.class, () -> connectionFor(address));
	}

	@Test
	void acceptsAnHttpsAddressWithoutConnectingToTheNetwork() {
		assertDoesNotThrow(() -> {
			try (var connection = connectionFor("https://fictitious-server.invalid/context")) {
				connection.close();
			}
		});
	}

	private ServerConnection connectionFor(String address) {
		return new ServerConnection(URI.create(address));
	}

	private CookieManager cookies(ServerConnection connection) throws ReflectiveOperationException {
		Field field = ServerConnection.class.getDeclaredField("cookies");
		field.setAccessible(true);
		return (CookieManager) field.get(connection);
	}
}
