package com.prescriptionscanner.domain;

/** How confident the AI is about handwritten parts of a prescription. */
public enum ConfidenceLevel {
	LOW,
	MEDIUM,
	HIGH;

	public static ConfidenceLevel from(String raw) {
		if (raw == null || raw.isBlank()) {
			return LOW;
		}
		for (ConfidenceLevel level : values()) {
			if (level.name().equalsIgnoreCase(raw.trim())) {
				return level;
			}
		}
		return LOW;
	}
}
