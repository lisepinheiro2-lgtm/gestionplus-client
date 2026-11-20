package gestion.net;

import static gestion.net.LoopbackHttpFixture.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import gestion.net.LoopbackHttpFixture.CapturedRequest;
import gestion.net.LoopbackHttpFixture.Reply;
import gestion.net.ServerConnection.LoginResult;

@ResourceLock("gestionplus.server.url")
class ServerLoginTest {

	private static final String VALID_TOKEN = "{\"parameterName\":\"_csrf\",\"headerName\":\"X-CSRF-TOKEN\","
			+ "\"token\":\"fictitious_csrf_token\"}";
	private static final String CURRENT_USER = "{\"userName\":\"fictitious_user\"}";
	private static final String FICTITIOUS_PASSWORD = "FictitiousPassword123!";
	private String originalUrl;

	@BeforeEach
	void rememberConfiguration() {
		originalUrl = System.getProperty("gestionplus.server.url");
	}

	@AfterEach
	void restoreConfiguration() {
		if (originalUrl == null) {
			System.clearProperty("gestionplus.server.url");
		} else {
			System.setProperty("gestionplus.server.url", originalUrl);
		}
	}

	@Test
	void verifiesTheCurrentUserWithTheRotatedCookieBeforeReportingSuccess() throws Exception {
		Reply csrf = new Reply(200, VALID_TOKEN,
				Map.of("Set-Cookie", "JSESSIONID=fictitious_anonymous; Path=/; HttpOnly; SameSite=Lax"));
		Reply login = new Reply(200, "",
				Map.of("Set-Cookie", "JSESSIONID=fictitious_authenticated; Path=/; HttpOnly; SameSite=Lax"));
		try (var server = new LoopbackHttpFixture(csrf, login, json(200, CURRENT_USER))) {
			LoginResult result = login(connectionFor(server.baseUrl()), "fictitious_user", FICTITIOUS_PASSWORD,
					"001234");
			assertEquals(LoginStatus.AUTHENTICATED, result.status());
			assertEquals("fictitious_user", result.userName());
			List<CapturedRequest> requests = server.requests();
			assertEquals(List.of("GET /api/auth/csrf HTTP/1.1", "POST /login HTTP/1.1",
					"GET /api/auth/me HTTP/1.1"), requests.stream().map(CapturedRequest::requestLine).toList());
			assertEquals("application/x-www-form-urlencoded", requests.get(1).headers().get("content-type"));
			assertEquals("fictitious_csrf_token", requests.get(1).headers().get("x-csrf-token"));
			assertTrue(requests.get(1).headers().get("cookie").contains("JSESSIONID=fictitious_anonymous"));
			String authenticatedCookie = requests.get(2).headers().get("cookie");
			assertTrue(authenticatedCookie.contains("JSESSIONID=fictitious_authenticated"));
			assertFalse(authenticatedCookie.contains("JSESSIONID=fictitious_anonymous"));
		}
	}

	@Test
	void encodesCredentialsWithoutChangingSpacesUnicodeOrLeadingTotpZeroes() throws Exception {
		String userName = "élise+& =%";
		String password = " Fictitious+&=é% ";
		try (var server = new LoopbackHttpFixture(json(200, VALID_TOKEN), json(200, ""),
				json(200, CURRENT_USER))) {
			assertEquals(LoginStatus.AUTHENTICATED,
					login(connectionFor(server.baseUrl()), userName, password, "001234").status());
			assertEquals(Map.of("username", userName, "password", password, "totp", "001234"),
					formParameters(server.requests().get(1)));
		}
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { " ", "\t" })
	void omitsAnAbsentOptionalTotpCode(String totpCode) throws Exception {
		try (var server = new LoopbackHttpFixture(json(200, VALID_TOKEN), json(200, ""),
				json(200, CURRENT_USER))) {
			assertEquals(LoginStatus.AUTHENTICATED,
					login(connectionFor(server.baseUrl()), "fictitious_user", FICTITIOUS_PASSWORD, totpCode).status());
			assertEquals(Map.of("username", "fictitious_user", "password", FICTITIOUS_PASSWORD),
					formParameters(server.requests().get(1)));
		}
	}

