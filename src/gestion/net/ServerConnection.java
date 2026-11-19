package gestion.net;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ServerConnection {

	private final HttpClient http;
	private final String base;
	private final ObjectMapper mapper;

	public ServerConnection() {
		http = HttpClient.newBuilder()
				.cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER))
				.connectTimeout(Duration.ofSeconds(5))
				.build();
		base = System.getProperty("gestionplus.server.url", "http://localhost:8080");
		mapper = new ObjectMapper();

	}

	public CsrfToken fetchCsrfToken() throws IOException, InterruptedException {

		URI endpoint = URI.create(base + "/api/auth/csrf");
		HttpRequest request = HttpRequest.newBuilder(endpoint).GET().timeout(Duration.ofSeconds(5)).build();
		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
		int status = response.statusCode();

		if (status != 200) {
			throw new IOException("Erreur HTTP : " + status);
		}

		String json = response.body();
		CsrfToken csrfToken = mapper.readValue(json, CsrfToken.class);

		if (csrfToken == null || csrfToken.token() == null || csrfToken.token().isBlank()) {
			throw new IOException("Réponse CSRF invalide");
		}

		if (csrfToken.headerName() == null || csrfToken.headerName().isBlank()) {
			throw new IOException("Nom de l'en-tête CSRF manquant ou vide.");
		}

		return csrfToken;
	}

	public record CsrfToken(String headerName, String token) {
	}
}
