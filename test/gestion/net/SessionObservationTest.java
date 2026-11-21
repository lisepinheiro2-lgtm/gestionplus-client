package gestion.net;

import static gestion.net.LoopbackHttpFixture.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import gestion.net.LoopbackHttpFixture.Reply;

class SessionObservationTest {

	private static final Instant SERVER_TIME = Instant.parse("2050-06-15T12:00:00Z");
	private static final String BODY = "{\"expiresAt\":\"2050-06-15T12:30:00Z\",\"warnAt\":\"2050-06-15T12:28:00Z\"}";

	@Test
	void readsTheServerDateSoTheCountdownDoesNotDependOnTheWorkstationsClock() throws Exception {
		String date = DateTimeFormatter.RFC_1123_DATE_TIME.format(SERVER_TIME.atZone(ZoneOffset.UTC));
		try (var server = new LoopbackHttpFixture(new Reply(200, BODY, Map.of("Date", date)));
				var connection = new ServerConnection(URI.create(server.baseUrl()))) {
			SessionObservation observation = connection.fetchSessionObservation();
			assertEquals(SERVER_TIME, observation.serverTime());
			assertEquals(SERVER_TIME.plusSeconds(1_800), observation.expiration().expiresAt());
			assertEquals(SERVER_TIME.plusSeconds(1_680), observation.expiration().warnAt());
			assertFalse(observation.roundTrip().isNegative());
			assertEquals("GET /api/auth/session HTTP/1.1", server.requests().getFirst().requestLine());
		}
	}

	@Test
	void supportsResponsesWithoutADateHeaderWithoutDiscardingTheServerDeadlines() throws Exception {
		try (var server = new LoopbackHttpFixture(json(200, BODY));
				var connection = new ServerConnection(URI.create(server.baseUrl()))) {
			Instant before = Instant.now();
			SessionObservation observation = connection.fetchSessionObservation();
			Instant after = Instant.now();
			assertFalse(observation.serverTime().isBefore(before));
			assertFalse(observation.serverTime().isAfter(after));
			assertEquals(SERVER_TIME.plusSeconds(1_800), observation.expiration().expiresAt());
		}
	}

	@Test
	void rejectsAnInvalidServerDateInsteadOfInventingACountdown() throws Exception {
		try (var server = new LoopbackHttpFixture(new Reply(200, BODY, Map.of("Date", "not-a-date")));
				var connection = new ServerConnection(URI.create(server.baseUrl()))) {
			assertThrows(IOException.class, connection::fetchSessionObservation);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "session", "me", "csrf" })
	void notifiesTheSessionOwnerForA401OnEveryExistingRead(String route) throws Exception {
		AtomicInteger rejected = new AtomicInteger();
		try (var server = new LoopbackHttpFixture(json(401, "{\"code\":\"AUTHENTICATION_REQUIRED\"}"));
				var connection = new ServerConnection(URI.create(server.baseUrl()))) {
			connection.setSessionInvalidationHandler(rejected::incrementAndGet);
			switch (route) {
			case "session" -> assertThrows(HttpApiException.class, connection::fetchSessionObservation);
			case "me" -> assertTrue(connection.fetchCurrentUser().isEmpty());
			case "csrf" -> assertThrows(HttpApiException.class, connection::fetchCsrfToken);
			}
			assertEquals(1, rejected.get());
		}
	}

	@ParameterizedTest
	@ValueSource(ints = { 400, 403, 404, 500, 503 })
	void otherHttpFailuresDoNotInvalidateTheSession(int status) throws Exception {
		AtomicInteger rejected = new AtomicInteger();
		try (var server = new LoopbackHttpFixture(json(status, "{\"code\":\"FICTITIOUS_ERROR\"}"));
				var connection = new ServerConnection(URI.create(server.baseUrl()))) {
			connection.setSessionInvalidationHandler(rejected::incrementAndGet);
			assertEquals(status, assertThrows(HttpApiException.class, connection::fetchSessionObservation).status());
			assertEquals(0, rejected.get());
		}
	}

	@Test
	void anOrdinaryLoginRefusalWithoutASessionOwnerRemainsALoginResult() throws Exception {
		String csrf = "{\"headerName\":\"X-CSRF-TOKEN\",\"token\":\"fictitious_csrf\"}";
		try (var server = new LoopbackHttpFixture(json(200, csrf),
				json(401, "{\"code\":\"AUTHENTICATION_REQUIRED\"}"));
				var connection = new ServerConnection(URI.create(server.baseUrl()))) {
			assertEquals(LoginStatus.REJECTED,
					connection.login("fictitious_user", "FictitiousPassword123!".toCharArray(), "").status());
		}
	}

	@Test
	void aClosedConnectionCannotNotifyItsFormerSessionOwnerOrStartAnotherCheck() throws Exception {
		AtomicInteger rejected = new AtomicInteger();
		try (var connection = new ServerConnection(URI.create("http://127.0.0.1:1"))) {
			connection.setSessionInvalidationHandler(rejected::incrementAndGet);
			connection.close();
			assertThrows(IOException.class, connection::fetchSessionObservation);
			assertEquals(0, rejected.get());
		}
	}

	@Test
	void observationsRequireTheirDatesAndANonNegativeNetworkDuration() {
		SessionExpiration expiration = new SessionExpiration(SERVER_TIME.plusSeconds(300), SERVER_TIME.plusSeconds(180));
		assertThrows(NullPointerException.class, () -> new SessionObservation(null, SERVER_TIME, Duration.ZERO));
		assertThrows(NullPointerException.class, () -> new SessionObservation(expiration, null, Duration.ZERO));
		assertThrows(NullPointerException.class, () -> new SessionObservation(expiration, SERVER_TIME, null));
		assertThrows(IllegalArgumentException.class,
				() -> new SessionObservation(expiration, SERVER_TIME, Duration.ofMillis(-1)));
	}
}