	@ParameterizedTest
	@CsvSource({ "401,REJECTED", "403,CSRF_REJECTED", "503,UNAVAILABLE", "400,FAILED", "500,FAILED",
			"302,FAILED" })
	void mapsLoginRefusalsWithoutReadingTheCurrentUser(int status, LoginStatus expected) throws Exception {
		try (var server = new LoopbackHttpFixture(json(200, VALID_TOKEN), json(status, "{}"))) {
			LoginResult result = login(connectionFor(server.baseUrl()), "fictitious_user", FICTITIOUS_PASSWORD, "");
			assertEquals(expected, result.status());
			assertNull(result.userName());
			assertEquals(2, server.requests().size());
		}
	}

	@Test
	void rejectsAnUnauthenticatedSessionDespiteASuccessfulLoginResponse() throws Exception {
		try (var server = new LoopbackHttpFixture(json(200, VALID_TOKEN), json(200, ""),
				json(401, "{\"code\":\"AUTHENTICATION_REQUIRED\"}"))) {
			LoginResult result = login(connectionFor(server.baseUrl()), "fictitious_user", FICTITIOUS_PASSWORD, "");
			assertEquals(LoginStatus.REJECTED, result.status());
			assertNull(result.userName());
			assertEquals("GET /api/auth/me HTTP/1.1", server.requests().get(2).requestLine());
		}
	}

	@ParameterizedTest
	@ValueSource(ints = { 400, 403, 500, 503 })
	void neverAuthenticatesWhenTheCurrentUserLookupFails(int status) throws Exception {
		try (var server = new LoopbackHttpFixture(json(200, VALID_TOKEN), json(200, ""),
				json(status, "{}"))) {
			LoginResult result = login(connectionFor(server.baseUrl()), "fictitious_user", FICTITIOUS_PASSWORD, "");
			assertEquals(LoginStatus.FAILED, result.status());
			assertNull(result.userName());
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "null", "{}", "{\"userName\":null}", "{\"userName\":\"   \"}", "not-json",
			"\"fictitious_user\"" })
	void neverAuthenticatesWithAnInvalidCurrentUserResponse(String body) throws Exception {
		try (var server = new LoopbackHttpFixture(json(200, VALID_TOKEN), json(200, ""),
				json(200, body))) {
			LoginResult result = login(connectionFor(server.baseUrl()), "fictitious_user", FICTITIOUS_PASSWORD, "");
			assertEquals(LoginStatus.FAILED, result.status());
			assertNull(result.userName());
		}
	}

	@ParameterizedTest
	@ValueSource(ints = { 401, 403, 503 })
	void doesNotSendCredentialsWhenTheCsrfRequestIsRefused(int status) throws Exception {
		try (var server = new LoopbackHttpFixture(json(status, "{}"))) {
			LoginResult result = login(connectionFor(server.baseUrl()), "fictitious_user", FICTITIOUS_PASSWORD, "");
			assertEquals(LoginStatus.FAILED, result.status());
			assertEquals(List.of("GET /api/auth/csrf HTTP/1.1"),
					server.requests().stream().map(CapturedRequest::requestLine).toList());
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "null", "{}", "not-json" })
	void doesNotSendCredentialsWhenTheCsrfResponseIsInvalid(String body) throws Exception {
		try (var server = new LoopbackHttpFixture(json(200, body))) {
			LoginResult result = login(connectionFor(server.baseUrl()), "fictitious_user", FICTITIOUS_PASSWORD, "");
			assertEquals(LoginStatus.FAILED, result.status());
			assertEquals(1, server.requests().size());
		}
	}

