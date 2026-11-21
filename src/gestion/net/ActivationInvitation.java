package gestion.net;

import java.net.URI;
import java.util.UUID;
import java.util.regex.Pattern;

public record ActivationInvitation(UUID invitationID, String token) {

	private static final Pattern FRAGMENT = Pattern.compile(
			"invitation=([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})&token=([A-Za-z0-9_-]{43})");

	public ActivationInvitation {
		if (invitationID == null || token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
			throw new IllegalArgumentException("Le lien d’invitation n’est pas valide.");
		}
	}

	public static ActivationInvitation fromLink(String supplied) {
		try {
			if (supplied == null || supplied.length() > 4096) {
				throw new IllegalArgumentException();
			}
			URI link = URI.create(supplied.strip());
			boolean local = "localhost".equalsIgnoreCase(link.getHost()) || "127.0.0.1".equals(link.getHost())
					|| "[::1]".equals(link.getHost());
			if (link.getHost() == null || link.getRawUserInfo() != null || link.getRawQuery() != null
					|| !("https".equalsIgnoreCase(link.getScheme()) || local && "http".equalsIgnoreCase(link.getScheme()))) {
				throw new IllegalArgumentException();
			}
			var match = FRAGMENT.matcher(link.getRawFragment() == null ? "" : link.getRawFragment());
			if (!match.matches()) {
				throw new IllegalArgumentException();
			}
			return new ActivationInvitation(UUID.fromString(match.group(1)), match.group(2));
		} catch (IllegalArgumentException failure) {
			throw new IllegalArgumentException("Le lien d’invitation n’est pas valide.");
		}
	}

	@Override
	public String toString() {
		return "ActivationInvitation[protected]";
	}
}
