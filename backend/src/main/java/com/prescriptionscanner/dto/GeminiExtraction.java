package com.prescriptionscanner.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.databind.JsonNode;

/**
 * The exact shape Gemini is asked to return.
 *
 * <p>Everything is nullable on purpose: the model is instructed to use null for
 * anything not present on the scan, and to list unreadable fields in
 * uncertainFields.
 *
 * <p>Note the JsonNode fields: Gemini is inconsistent about whether a list of
 * diagnoses contains plain strings or objects, so those are accepted raw and
 * normalised by the helper methods below.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiExtraction(
		@JsonProperty("document_type") String documentType,
		Clinic clinic,
		DoctorInfo doctor,
		PatientInfo patient,
		AppointmentInfo appointment,
		List<JsonNode> diagnoses,
		List<MedicineInfo> medicines,
		List<JsonNode> investigations,
		@JsonProperty("lab_results") List<LabResultInfo> labResults,
		@JsonProperty("follow_up_date") String followUpDate,
		String notes,
		@JsonProperty("uncertain_fields") List<String> uncertainFields) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Clinic(
			String name,
			String address,
			List<JsonNode> phone,
			String email) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record DoctorInfo(
			String name,
			String qualification,
			@JsonProperty("registration_no") String registrationNo,
			String designation) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record PatientInfo(
			String pid,
			String name,
			String gender,
			String age,
			@JsonProperty("marital_status") String maritalStatus,
			String address) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record AppointmentInfo(
			String date,
			String time,
			@JsonProperty("valid_upto") String validUpto,
			@JsonProperty("appointment_no") String appointmentNo,
			String mode) {
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
			String test,
			String value,
			String unit,
			@JsonProperty("reference_range") String referenceRange,
			Boolean abnormal) {
	}

	private static final String[] TEXT_KEYS = {
			"description", "diagnosis", "name", "test", "value", "text", "finding", "investigation"
	};

	/** Accepts ["Fever"] and [{"description":"Fever"}] alike. */
	public List<String> diagnosisStrings() {
		List<String> out = new ArrayList<>();
		if (diagnoses == null) {
			return out;
		}
		for (JsonNode node : diagnoses) {
			if (node == null || node.isNull()) {
				continue;
			}
			if (node.isString()) {
				addText(out, node.asText());
			} else if (node.isObject()) {
				addText(out, firstTextIn(node));
			} else if (node.isValueNode()) {
				addText(out, node.asText());
			}
		}
		return out;
	}

	private static String firstTextIn(JsonNode object) {
		for (String key : TEXT_KEYS) {
			JsonNode value = object.get(key);
			if (value != null && value.isValueNode() && !value.asText().isBlank()) {
				return value.asText();
			}
		}
		// Fall back to the first non-empty scalar field.
		for (Map.Entry<String, JsonNode> entry : object.properties()) {
			JsonNode value = entry.getValue();
			if (value != null && value.isValueNode() && !value.asText().isBlank()) {
				return value.asText();
			}
		}
		return null;
	}

	/** Phone numbers may arrive as strings or numbers. */
	public List<String> clinicPhones() {
		List<String> out = new ArrayList<>();
		if (clinic != null && clinic.phone() != null) {
			for (JsonNode node : clinic.phone()) {
				if (node != null && !node.isNull()) {
					addText(out, node.asText());
				}
			}
		}
		return out;
	}

	public String clinicName() {
		return clinic == null ? null : blankToNull(clinic.name());
	}

	public String clinicAddress() {
		return clinic == null ? null : blankToNull(clinic.address());
	}

	public GeminiExtraction normalized() {
		return new GeminiExtraction(
				blankToNull(documentType),
				clinic == null ? new Clinic(null, null, List.of(), null) : clinic,
				doctor == null ? new DoctorInfo(null, null, null, null) : doctor,
				patient == null ? new PatientInfo(null, null, null, null, null, null) : patient,
				appointment == null ? new AppointmentInfo(null, null, null, null, null) : appointment,
				diagnoses == null ? List.of() : diagnoses,
				medicines == null ? List.of() : medicines,
				investigations == null ? List.of() : investigations,
				labResults == null ? List.of() : labResults,
				blankToNull(followUpDate),
				blankToNull(notes),
				uncertainFields == null ? List.of() : uncertainFields);
	}

	private static void addText(List<String> out, String value) {
		String trimmed = blankToNull(value);
		if (trimmed != null) {
			out.add(trimmed);
		}
	}

	private static String blankToNull(String s) {
		if (s == null) {
			return null;
		}
		String trimmed = s.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
