package gestion.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

final class LoopbackHttpFixture implements AutoCloseable {

	private final ServerSocket server;
	private final ExecutorService executor;
	private final Future<List<CapturedRequest>> exchange;

	LoopbackHttpFixture(Reply... replies) throws IOException {
		server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
		server.setSoTimeout(5_000);
		executor = Executors.newSingleThreadExecutor();
		exchange = executor.submit(() -> serve(List.of(replies)));
	}

	String baseUrl() {
		return "http://127.0.0.1:" + server.getLocalPort();
	}

	List<CapturedRequest> requests() throws Exception {
		return exchange.get(5, TimeUnit.SECONDS);
	}

	static Reply json(int status, String body) {
		return new Reply(status, body, Map.of());
	}

	@Override
	public void close() throws Exception {
		try {
			exchange.get(5, TimeUnit.SECONDS);
		} finally {
			server.close();
			executor.shutdownNow();
			if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
				throw new AssertionError("Le serveur HTTP fictif ne s'est pas arrêté.");
			}
		}
	}

	private List<CapturedRequest> serve(List<Reply> replies) throws IOException {
		List<CapturedRequest> requests = new ArrayList<>();
		for (Reply reply : replies) {
			try (Socket client = server.accept()) {
				client.setSoTimeout(5_000);
				BufferedReader input = new BufferedReader(
						new InputStreamReader(client.getInputStream(), StandardCharsets.ISO_8859_1));
				String requestLine = input.readLine();
				if (requestLine == null) {
					throw new IOException("Requête HTTP fictive absente.");
				}
				Map<String, String> headers = new LinkedHashMap<>();
				String line;
				while ((line = input.readLine()) != null && !line.isEmpty()) {
					int separator = line.indexOf(':');
					if (separator < 1) {
						throw new IOException("En-tête HTTP fictif invalide.");
					}
					headers.merge(line.substring(0, separator).toLowerCase(Locale.ROOT),
							line.substring(separator + 1).strip(), (first, second) -> first + "; " + second);
				}
				int length = Integer.parseInt(headers.getOrDefault("content-length", "0"));
				char[] requestBody = new char[length];
				int received = 0;
				while (received < length) {
					int count = input.read(requestBody, received, length - received);
					if (count < 0) {
						throw new IOException("Corps HTTP fictif incomplet.");
					}
					received += count;
				}
				requests.add(new CapturedRequest(requestLine, Map.copyOf(headers), new String(requestBody)));
				byte[] body = reply.status() == 204 ? new byte[0] : reply.body().getBytes(StandardCharsets.UTF_8);
				StringBuilder response = new StringBuilder("HTTP/1.1 ").append(reply.status()).append(" Test\r\n")
						.append("Content-Type: application/json; charset=UTF-8\r\n")
						.append("Connection: close\r\n");
				if (reply.status() != 204) {
					response.append("Content-Length: ").append(body.length).append("\r\n");
				}
				for (var header : reply.headers().entrySet()) {
					response.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
				}
				response.append("\r\n");
				client.getOutputStream().write(response.toString().getBytes(StandardCharsets.ISO_8859_1));
				client.getOutputStream().write(body);
				client.getOutputStream().flush();
			}
		}
		return List.copyOf(requests);
	}

	record Reply(int status, String body, Map<String, String> headers) {
	}

	record CapturedRequest(String requestLine, Map<String, String> headers, String body) {
	}
}
