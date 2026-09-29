package com.prescriptionscanner.dto;

import java.util.List;

import tools.jackson.databind.JsonNode;

/**
 * The "Verify & Save" payload sent by the review screen.
 *
 * <p>Every field is validated before it reaches the database. rawAiJson carries
 * the untouched AI output so the audit trail keeps what the model originally
 * said, even after staff corrections.
 */
public record VerifyRequest(
		PatientInput patient,
		DoctorInput doctor,
		VisitInput visit,
		List<DiagnosisInput> diagnoses,
		List<MedicineInput> medicines,
		List<LabResultInput> labResults,
		JsonNode rawAiJson) {

	public record PatientInput(
			String pid,
			String name,
			String gender,
			String age,
			String maritalStatus,
			String phone,
			String address,
			String allergies) {
	}

	public record DoctorInput(
			String name,
			String qualification,
			String registrationNo,
			String designation,
			String clinic) {
	}

	public record VisitInput(
			String visitDate,
			String visitTime,
			String validUpTo,
			String appointmentNo,
			String mode,
			String followUpDate,
			String notes) {
	}

	public record DiagnosisInput(String description) {
	}

	public record MedicineInput(
			String name,
			String dose,
			String frequency,
			String duration,
			String instructions) {
	}

	public record LabResultInput(
			String testName,
			String value,
			String unit,
			String referenceRange,
			Boolean isAbnormal) {
	}
}
