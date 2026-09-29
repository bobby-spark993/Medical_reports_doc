package com.prescriptionscanner.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import tools.jackson.databind.JsonNode;

/** A medical document plus its reviewed lab results / prescription. */
public record MedicalDocumentDto(
		Long id,
		String documentType,
		String originalFileName,
		String originalFilePath,
		LocalDate documentDate,
		LocalDate reportedDate,
		String labName,
		String labReportId,
		String referredBy,
		boolean reviewedByUser,
		Instant createdAt,
		/** The complete extracted JSON, verbatim, for a readable audit trail. */
		JsonNode rawExtractedJson,
		List<LabResultDto> results,
		PrescriptionDto prescription) {

	public record LabResultDto(
			Long id,
			String testGroup,
			String testName,
			String resultValue,
			String unit,
			String referenceRange,
			String flag) {
	}

	public record PrescriptionDto(
			Long id,
			String doctorName,
			String doctorQualification,
			String clinicName,
			String registrationNo,
			LocalDate appointmentDate,
			String appointmentNo,
			LocalDate validUpTo,
			LocalDate patientRegdValidUpTo,
			String diagnosisOrComplaints,
			List<MedicineDto> medicines,
			String advice,
			String handwrittenRawText,
			String confidence) {
	}

	public record MedicineDto(
			String name,
			String dose,
			String frequency,
			String duration,
			String notes) {
	}
}
