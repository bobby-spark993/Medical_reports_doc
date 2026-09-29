package com.prescriptionscanner.dto;

import java.util.List;

/**
 * Result of POST /api/documents/scan: one item per uploaded file, each with the
 * AI draft, the stored file path and any patient-match suggestions. Nothing is
 * saved to the database yet.
 */
public record ScanResponse(boolean ok, List<ScanItem> items) {

	public record ScanItem(
			MedicalExtraction extracted,
			String originalFilePath,
			String originalFileName,
			/** Set when the patient was matched automatically by pid or pidShort. */
			Long matchedPatientId,
			String matchedPatientName,
			/** Candidates with the same name + age + gender; ask the user to choose. */
			List<PatientSummaryDto> possibleMatches) {
	}
}
