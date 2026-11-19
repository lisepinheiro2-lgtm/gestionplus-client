package gestion.net;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpClient;
import java.time.Duration;

public class ServerConnection {

	private final HttpClient http;

	public ServerConnection() {
		http = HttpClient.newBuilder()
				.cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER))
				.connectTimeout(Duration.ofSeconds(5))
				.build();
	}
}
