package gestion.net;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SessionExpiration(Instant expiresAt, Instant warnAt) {

	public SessionExpiration {
		if (expiresAt == null || warnAt == null || warnAt.isAfter(expiresAt)) {
			throw new IllegalArgumentException("Les échéances de session sont absentes ou incohérentes.");
		}
	}
}
