package gestion.net;

public record ActivationResult(String enrollmentProof) {

	public ActivationResult {
		if (enrollmentProof != null && !enrollmentProof.matches("[A-Za-z0-9_-]{22}")) {
			throw new IllegalArgumentException("La preuve d’enrôlement est invalide.");
		}
	}

	public boolean requiresMfaEnrollment() {
		return enrollmentProof != null;
	}

	@Override
	public String toString() {
		return "ActivationResult[requiresMfaEnrollment=" + requiresMfaEnrollment() + "]";
	}
}
