package gestion.net;

import static gestion.net.LoopbackHttpFixture.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.ObjectMapper;

class MfaEnrollmentConnectionTest {

	private static final String PROOF = "p".repeat(22);
	private static final UUID ENROLLMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000042");
	private static final String SETUP_KEY = "A".repeat(32);
	private static final String CSRF = "{\"headerName\":\"X-CSRF-TOKEN\",\"token\":\"fictitious-csrf\"}";
	private static final List<String> CODES = IntStream.range(0, 10).mapToObj(index -> "fictitious" + index + "x".repeat(11)).toList();

	@Test
	void activationReturnsTheProofWithoutOpeningAnotherRequestOrLoggingItsValue() throws Exception {
		try (var server = new LoopbackHttpFixture(json(200, CSRF), json(200, "{\"enrollmentProof\":\"" + PROOF + "\"}"));
				var connection = new ServerConnection(URI.create(server.baseUrl()))) {
			var result = connection.completeActivation(new ActivationInvitation(UUID.randomUUID(), "a".repeat(43)),
					"fictitious_account", "Fictitious-Password-2026!".toCharArray(), "000042");
			assertTrue(result.requiresMfaEnrollment());
			assertEquals(PROOF, result.enrollmentProof());
			assertFalse(result.toString().contains(PROOF));
			assertEquals(2, server.requests().size());
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "{}", "null", "{\"enrollmentProof\":\"private-invalid-proof\"}",
			"{\"enrollmentProof\":null} {}" })
	void rejectsAnAmbiguousActivationResponseInsteadOfClaimingMfaIsNotRequired(String body) throws Exception {
		try (var server = new LoopbackHttpFixture(json(200, CSRF), json(200, body));
				var connection = new ServerConnection(URI.create(server.baseUrl()))) {
			IOException failure = assertThrows(IOException.class, () -> connection.completeActivation(
					new ActivationInvitation(UUID.randomUUID(), "a".repeat(43)), "fictitious_account", "Fictitious!".toCharArray(), "000042"));
			assertFalse(failure.toString().contains("private-invalid-proof"));
			assertEquals(2, server.requests().size());
		}
	}

	@Test
	void keepsTheRotatedRestrictedCookieAndFetchesFreshCsrfForEveryMfaPost() throws Exception {
		String codes = new ObjectMapper().writeValueAsString(Map.of("codes", CODES));
		try (var server = new LoopbackHttpFixture(
				new LoopbackHttpFixture.Reply(200, CSRF, Map.of("Set-Cookie", "JSESSIONID=fictitious_anonymous; Path=/")),
				new LoopbackHttpFixture.Reply(204, "", Map.of("Set-Cookie", "JSESSIONID=fictitious_restricted; Path=/")),
				json(200, CSRF.replace("fictitious-csrf", "rotated-csrf")),
				new LoopbackHttpFixture.Reply(200, enrollmentJson("2035-01-01T00:05:00Z"), Map.of("Date", "Mon, 1 Jan 2035 00:00:00 GMT")),
				json(200, CSRF.replace("fictitious-csrf", "completion-csrf")), json(200, codes));
				var connection = new ServerConnection(URI.create(server.baseUrl() + "/fictitious-context"))) {
			connection.openMfaEnrollment(PROOF);
			var observation = connection.beginMfaEnrollment();
			assertEquals(SETUP_KEY, observation.enrollment().setupKey());
			assertEquals(Instant.parse("2035-01-01T00:05:00Z"), observation.enrollment().expiresAt());
			assertTrue(observation.remaining().compareTo(Duration.ofSeconds(295)) > 0);
			assertTrue(observation.remaining().compareTo(Duration.ofSeconds(300)) < 0);
			assertEquals(CODES, connection.completeMfaEnrollment(ENROLLMENT_ID, "000042").codes());
			var requests = server.requests();
			assertEquals(6, requests.size());
			assertEquals("POST /fictitious-context/api/auth/mfa/enrollment/session HTTP/1.1", requests.get(1).requestLine());
			assertTrue(requests.get(1).body().contains(PROOF));
			assertEquals("rotated-csrf", requests.get(3).headers().get("x-csrf-token"));
			assertTrue(requests.get(3).headers().get("cookie").contains("fictitious_restricted"));
			assertFalse(requests.get(3).headers().get("cookie").contains("fictitious_anonymous"));
			assertEquals("{}", requests.get(3).body());
			assertEquals("completion-csrf", requests.get(5).headers().get("x-csrf-token"));
			assertEquals("000042", new ObjectMapper().readTree(requests.get(5).body()).get("code").asText());
			assertFalse(requests.get(5).body().contains(PROOF));
			assertTrue(requests.stream().noneMatch(request -> request.requestLine().contains("/login ")));
		}
	}

