package gestion.net;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class ServerConnection implements AutoCloseable {

	private final HttpClient http;
	private final URI base;
	private final CookieManager cookies;
	private final ObjectMapper mapper;
	private final AtomicBoolean closed = new AtomicBoolean();
	private volatile Runnable sessionInvalidationHandler = () -> {
	};

	public ServerConnection() {
		this(URI.create(System.getProperty("gestionplus.server.url", "http://localhost:8080")));
	}

	public ServerConnection(URI base) {
		this.base = validateBase(base);
		cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER) {
			@Override
			public synchronized void put(URI uri, Map<String, List<String>> headers) throws IOException {
				if (!closed.get()) {
					super.put(uri, headers);
				}
			}
		};
		http = HttpClient.newBuilder()
				.cookieHandler(cookies)
				.connectTimeout(Duration.ofSeconds(5))
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
		mapper = new ObjectMapper().registerModule(new JavaTimeModule())
				.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	}

	public CsrfToken fetchCsrfToken() throws IOException, InterruptedException {

		URI endpoint = endpoint("/api/auth/csrf");
		HttpRequest request = HttpRequest.newBuilder(endpoint).GET().timeout(Duration.ofSeconds(5)).build();
		HttpResponse<String> response = send(request);
		int status = response.statusCode();

		if (status != 200) {
			throw apiFailure(response);
		}

		String json = response.body();
		CsrfToken csrfToken = mapper.readValue(json, CsrfToken.class);

		if (csrfToken == null || csrfToken.token() == null || csrfToken.token().isBlank()) {
			throw new IOException("Réponse CSRF invalide");
		}

		if (csrfToken.headerName() == null || csrfToken.headerName().isBlank()) {
			throw new IOException("Nom de l'en-tête CSRF manquant ou vide.");
		}

		try {
			HttpRequest.newBuilder().header(csrfToken.headerName(), csrfToken.token());
		} catch (IllegalArgumentException failure) {
			throw new IOException("En-tête CSRF invalide.");
		}

		return csrfToken;
	}

	public LoginResult login(String userName, char[] password, String totpCode) {

		try {
			CsrfToken csrfToken = fetchCsrfToken();
			String formBody = buildFormBody(userName, password, totpCode);
			HttpRequest request = buildLoginRequest(csrfToken, formBody);
			HttpResponse<String> response = send(request);

			if (response.statusCode() == 200) {
				return fetchCurrentUser().map(user -> new LoginResult(LoginStatus.AUTHENTICATED, user))
						.orElseGet(() -> new LoginResult(LoginStatus.REJECTED, null));
			}

			throw apiFailure(response);
		} catch (HttpApiException failure) {
			return new LoginResult(loginStatus(failure), null);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return new LoginResult(LoginStatus.FAILED, null);
		} catch (IOException e) {
			return new LoginResult(LoginStatus.FAILED, null);
		}

	}

	public Optional<String> fetchCurrentUser() throws IOException, InterruptedException {

		URI endpoint = endpoint("/api/auth/me");
		HttpRequest request = HttpRequest.newBuilder(endpoint).GET().timeout(Duration.ofSeconds(5)).build();
		HttpResponse<String> response = send(request);
		int status = response.statusCode();

		if (status == 401) {
			return Optional.empty();
		}

		if (status != 200) {
			throw apiFailure(response);
		}

		String json = response.body();
		CurrentUser currentUser = mapper.readValue(json, CurrentUser.class);

		if (currentUser == null || currentUser.userName() == null || currentUser.userName().isBlank()) {
			throw new IOException("Le nom d'utilisateur ne peut être null ou vide");
		}

		return Optional.of(currentUser.userName());
	}

	public SessionExpiration fetchSessionExpiration() throws IOException, InterruptedException {
		return fetchSessionObservation().expiration();
	}

	public SessionObservation fetchSessionObservation() throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(endpoint("/api/auth/session"))
				.GET().timeout(Duration.ofSeconds(5)).build();
		long started = System.nanoTime();
		HttpResponse<String> response = send(request);
		Duration roundTrip = Duration.ofNanos(System.nanoTime() - started);
		if (response.statusCode() != 200) {
			throw apiFailure(response);
		}
		SessionExpiration expiration = mapper.readValue(response.body(), SessionExpiration.class);
		if (expiration == null) {
			throw new IOException("Réponse d'échéance de session invalide.");
		}
		Instant serverTime;
		try {
			serverTime = response.headers().firstValue("Date")
					.map(value -> Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(value)))
					.orElseGet(Instant::now);
		} catch (DateTimeParseException failure) {
			throw new IOException("Date de réponse du serveur invalide.", failure);
		}
		return new SessionObservation(expiration, serverTime, roundTrip);
	}

	public void setSessionInvalidationHandler(Runnable handler) {
		sessionInvalidationHandler = Objects.requireNonNull(handler);
	}

	public List<StructureSummary> fetchAccessibleStructures() throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(endpoint("/api/structures"))
				.GET().timeout(Duration.ofSeconds(5)).build();
		HttpResponse<String> response = send(request);
		if (response.statusCode() != 200) {
			throw apiFailure(response);
		}
		List<StructureSummary> structures = mapper.readerFor(
				mapper.getTypeFactory().constructCollectionType(List.class, StructureSummary.class))
				.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(response.body());
		if (structures == null) {
			throw new IOException("Réponse de structures invalide.");
		}
		HashSet<UUID> identifiers = new HashSet<>();
		for (StructureSummary structure : structures) {
			if (structure == null || !identifiers.add(structure.structureID())) {
				throw new IOException("Réponse de structures invalide.");
			}
		}
		return List.copyOf(structures);
	}

	public Optional<StructureSummary> fetchAccessibleStructure(UUID structureID)
			throws IOException, InterruptedException {
		Objects.requireNonNull(structureID, "L'identifiant de structure est obligatoire.");
		HttpRequest request = HttpRequest.newBuilder(endpoint("/api/structures/" + structureID))
				.GET().timeout(Duration.ofSeconds(5)).build();
		HttpResponse<String> response = send(request);
		if (response.statusCode() == 404) {
			return Optional.empty();
		}
		if (response.statusCode() != 200) {
			throw apiFailure(response);
		}
		StructureSummary structure = mapper.readerFor(StructureSummary.class)
				.with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(response.body());
		if (structure == null || !structureID.equals(structure.structureID())) {
			throw new IOException("Réponse de structure invalide.");
		}
		return Optional.of(structure);
	}

	public void logout() throws IOException, InterruptedException {
		CsrfToken csrfToken;
		try {
			csrfToken = fetchCsrfToken();
		} catch (HttpApiException failure) {
			if (failure.status() != 401) {
				throw failure;
			}
			csrfToken = fetchCsrfToken();
		}
		HttpRequest request = HttpRequest.newBuilder(endpoint("/logout"))
				.header(csrfToken.headerName(), csrfToken.token())
				.POST(HttpRequest.BodyPublishers.noBody())
				.timeout(Duration.ofSeconds(5)).build();
		HttpResponse<String> response = send(request);
		if (response.statusCode() != 204) {
			throw apiFailure(response);
		}
	}

	public void requestActivationChallenge(ActivationInvitation invitation) throws IOException, InterruptedException {
		Objects.requireNonNull(invitation);
		postActivation(invitation, "/sms-challenge", Map.of("token", invitation.token()));
	}

	public void completeActivation(ActivationInvitation invitation, String userName, char[] password, String code)
			throws IOException, InterruptedException {
		Objects.requireNonNull(invitation);
		Objects.requireNonNull(password);
		postActivation(invitation, "/activation", Map.of("token", invitation.token(), "proof", code,
				"userName", userName, "password", new String(password)));
	}

	private void postActivation(ActivationInvitation invitation, String suffix, Map<String, String> body)
			throws IOException, InterruptedException {
		CsrfToken csrf = fetchCsrfToken();
		HttpRequest request = HttpRequest.newBuilder(endpoint("/api/invitations/" + invitation.invitationID() + suffix))
				.header("Content-Type", "application/json; charset=UTF-8").header(csrf.headerName(), csrf.token())
				.timeout(Duration.ofSeconds(20)).POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body),
						StandardCharsets.UTF_8)).build();
		HttpResponse<String> response = send(request);
		if (response.statusCode() != 200) {
			throw apiFailure(response);
		}
	}

	@Override
	public void close() {
		if (closed.compareAndSet(false, true)) {
			sessionInvalidationHandler = () -> {
			};
			try {
				http.shutdownNow();
			} finally {
				synchronized (cookies) {
					cookies.getCookieStore().removeAll();
				}
			}
		}
	}

	private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
		HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() == 401 && !closed.get()) {
			sessionInvalidationHandler.run();
		}
		return response;
	}

	private HttpRequest buildLoginRequest(CsrfToken csrfToken, String formBody) throws IOException {

		Objects.requireNonNull(csrfToken, "Le jeton CSRF ne peut pas être null");
		Objects.requireNonNull(formBody, "Le corps du formulaire ne peut pas être null");

		URI endpoint = endpoint("/login");
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

	private URI endpoint(String path) throws IOException {
		if (closed.get()) {
			throw new IOException("La connexion au serveur est fermée.");
		}
		return URI.create(base.toASCIIString() + path);
	}

	private HttpApiException apiFailure(HttpResponse<String> response) {
		String code = null;
		try {
			ErrorResponse error = mapper.readValue(response.body(), ErrorResponse.class);
			if (error != null) {
				code = error.code();
			}
		} catch (IOException failure) {
			code = null;
		}
		Duration retryAfter = null;
		try {
			long seconds = Long.parseLong(response.headers().firstValue("Retry-After").orElse("0"));
			if (seconds > 0 && seconds <= 86400) {
				retryAfter = Duration.ofSeconds(seconds);
			}
		} catch (NumberFormatException invalidDelay) {
			retryAfter = null;
		}
		return new HttpApiException(response.statusCode(), code, retryAfter);
	}

	private LoginStatus loginStatus(HttpApiException failure) {
		return switch (failure.status()) {
		case 400 -> LoginStatus.INVALID_REQUEST;
		case 401 -> LoginStatus.REJECTED;
		case 403 -> failure.code().filter("HTTPS_REQUIRED"::equals).isPresent()
				? LoginStatus.HTTPS_REQUIRED : LoginStatus.FORBIDDEN;
		case 503 -> LoginStatus.UNAVAILABLE;
		default -> LoginStatus.FAILED;
		};
	}

	private static URI validateBase(URI base) {
		Objects.requireNonNull(base, "L'adresse du serveur ne peut pas être nulle.");
		String scheme = base.getScheme();
		String host = base.getHost();
		if (host == null || base.getRawUserInfo() != null || base.getRawQuery() != null
				|| base.getRawFragment() != null
				|| !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))) {
			throw new IllegalArgumentException("L'adresse du serveur doit être une URL HTTP ou HTTPS valide.");
		}
		boolean loopback = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
				|| "::1".equals(host) || "[::1]".equals(host);
		if ("http".equalsIgnoreCase(scheme) && !loopback) {
			throw new IllegalArgumentException("HTTPS est obligatoire pour un serveur distant.");
		}
		String normalized = base.normalize().toASCIIString();
		while (normalized.endsWith("/")) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		return URI.create(normalized);
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record CsrfToken(String headerName, String token) {
	}

	public record LoginResult(LoginStatus status, String userName) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record CurrentUser(String userName) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ErrorResponse(String code) {
	}

}
