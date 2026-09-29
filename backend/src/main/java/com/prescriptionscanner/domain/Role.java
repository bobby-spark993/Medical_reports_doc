package com.prescriptionscanner.domain;

/** Staff roles. ADMIN implicitly has every permission. */
public enum Role {
	ADMIN("admin"),
	DOCTOR("doctor"),
	RECEPTIONIST("receptionist");

	private final String value;

	Role(String value) {
		this.value = value;
	}

	public String value() {
		return value;
	}

	/** Parses the lowercase form used in JSON and in the database. */
	public static Role from(String raw) {
		if (raw == null) {
			return RECEPTIONIST;
		}
		String normalized = raw.trim().toLowerCase();
		for (Role r : values()) {
			if (r.value.equals(normalized) || r.name().equalsIgnoreCase(normalized)) {
				return r;
			}
		}
		return RECEPTIONIST;
	}
}
