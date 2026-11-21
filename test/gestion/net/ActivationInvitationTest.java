package gestion.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ActivationInvitationTest {

	private static final String ID = "00000000-0000-0000-0000-000000000001";
	private static final String TOKEN = "a".repeat(43);

	@Test
	void readsTheExactEmailFragmentWithoutMakingTheLinkANetworkDestination() {
		ActivationInvitation invitation = ActivationInvitation.fromLink("https://fictitious.example/activate#invitation="
				+ ID + "&token=" + TOKEN);
		assertEquals(UUID.fromString(ID), invitation.invitationID());
		assertEquals(TOKEN, invitation.token());
		assertFalse(invitation.toString().contains(TOKEN));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { " ", "not a link", "https://example.invalid/", "http://example.invalid/activate",
			"https://user:secret@example.invalid/activate#invitation=x&token=x",
			"file:///tmp/invitation", "javascript:alert(1)", "https://example.invalid/?token=private" })
	void rejectsMalformedLinksWithoutEchoingThem(String link) {
		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> ActivationInvitation.fromLink(link));
		assertEquals("Le lien d’invitation n’est pas valide.", failure.getMessage());
		assertNull(failure.getCause());
	}

	@Test
	void refusesAdditionalOrDuplicateFragmentParameters() {
		String prefix = "https://example.invalid/#invitation=" + ID + "&token=" + TOKEN;
		assertThrows(IllegalArgumentException.class, () -> ActivationInvitation.fromLink(prefix + "&token=" + TOKEN));
		assertThrows(IllegalArgumentException.class, () -> ActivationInvitation.fromLink(prefix + "&next=https://other.invalid"));
	}
}
