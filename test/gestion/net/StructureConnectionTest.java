package gestion.net;

import static gestion.net.LoopbackHttpFixture.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StructureConnectionTest {

	private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final String STRUCTURE = "{\"structureID\":\"" + FIRST
			+ "\",\"structureName\":\"Fictitious North\"}";

	@Test
	void readsTheAuthorizedListWithoutInferringRolesOrExposingMutableState() throws Exception {
		String body = "[" + STRUCTURE.substring(0, STRUCTURE.length() - 1) + ",\"futureField\":true}]";
		try (var server = new LoopbackHttpFixture(json(200, body));
				var connection = new ServerConnection(URI.create(server.baseUrl()))) {
			List<StructureSummary> structures = connection.fetchAccessibleStructures();
			assertEquals(List.of(new StructureSummary(FIRST, "Fictitious North")), structures);
			assertThrows(UnsupportedOperationException.class, structures::clear);
			assertEquals("GET /api/structures HTTP/1.1", server.requests().getFirst().requestLine());
		}
	}

	@Test
	void acceptsNoAccessAsAnEmptyList() throws Exception {
		try (var server = new LoopbackHttpFixture(json(200, "[]"));
				var connection = new ServerConnection(URI.create(server.baseUrl()))) {
			assertTrue(connection.fetchAccessibleStructures().isEmpty());
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "null", "{}", "[null]", "[{}]", "[{\"structureID\":\"invalid\",\"structureName\":\"X\"}]",
			"[{\"structureID\":\"00000000-0000-0000-0000-000000000001\",\"structureName\":\" \"}]", "[] trailing" })
	void refusesMalformedListsRatherThanReplacingThemWithFictitiousData(String body) throws Exception {
		try (var server = new LoopbackHttpFixture(json(200, body));
				var connection = new ServerConnection(URI.create(server.baseUrl()))) {
			assertThrows(IOException.class, connection::fetchAccessibleStructures);
		}
	}

	@Test
	void rejectsDuplicateIdentifiers() throws Exception {
		try (var server = new LoopbackHttpFixture(json(200, "[" + STRUCTURE + "," + STRUCTURE + "]"));
				var connection = new ServerConnection(URI.create(server.baseUrl()))) {
			assertThrows(IOException.class, connection::fetchAccessibleStructures);
		}
	}

	@Test
	void preservesTheContextPathAndTheSessionCookieForTheDetailRead() throws Exception {
		try (var server = new LoopbackHttpFixture(
				new LoopbackHttpFixture.Reply(200, "[" + STRUCTURE + "]",
						Map.of("Set-Cookie", "JSESSIONID=fictitious_structure_session; Path=/")),
				json(200, STRUCTURE));
				var connection = new ServerConnection(URI.create(server.baseUrl() + "/fictitious-context"))) {
			connection.fetchAccessibleStructures();
			assertEquals(FIRST, connection.fetchAccessibleStructure(FIRST).orElseThrow().structureID());
			assertEquals("GET /fictitious-context/api/structures/" + FIRST + " HTTP/1.1",
					server.requests().getLast().requestLine());
			assertTrue(server.requests().getLast().headers().get("cookie").contains("fictitious_structure_session"));
		}
	}

	@Test
	void treatsA404AsInaccessibleWithoutDistinguishingForeignAndMissingStructures() throws Exception {
		try (var server = new LoopbackHttpFixture(json(404, "{\"code\":\"NOT_FOUND\"}"));
				var connection = new ServerConnection(URI.create(server.baseUrl()))) {
			assertTrue(connection.fetchAccessibleStructure(FIRST).isEmpty());
		}
	}

	@Test
	void rejectsADetailWhoseIdentifierDoesNotMatchTheRequest() throws Exception {
		try (var server = new LoopbackHttpFixture(json(200, STRUCTURE));
				var connection = new ServerConnection(URI.create(server.baseUrl()))) {
			assertThrows(IOException.class, () -> connection.fetchAccessibleStructure(SECOND));
		}
	}

	@Test
	void a401NotifiesTheCentralSessionHandlerForBothRoutes() throws Exception {
		try (var server = new LoopbackHttpFixture(json(401, "{\"code\":\"AUTHENTICATION_REQUIRED\"}"),
				json(401, "{\"code\":\"AUTHENTICATION_REQUIRED\"}"));
				var connection = new ServerConnection(URI.create(server.baseUrl()))) {
			AtomicInteger rejected = new AtomicInteger();
			connection.setSessionInvalidationHandler(rejected::incrementAndGet);
			assertEquals(401, assertThrows(HttpApiException.class, connection::fetchAccessibleStructures).status());
			assertEquals(401, assertThrows(HttpApiException.class,
					() -> connection.fetchAccessibleStructure(FIRST)).status());
			assertEquals(2, rejected.get());
		}
	}

	@Test
	void aServerFailureIsNotAnEmptyAuthorizedList() throws Exception {
		try (var server = new LoopbackHttpFixture(json(503, "{\"code\":\"SERVICE_UNAVAILABLE\"}"));
				var connection = new ServerConnection(URI.create(server.baseUrl()))) {
			assertEquals(503, assertThrows(HttpApiException.class, connection::fetchAccessibleStructures).status());
		}
	}

	@Test
	void closedConnectionsCannotReadStructures() {
		var connection = new ServerConnection(URI.create("http://127.0.0.1:1"));
		connection.close();
		assertThrows(IOException.class, connection::fetchAccessibleStructures);
		assertThrows(IOException.class, () -> connection.fetchAccessibleStructure(FIRST));
	}

	@Test
	void requiresAnIdentifierAndADisplayName() {
		assertThrows(NullPointerException.class, () -> new StructureSummary(null, "Fictitious"));
		assertThrows(IllegalArgumentException.class, () -> new StructureSummary(FIRST, null));
		assertThrows(IllegalArgumentException.class, () -> new StructureSummary(FIRST, ""));
		assertFalse(new StructureSummary(FIRST, "Fictitious").structureName().isBlank());
	}
}
