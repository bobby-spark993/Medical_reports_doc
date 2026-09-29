package com.prescriptionscanner.dto;

/**
 * Returned by POST /api/prescriptions/upload.
 *
 * @param ok        always true on success
 * @param id        the draft visit id, used by the review screen
 * @param isVerified always false: a draft is never verified
 * @param extracted the normalised AI extraction
 */
public record UploadResponse(
		boolean ok,
		Long id,
		boolean isVerified,
		GeminiExtraction extracted,
		/** Existing patient matched by name + age + gender (pid first), else null = new folder. */
		PatientSummaryDto matchedPatient) {

	public static UploadResponse of(Long id, GeminiExtraction extracted, PatientSummaryDto matchedPatient) {
		return new UploadResponse(true, id, false, extracted, matchedPatient);
	}
}
