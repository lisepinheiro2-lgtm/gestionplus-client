package gestion.net;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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

	public LoginResult login(String userName, char[] password, String totpCode) {

		try {
			CsrfToken csrfToken = fetchCsrfToken();
			String formBody = buildFormBody(userName, password, totpCode);
			HttpRequest request = buildLoginRequest(csrfToken, formBody);
			HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

			switch (response.statusCode()) {
			case 200:
				return new LoginResult(LoginStatus.AUTHENTICATED, null);

			case 401:
				return new LoginResult(LoginStatus.REJECTED, null);

			case 403:
				return new LoginResult(LoginStatus.CSRF_REJECTED, null);

			case 503:
				return new LoginResult(LoginStatus.UNAVAILABLE, null);

			default:
				return new LoginResult(LoginStatus.FAILED, null);
			}

		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return new LoginResult(LoginStatus.FAILED, null);
		} catch (IOException e) {
			return new LoginResult(LoginStatus.FAILED, null);
		}

	}

	public Optional<String> fetchCurrentUser() throws IOException, InterruptedException {

		URI endpoint = URI.create(base + "/api/auth/me");
		HttpRequest request = HttpRequest.newBuilder(endpoint).GET().timeout(Duration.ofSeconds(5)).build();
		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
		int status = response.statusCode();

		if (status == 401) {
			return Optional.empty();
		}

		if (status != 200) {
			throw new IOException("Erreur HTTP : " + status);
		}

		String json = response.body();
		CurrentUser currentUser = mapper.readValue(json, CurrentUser.class);

		if (currentUser.userName() == null || currentUser.userName().isBlank()) {
			throw new IOException("Le nom d'utilisateur ne peut être null ou vide");
		}

		return Optional.of(currentUser.userName());
	}

	private HttpRequest buildLoginRequest(CsrfToken csrfToken, String formBody) {

		Objects.requireNonNull(csrfToken, "Le jeton CSRF ne peut pas être null");
		Objects.requireNonNull(formBody, "Le corps du formulaire ne peut pas être null");

		URI endpoint = URI.create(base + "/login");
		HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint).header(csrfToken.headerName(), csrfToken.token())
				.header("Content-Type", "application/x-www-form-urlencoded")
				.POST(HttpRequest.BodyPublishers.ofString(formBody)).timeout(Duration.ofSeconds(5));

		return builder.build();
	}

	private String buildFormBody(String userName, char[] password, String totpCode) {

		Objects.requireNonNull(userName, "Le nom utilisateur ne peut pas être null");
		Objects.requireNonNull(password, "Le mot de passe ne peut pas être null");

		List<String> formParameters = new ArrayList<>();
		formParameters.add("username=" + URLEncoder.encode(userName, StandardCharsets.UTF_8));
		formParameters.add("password=" + URLEncoder.encode(new String(password), StandardCharsets.UTF_8));

		if (totpCode != null && !totpCode.isBlank()) {
			formParameters.add("totp=" + URLEncoder.encode(totpCode, StandardCharsets.UTF_8));
		}

		return String.join("&", formParameters);
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record CsrfToken(String headerName, String token) {
	}

	public record LoginResult(LoginStatus status, String userName) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record CurrentUser(String userName) {
	}

}
