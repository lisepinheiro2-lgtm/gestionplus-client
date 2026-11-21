package gestion.net;

import static gestion.net.LoopbackHttpFixture.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.ObjectMapper;

class ActivationConnectionTest {

	private static final ActivationInvitation INVITATION = new ActivationInvitation(
			UUID.fromString("00000000-0000-0000-0000-000000000001"), "a".repeat(43));
	private static final String CSRF = "{\"headerName\":\"X-CSRF-TOKEN\",\"token\":\"fictitious-csrf\"}";

	@Test
	void sendsAnExplicitSmsRequestWithTheFreshCsrfTokenAndItsAnonymousSessionCookie() throws Exception {
		try (var server = new LoopbackHttpFixture(new LoopbackHttpFixture.Reply(200, CSRF,
				Map.of("Set-Cookie", "JSESSIONID=fictitious_activation_session; Path=/")), json(200, ""));
				var connection = new ServerConnection(URI.create(server.baseUrl() + "/fictitious-context"))) {
			connection.requestActivationChallenge(INVITATION);
			var request = server.requests().getLast();
			assertEquals("POST /fictitious-context/api/invitations/" + INVITATION.invitationID()
					+ "/sms-challenge HTTP/1.1", request.requestLine());
			assertEquals("fictitious-csrf", request.headers().get("x-csrf-token"));
			assertTrue(request.headers().get("cookie").contains("fictitious_activation_session"));
			assertEquals(INVITATION.token(), new ObjectMapper().readTree(request.body()).get("token").asText());
			assertFalse(request.requestLine().contains(INVITATION.token()));
		}
	}

	@Test
	void completesActivationWithJsonWithoutAutomaticallyLoggingIn() throws Exception {
		try (var server = new LoopbackHttpFixture(json(200, CSRF), json(200, ""));
				var connection = new ServerConnection(URI.create(server.baseUrl()))) {
			connection.completeActivation(INVITATION, "fictitious_user", "Fictitious-Password-2026!".toCharArray(), "000042");
			assertEquals(2, server.requests().size());
			var request = server.requests().getLast();
			var body = new ObjectMapper().readTree(request.body());
			assertEquals("000042", body.get("proof").asText());
			assertEquals("fictitious_user", body.get("userName").asText());
			assertEquals("Fictitious-Password-2026!", body.get("password").asText());
			assertTrue(request.requestLine().contains("/activation "));
		}
	}

	@ParameterizedTest
	@ValueSource(ints = { 400, 403, 409, 429, 503 })
	void preservesPublicFailuresAndNeverAutomaticallyRepeatsAnSmsRequest(int status) throws Exception {
		try (var server = new LoopbackHttpFixture(json(200, CSRF), new LoopbackHttpFixture.Reply(status,
				"{\"code\":\"ACTIVATION_DENIED\"}", Map.of("Retry-After", "42")));
				var connection = new ServerConnection(URI.create(server.baseUrl()))) {
			HttpApiException failure = assertThrows(HttpApiException.class,
					() -> connection.requestActivationChallenge(INVITATION));
			assertEquals(status, failure.status());
			assertEquals(Duration.ofSeconds(42), failure.retryAfter().orElseThrow());
			assertEquals(2, server.requests().size());
		}
	}
}