	@Test
	void keepsTheConfiguredContextPathThroughoutAuthentication() throws Exception {
		try (var server = new LoopbackHttpFixture(json(200, VALID_TOKEN), json(200, ""),
				json(200, CURRENT_USER))) {
			assertEquals(LoginStatus.AUTHENTICATED,
					login(connectionFor(server.baseUrl() + "/fictitious-context"), "fictitious_user",
							FICTITIOUS_PASSWORD, "").status());
			assertEquals(List.of("GET /fictitious-context/api/auth/csrf HTTP/1.1",
					"POST /fictitious-context/login HTTP/1.1", "GET /fictitious-context/api/auth/me HTTP/1.1"),
					server.requests().stream().map(CapturedRequest::requestLine).toList());
		}
	}

	@Test
	void returnsEmptyForAnUnauthenticatedCurrentUserRequest() throws Exception {
		try (var server = new LoopbackHttpFixture(json(401, "{}"))) {
			assertTrue(connectionFor(server.baseUrl()).fetchCurrentUser().isEmpty());
			assertEquals("GET /api/auth/me HTTP/1.1", server.requests().getFirst().requestLine());
		}
	}

	@Test
	void readsTheCurrentUserWithoutRejectingAdditionalResponseFields() throws Exception {
		try (var server = new LoopbackHttpFixture(
				json(200, "{\"userName\":\"fictitious_user\",\"additionalField\":\"fictitious_value\"}"))) {
			assertEquals("fictitious_user", connectionFor(server.baseUrl()).fetchCurrentUser().orElseThrow());
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "null", "{}", "{\"userName\":null}", "{\"userName\":\"\"}",
			"{\"userName\":\"   \"}", "not-json" })
	void rejectsAnInvalidCurrentUserResponseAsAnIoFailure(String body) throws Exception {
		try (var server = new LoopbackHttpFixture(json(200, body))) {
			assertThrows(IOException.class, () -> connectionFor(server.baseUrl()).fetchCurrentUser());
		}
	}

	@ParameterizedTest
	@ValueSource(ints = { 403, 404, 503 })
	void reportsUnexpectedCurrentUserStatusesAsIoFailures(int status) throws Exception {
		try (var server = new LoopbackHttpFixture(json(status, "{}"))) {
			assertThrows(IOException.class, () -> connectionFor(server.baseUrl()).fetchCurrentUser());
		}
	}

	@Test
	void doesNotShareAuthenticatedCookiesWithAnotherConnection() throws Exception {
		Reply login = new Reply(200, "",
				Map.of("Set-Cookie", "JSESSIONID=fictitious_authenticated; Path=/; HttpOnly; SameSite=Lax"));
		try (var server = new LoopbackHttpFixture(json(200, VALID_TOKEN), login,
				json(200, CURRENT_USER), json(401, "{}"))) {
			ServerConnection authenticated = connectionFor(server.baseUrl());
			assertEquals(LoginStatus.AUTHENTICATED,
					login(authenticated, "fictitious_user", FICTITIOUS_PASSWORD, "").status());
			assertTrue(connectionFor(server.baseUrl()).fetchCurrentUser().isEmpty());
			assertFalse(server.requests().get(3).headers().containsKey("cookie"));
		}
	}

	private ServerConnection connectionFor(String baseUrl) {
		System.setProperty("gestionplus.server.url", baseUrl);
		return new ServerConnection();
	}

	private LoginResult login(ServerConnection connection, String userName, String password, String totpCode) {
		char[] suppliedPassword = password.toCharArray();
		try {
			return connection.login(userName, suppliedPassword, totpCode);
		} finally {
			Arrays.fill(suppliedPassword, '\0');
		}
	}

	private Map<String, String> formParameters(CapturedRequest request) {
		Map<String, String> parameters = new LinkedHashMap<>();
		for (String parameter : request.body().split("&")) {
			String[] pair = parameter.split("=", 2);
			String name = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
			String value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
			assertNull(parameters.put(name, value));
		}
		return parameters;
	}
}
