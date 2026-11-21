package gestion.net;

import java.io.IOException;
import java.util.Optional;
import java.time.Duration;

public final class HttpApiException extends IOException {

	private static final long serialVersionUID = 1L;
	private final int status;
	private final String code;
	private final Duration retryAfter;

	public HttpApiException(int status, String code) {
		this(status, code, null);
	}

	public HttpApiException(int status, String code, Duration retryAfter) {
		super("Erreur HTTP : " + status);
		this.status = status;
		this.code = code;
		this.retryAfter = retryAfter;
	}

	public int status() {
		return status;
	}

	public Optional<String> code() {
		return Optional.ofNullable(code);
	}

	public Optional<Duration> retryAfter() {
		return Optional.ofNullable(retryAfter);
	}
}
