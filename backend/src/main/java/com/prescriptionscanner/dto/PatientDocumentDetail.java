package com.prescriptionscanner.dto;

import java.util.List;

/** GET /api/patients/by-pid/{pid}: patient info + every document. */
public record PatientDocumentDetail(
		boolean ok,
		PatientSummaryDto patient,
		List<MedicalDocumentDto> documents) {
}
