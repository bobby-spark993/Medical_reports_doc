package com.prescriptionscanner.dto;

/** Result of POST /api/prescriptions/{id}/verify. */
public record VerifyResponse(
		boolean ok,
		Long visitId,
		Long patientId,
		boolean patientCreated,
		String patientPid,
		Long doctorId,
		Counts counts,
		String message) {

	public record Counts(int diagnoses, int medicines, int labResults) {
	}
}
