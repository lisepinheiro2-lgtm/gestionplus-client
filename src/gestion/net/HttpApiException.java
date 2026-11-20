package gestion.net;

import java.io.IOException;
import java.util.Optional;

public final class HttpApiException extends IOException {

	private static final long serialVersionUID = 1L;
	private final int status;
	private final String code;

	public HttpApiException(int status, String code) {
		super("Erreur HTTP : " + status);
		this.status = status;
		this.code = code;
	}

	public int status() {
		return status;
	}

	public Optional<String> code() {
		return Optional.ofNullable(code);
	}
}
