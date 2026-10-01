package com.prescriptionscanner.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.prescriptionscanner.domain.Visit;

/** A visit with its children, ready for JSON. */
public record VisitDto(
		Long id,
		LocalDate visitDate,
		String visitTime,
		LocalDate validUpTo,
		String appointmentNo,
		String mode,
		LocalDate followUpDate,
		String notes,
		boolean isVerified,
		boolean hasScan,
		String batchId,
		Integer pageNo,
		Integer pageCount,
		Long patientId,
		Long doctorId,
		DoctorDto doctor,
		List<String> diagnoses,
		List<Medicine> medicines,
		List<LabResult> labResults,
		Instant createdAt,
		Instant updatedAt) {

	public record DoctorDto(
			Long id,
			String name,
			String qualification,
			String registrationNo,
			String designation,
			String clinic) {
	}

	public record Medicine(
			Long id,
			String name,
			String dose,
			String frequency,
			String duration,
			String instructions) {
	}

	public record LabResult(
			Long id,
			String testName,
			String value,
			String unit,
			String referenceRange,
			boolean isAbnormal) {
	}

	/**
	 * Full mapping. Call inside a transaction: it touches the lazy children and
	 * the doctor association.
	 */
	public static VisitDto from(Visit v) {
		DoctorDto doctor = v.getDoctor() == null ? null : new DoctorDto(
				v.getDoctor().getId(),
				v.getDoctor().getName(),
				v.getDoctor().getQualification(),
				v.getDoctor().getRegistrationNo(),
				v.getDoctor().getDesignation(),
				v.getDoctor().getClinic());

		List<String> diagnoses = v.getDiagnoses().stream()
				.map(d -> d.getDescription())
				.toList();

		List<Medicine> medicines = v.getMedicines().stream()
				.map(m -> new Medicine(m.getId(), m.getName(), m.getDose(), m.getFrequency(),
						m.getDuration(), m.getInstructions()))
				.toList();

		List<LabResult> labs = v.getLabResults().stream()
				.map(l -> new LabResult(l.getId(), l.getTestName(), l.getValue(), l.getUnit(),
						l.getReferenceRange(), l.isAbnormal()))
				.toList();

		return new VisitDto(
				v.getId(),
				v.getVisitDate(),
				v.getVisitTime(),
				v.getValidUpTo(),
				v.getAppointmentNo(),
				v.getMode(),
				v.getFollowUpDate(),
				v.getNotes(),
				v.isVerified(),
				v.getScanFilePath() != null && !v.getScanFilePath().isBlank(),
				v.getBatchId(),
				v.getPageNo(),
				v.getPageCount(),
				v.getPatient() == null ? null : v.getPatient().getId(),
				v.getDoctor() == null ? null : v.getDoctor().getId(),
				doctor,
				diagnoses,
				medicines,
				labs,
				v.getCreatedAt(),
				v.getUpdatedAt());
	}
}
