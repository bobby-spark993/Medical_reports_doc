package com.prescriptionscanner.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The exact shape Gemini is asked to return (see
 * {@code prompts/prescription-extraction-schema.json}).
 *
 * <p>Everything is nullable on purpose: the model is instructed to use null for
 * anything not present on the scan, and to list unreadable fields in
 * uncertainFields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiExtraction(
		@JsonProperty("document_type") String documentType,
		@JsonProperty("document_title") String documentTitle,
		Facility facility,
		PatientInfo patient,
		DoctorInfo doctor,
		@JsonProperty("referred_by") String referredBy,
		AppointmentInfo appointment,
		ReportInfo report,
		@JsonProperty("chief_complaints") List<String> chiefComplaints,
		List<String> examination,
		List<String> diagnoses,
		List<MedicineInfo> medicines,
		@JsonProperty("investigations_advised") List<String> investigationsAdvised,
		List<String> advice,
		@JsonProperty("follow_up_date") String followUpDate,
		@JsonProperty("lab_results") List<LabResultInfo> labResults,
		RadiologyInfo radiology,
		@JsonProperty("handwriting_confidence") String handwritingConfidence,
		String notes,
		@JsonProperty("uncertain_fields") List<String> uncertainFields) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Facility(
			String name,
			String address,
			List<String> phone,
			String email,
			String website) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record PatientInfo(
			String pid,
			@JsonProperty("patient_ref_no") String patientRefNo,
			String name,
			String gender,
			String age,
			@JsonProperty("age_years") Integer ageYears,
			@JsonProperty("marital_status") String maritalStatus,
			String address,
			@JsonProperty("pt_regd_valid_upto") String ptRegdValidUpto) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record DoctorInfo(
			String name,
			String qualification,
			@JsonProperty("registration_no") String registrationNo,
			String designation) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record AppointmentInfo(
			String date,
			@JsonProperty("valid_upto") String validUpto,
			@JsonProperty("appointment_no") String appointmentNo,
			String mode) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ReportInfo(
			@JsonProperty("report_id") String reportId,
			@JsonProperty("received_on") String receivedOn,
			@JsonProperty("reported_on") String reportedOn,
			@JsonProperty("report_date") String reportDate,
			@JsonProperty("signed_by") String signedBy,
			@JsonProperty("signed_by_designation") String signedByDesignation) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record MedicineInfo(
			String name,
			String dose,
			String frequency,
			String duration,
			String instructions) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record LabResultInfo(
			String group,
			String test,
			String value,
			String unit,
			@JsonProperty("reference_range") String referenceRange,
			String flag,
			Boolean abnormal,
			String remark) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record RadiologyInfo(
			String examination,
			String protocol,
			List<String> observations,
			String impression) {
	}

	/** A completely empty extraction, used when stored JSON cannot be parsed. */
	public static GeminiExtraction empty() {
		return new GeminiExtraction(null, null, null, null, null, null, null, null,
				List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null,
				List.of(), null, null, null, List.of());
	}

	/** Replaces every null with an empty value so the UI never sees null. */
	public GeminiExtraction normalized() {
		return new GeminiExtraction(
				blankToNull(documentType),
				blankToNull(documentTitle),
				facility == null ? new Facility(null, null, List.of(), null, null) : facility,
				patient == null
						? new PatientInfo(null, null, null, null, null, null, null, null, null)
						: patient,
				doctor == null ? new DoctorInfo(null, null, null, null) : doctor,
				blankToNull(referredBy),
				appointment == null ? new AppointmentInfo(null, null, null, null) : appointment,
				report == null ? new ReportInfo(null, null, null, null, null, null) : report,
				chiefComplaints == null ? List.of() : chiefComplaints,
				examination == null ? List.of() : examination,
				diagnoses == null ? List.of() : diagnoses,
				medicines == null ? List.of() : medicines,
				investigationsAdvised == null ? List.of() : investigationsAdvised,
				advice == null ? List.of() : advice,
				blankToNull(followUpDate),
				labResults == null ? List.of() : labResults,
				radiology == null ? new RadiologyInfo(null, null, List.of(), null) : radiology,
				blankToNull(handwritingConfidence),
				blankToNull(notes),
				uncertainFields == null ? List.of() : uncertainFields);
	}

	public String facilityName() {
		return facility == null ? null : blankToNull(facility.name());
	}

	public String facilityAddress() {
		return facility == null ? null : blankToNull(facility.address());
	}

	public List<String> facilityPhones() {
		if (facility == null || facility.phone() == null) {
			return List.of();
		}
		return facility.phone().stream().filter(p -> p != null && !p.isBlank()).toList();
	}

	private static String blankToNull(String s) {
		if (s == null) {
			return null;
		}
		String trimmed = s.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
