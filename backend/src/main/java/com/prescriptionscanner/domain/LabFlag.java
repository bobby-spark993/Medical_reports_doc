package com.prescriptionscanner.domain;

/** Result flag as printed on a lab report: the (L)/(H) markers, else normal. */
public enum LabFlag {
	NORMAL,
	LOW,
	HIGH;

	/**
	 * Parses the flag from the value or a printed marker. Accepts "L", "(L)",
	 * "low", "H", "high", etc.; anything else is NORMAL.
	 */
	public static LabFlag from(String raw) {
		if (raw == null || raw.isBlank()) {
			return NORMAL;
		}
		String normalized = raw.trim().toUpperCase().replace("(", "").replace(")", "");
		return switch (normalized) {
			case "L", "LOW", "LL", "LOW*" -> LOW;
			case "H", "HIGH", "HH", "HIGH*" -> HIGH;
			default -> NORMAL;
		};
	}
}
