package gestion.net;

import static gestion.net.LoopbackHttpFixture.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import com.fasterxml.jackson.core.JsonParseException;

import gestion.net.LoopbackHttpFixture.Reply;

@ResourceLock("gestionplus.server.url")
class ServerConnectionTest {

	private static final String VALID_TOKEN = "{\"headerName\":\"X-CSRF-TOKEN\",\"token\":\"fictitious_csrf_token\"}";
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
	void retrievesTheTokenFromTheConfiguredServerUsingTheExpectedGetPath() throws Exception {
		try (var server = new LoopbackHttpFixture(json(200, VALID_TOKEN))) {
			ServerConnection connection = connectionFor(server.baseUrl());
			ServerConnection.CsrfToken token = connection.fetchCsrfToken();
			assertEquals("X-CSRF-TOKEN", token.headerName());
			assertEquals("fictitious_csrf_token", token.token());
			assertEquals("GET /api/auth/csrf HTTP/1.1", server.requests().getFirst().requestLine());
		}
	}

	@Test
	void acceptsTheParameterNamePresentInTheRealSpringSecurityResponse() throws Exception {
		String body = "{\"parameterName\":\"_csrf\",\"headerName\":\"X-CSRF-TOKEN\","
				+ "\"token\":\"fictitious_csrf_token\"}";
		try (var server = new LoopbackHttpFixture(json(200, body))) {
			ServerConnection.CsrfToken token = connectionFor(server.baseUrl()).fetchCsrfToken();
			assertEquals("X-CSRF-TOKEN", token.headerName());
			assertEquals("fictitious_csrf_token", token.token());
		}
	}

	@Test
	void keepsTheContextPathConfiguredInTheBaseUrl() throws Exception {
		try (var server = new LoopbackHttpFixture(json(200, VALID_TOKEN))) {
			connectionFor(server.baseUrl() + "/fictitious-context").fetchCsrfToken();
			assertEquals("GET /fictitious-context/api/auth/csrf HTTP/1.1",
					server.requests().getFirst().requestLine());
		}
	}

	@Test
	void retainsTheAnonymousSessionCookieForTheNextCsrfRequest() throws Exception {
		Reply first = new Reply(200, VALID_TOKEN,
				Map.of("Set-Cookie", "JSESSIONID=fictitious_session; Path=/; HttpOnly; SameSite=Lax"));
		try (var server = new LoopbackHttpFixture(first, json(200, VALID_TOKEN))) {
			ServerConnection connection = connectionFor(server.baseUrl());
			connection.fetchCsrfToken();
			connection.fetchCsrfToken();
			var requests = server.requests();
			assertFalse(requests.getFirst().headers().containsKey("cookie"));
			assertTrue(requests.get(1).headers().getOrDefault("cookie", "").contains("JSESSIONID=fictitious_session"));
		}
	}

	@Test
	void separateConnectionsDoNotShareTheirAnonymousSessionCookies() throws Exception {
		Reply first = new Reply(200, VALID_TOKEN,
				Map.of("Set-Cookie", "JSESSIONID=fictitious_session; Path=/; HttpOnly"));
		try (var server = new LoopbackHttpFixture(first, json(200, VALID_TOKEN))) {
			connectionFor(server.baseUrl()).fetchCsrfToken();
			connectionFor(server.baseUrl()).fetchCsrfToken();
			for (var request : server.requests()) {
				assertFalse(request.headers().containsKey("cookie"));
			}
		}
	}

	@Test
	void rejectsEveryNonSuccessStatusBeforeTryingToReadItsBody() throws Exception {
		for (int status : List.of(204, 302, 400, 401, 403, 500, 503)) {
			try (var server = new LoopbackHttpFixture(json(status, "Fictitious private error body"))) {
				ServerConnection connection = connectionFor(server.baseUrl());
				IOException failure = assertThrows(IOException.class, connection::fetchCsrfToken);
				assertEquals("Erreur HTTP : " + status, failure.getMessage());
				assertFalse(failure.getMessage().contains("private error body"));
			}
		}
	}

	@Test
	void rejectsNullMissingEmptyOrBlankTokens() throws Exception {
		for (String body : List.of("null", "{}", "{\"headerName\":\"X-CSRF-TOKEN\"}",
				"{\"headerName\":\"X-CSRF-TOKEN\",\"token\":null}",
				"{\"headerName\":\"X-CSRF-TOKEN\",\"token\":\"\"}",
				"{\"headerName\":\"X-CSRF-TOKEN\",\"token\":\"   \"}")) {
			try (var server = new LoopbackHttpFixture(json(200, body))) {
				ServerConnection connection = connectionFor(server.baseUrl());
				IOException failure = assertThrows(IOException.class, connection::fetchCsrfToken);
				assertEquals("Réponse CSRF invalide", failure.getMessage(), body);
			}
		}
	}

	@Test
	void rejectsMissingNullEmptyOrBlankHeaderNames() throws Exception {
		for (String body : List.of("{\"token\":\"fictitious_csrf_token\"}",
				"{\"headerName\":null,\"token\":\"fictitious_csrf_token\"}",
				"{\"headerName\":\"\",\"token\":\"fictitious_csrf_token\"}",
				"{\"headerName\":\"   \",\"token\":\"fictitious_csrf_token\"}")) {
			try (var server = new LoopbackHttpFixture(json(200, body))) {
				ServerConnection connection = connectionFor(server.baseUrl());
				IOException failure = assertThrows(IOException.class, connection::fetchCsrfToken);
				assertEquals("Nom de l'en-tête CSRF manquant ou vide.", failure.getMessage(), body);
			}
		}
	}

	@Test
	void rejectsMalformedJsonInsteadOfReturningAPartialToken() throws Exception {
		for (String body : List.of("not-json", "{\"headerName\":}", "{\"headerName\":\"X-CSRF-TOKEN\",\"token\":}")) {
			try (var server = new LoopbackHttpFixture(json(200, body))) {
				ServerConnection connection = connectionFor(server.baseUrl());
				assertThrows(JsonParseException.class, connection::fetchCsrfToken);
			}
		}
	}

	private ServerConnection connectionFor(String baseUrl) {
		System.setProperty("gestionplus.server.url", baseUrl);
		return new ServerConnection();
	}
}
