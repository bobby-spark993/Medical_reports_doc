package com.prescriptionscanner.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The strict JSON shape Gemini returns for a medical document (see
 * {@code prompts/medical-extraction-schema.json}). Everything is nullable: the
 * model is told to use null for anything absent or unreadable.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MedicalExtraction(
		String documentType,
		PatientInfo patient,
		@JsonProperty("labReport") LabReportInfo labReport,
		List<LabResultItem> results,
		PrescriptionInfo prescription) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record PatientInfo(
			String name,
			/** Trailing 6-digit number printed after the name on lab reports. */
			@JsonAlias("refNo") String patientRefNo,
			String pid,
			String gender,
			String age,
			String dateOfBirth,
			String address,
			String maritalStatus) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record LabReportInfo(
			String labName,
			String labReportId,
			String documentDate,
			String reportedDate,
			String referredBy) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record LabResultItem(
			String testGroup,
			String testName,
			String resultValue,
			String unit,
			String referenceRange,
			String flag) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record PrescriptionInfo(
			String doctorName,
			String doctorQualification,
			String clinicName,
			String registrationNo,
			String appointmentDate,
			String appointmentNo,
			String validUpTo,
			String patientRegdValidUpTo,
			String diagnosisOrComplaints,
			List<MedicineItem> medicines,
			String advice,
			String handwrittenRawText,
			String confidence) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record MedicineItem(
			String name,
			String dose,
			String frequency,
			String duration,
			String notes) {
	}
}
