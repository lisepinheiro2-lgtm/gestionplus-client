package gestion.net;

import java.util.HashSet;
import java.util.List;

public record MfaRecoveryCodes(List<String> codes) {

	public MfaRecoveryCodes {
		if (codes == null || codes.size() != 10 || codes.stream().anyMatch(code -> code == null
				|| !code.matches("[A-Za-z0-9_-]{22}")) || new HashSet<>(codes).size() != codes.size()) {
			throw new IllegalArgumentException("Les codes de récupération sont invalides.");
		}
		codes = List.copyOf(codes);
	}

	@Override
	public String toString() {
		return "MfaRecoveryCodes[protected]";
	}
}
