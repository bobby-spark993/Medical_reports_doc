package com.prescriptionscanner.dto;

/**
 * Returned by POST /api/prescriptions/scan (scan-only, nothing persisted).
 *
 * @param ok              always true on success
 * @param extracted       the normalised AI extraction
 * @param matchedPatient  existing patient matched by pid first, else by
 *                        name + age + gender, else null = new folder
 */
public record ScanDraftResponse(
		boolean ok,
		GeminiExtraction extracted,
		PatientSummaryDto matchedPatient) {

	public static ScanDraftResponse of(GeminiExtraction extracted, PatientSummaryDto matchedPatient) {
		return new ScanDraftResponse(true, extracted, matchedPatient);
	}
}
