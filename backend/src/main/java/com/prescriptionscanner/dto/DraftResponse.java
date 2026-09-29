package com.prescriptionscanner.dto;

import java.time.Instant;

/** Returned by GET /api/prescriptions/{id}: the draft/visit plus its AI JSON. */
public record DraftResponse(
		boolean ok,
		VisitDto visit,
		GeminiExtraction extracted,
		/** The exact JSON stored on the visit in PostgreSQL, for a clean UI preview. */
		String rawJson,
		/** Existing patient matched by pid or name+age+gender; null means a new folder. */
		PatientSummaryDto matchedPatient,
		Ref patient,
		Ref doctor) {

	public record Ref(Long id, String pid, String name) {
	}

	public static DraftResponse of(VisitDto visit, GeminiExtraction extracted, String rawJson,
			PatientSummaryDto matchedPatient, Ref patient, Ref doctor) {
		return new DraftResponse(true, visit, extracted, rawJson, matchedPatient, patient, doctor);
	}

	/** Minimal visit view for the review screen header. */
	public record DraftVisit(
			Long id,
			Long patientId,
			Long doctorId,
			boolean isVerified,
			boolean hasScan,
			Instant createdAt,
			Instant updatedAt) {
	}
}
