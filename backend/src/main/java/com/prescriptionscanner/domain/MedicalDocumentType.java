package com.prescriptionscanner.domain;

/** The kinds of medical document the scanner understands. */
public enum MedicalDocumentType {
	LAB_REPORT,
	PRESCRIPTION,
	OTHER;

	/** Lenient parse of the AI/user-supplied value; unknown values become OTHER. */
	public static MedicalDocumentType from(String raw) {
		if (raw == null || raw.isBlank()) {
			return OTHER;
		}
		String normalized = raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
		for (MedicalDocumentType type : values()) {
			if (type.name().equals(normalized)) {
				return type;
			}
		}
		return OTHER;
	}
}