	@ParameterizedTest
	@ValueSource(ints = { 401, 403, 429, 503 })
	void preservesConfirmationFailuresAndNeverRepeatsTheTotp(int status) throws Exception {
		try (var server = new LoopbackHttpFixture(json(200, CSRF), new LoopbackHttpFixture.Reply(status,
				"{\"code\":\"FORBIDDEN\"}", Map.of("Retry-After", "42")));
				var connection = new ServerConnection(URI.create(server.baseUrl()))) {
			HttpApiException failure = assertThrows(HttpApiException.class,
					() -> connection.completeMfaEnrollment(ENROLLMENT_ID, "000042"));
			assertEquals(status, failure.status());
			assertEquals(Duration.ofSeconds(42), failure.retryAfter().orElseThrow());
			assertEquals(2, server.requests().size());
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "null", "{}", "{\"enrollmentID\":null,\"setupKey\":\"private-key\",\"expiresAt\":null}",
			"{\"enrollmentID\":\"00000000-0000-0000-0000-000000000042\",\"setupKey\":\"private-key\",\"expiresAt\":\"2035-01-01T00:05:00Z\"}" })
	void rejectsMalformedSetupWithoutLeakingItInAnException(String body) throws Exception {
		try (var server = new LoopbackHttpFixture(json(200, CSRF), json(200, body));
				var connection = new ServerConnection(URI.create(server.baseUrl()))) {
			IOException failure = assertThrows(IOException.class, connection::beginMfaEnrollment);
			assertFalse(failure.toString().contains("private-key"));
			assertEquals(null, failure.getCause());
		}
	}

	@Test
	void rejectsDuplicateRecoveryCodesInsteadOfShowingAnIncompleteSafetyNet() throws Exception {
		String body = new ObjectMapper().writeValueAsString(Map.of("codes", java.util.Collections.nCopies(10, CODES.getFirst())));
		try (var server = new LoopbackHttpFixture(json(200, CSRF), json(200, body));
				var connection = new ServerConnection(URI.create(server.baseUrl()))) {
			assertThrows(IOException.class, () -> connection.completeMfaEnrollment(ENROLLMENT_ID, "000042"));
		}
	}

	@Test
	void secretBearingResponsesHaveRedactedRepresentationsAndImmutableRecoveryCodes() {
		var enrollment = new MfaEnrollment(ENROLLMENT_ID, SETUP_KEY, Instant.now());
		var codes = new MfaRecoveryCodes(CODES);
		assertFalse(enrollment.toString().contains(SETUP_KEY));
		assertFalse(codes.toString().contains(CODES.getFirst()));
		assertThrows(UnsupportedOperationException.class, () -> codes.codes().clear());
		assertNotNull(new ActivationResult(null));
	}

	@Test
	void refusesMalformedProofsAndCodesBeforeContactingTheServer() throws Exception {
		try (var connection = new ServerConnection(URI.create("http://127.0.0.1:1"))) {
			assertThrows(IllegalArgumentException.class, () -> connection.openMfaEnrollment("invalid"));
			assertThrows(IllegalArgumentException.class, () -> connection.completeMfaEnrollment(ENROLLMENT_ID, "１２３４５６"));
		}
	}

	private static String enrollmentJson(String expiresAt) {
		return "{\"enrollmentID\":\"" + ENROLLMENT_ID + "\",\"setupKey\":\"" + SETUP_KEY + "\",\"expiresAt\":\"" + expiresAt + "\"}";
	}
}
