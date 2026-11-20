package gestion.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import gestion.app.LoginController.Outcome;
import gestion.app.LoginController.Result;
import gestion.net.ServerConnection.CurrentUser;

class LoginControllerResultTest {

	@Test
	void preservesTheConfirmedIdentityForTheConnectedApplication() {
		CurrentUser user = new CurrentUser("fictitious_user");
		Result result = new Result(Outcome.AUTHENTICATED, user);
		assertEquals(Outcome.AUTHENTICATED, result.outcome());
		assertEquals(user, result.user());
	}

	@Test
	void requiresAnIdentityForAnAuthenticatedResult() {
		assertThrows(IllegalArgumentException.class, () -> new Result(Outcome.AUTHENTICATED, null));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { " " })
	void rejectsAnAuthenticatedResultWithoutAUsableUsername(String userName) {
		assertThrows(IllegalArgumentException.class,
				() -> new Result(Outcome.AUTHENTICATED, new CurrentUser(userName)));
	}

	@ParameterizedTest
	@EnumSource(value = Outcome.class, names = { "MODE_SELECTION", "CLOSED" })
	void keepsNonAuthenticatedResultsFreeOfIdentity(Outcome outcome) {
		Result result = new Result(outcome, null);
		assertEquals(outcome, result.outcome());
		assertNull(result.user());
		assertThrows(IllegalArgumentException.class, () -> new Result(outcome, new CurrentUser("fictitious_user")));
	}
}
