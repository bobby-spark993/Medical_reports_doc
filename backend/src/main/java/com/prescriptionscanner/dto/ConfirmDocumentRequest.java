package com.prescriptionscanner.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.databind.JsonNode;

/**
 * Payload for POST /api/documents/confirm: the reviewed/edited draft plus the
 * user's decision about which patient it belongs to. Dates are ISO strings and
 * parsed leniently (null when absent).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ConfirmDocumentRequest(
		/** AUTO | LINK_EXISTING | CREATE_NEW */
		String patientAction,
		Long patientId,
		PatientInput patient,
		String documentType,
		String originalFilePath,
		String originalFileName,
		String documentDate,
		String reportedDate,
		String labName,
		String labReportId,
		String referredBy,
		JsonNode rawExtractedJson,
		List<LabResultInput> results,
		PrescriptionInput prescription) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record PatientInput(
			String pid,
			String name,
			String gender,
			String age,
			String dateOfBirth,
			String maritalStatus,
			String phone,
			String address,
			/** Trailing 6-digit number printed on lab reports (not persisted). */
			@JsonAlias("refNo") String patientRefNo) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record LabResultInput(
			String testGroup,
			String testName,
			String resultValue,
			String unit,
			String referenceRange,
			String flag) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record PrescriptionInput(
			String doctorName,
			String doctorQualification,
			String clinicName,
			String registrationNo,
			String appointmentDate,
			String appointmentNo,
			String validUpTo,
			String patientRegdValidUpTo,
			String diagnosisOrComplaints,
			List<MedicineInput> medicines,
			String advice,
			String handwrittenRawText,
			String confidence) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record MedicineInput(
			String name,
			String dose,
			String frequency,
			String duration,
			String notes) {
	}
}
