package com.prescriptionscanner.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.prescriptionscanner.domain.Patient;

/** GET /api/patients/{id}: visits (legacy) + medical documents (new). */
public record PatientDetailResponse(
		boolean ok,
		PatientDto patient,
		List<VisitDto> visits,
		List<AppointmentDto> appointments,
		List<FollowUpDto> followUps,
		List<MedicalDocumentDto> documents) {

	public record PatientDto(
			Long id,
			String pid,
			String pidShort,
			String name,
			String gender,
			String age,
			LocalDate dateOfBirth,
			String maritalStatus,
			String phone,
			String address,
			String allergies,
			Instant createdAt,
			Instant updatedAt) {

		public static PatientDto from(Patient p) {
			return new PatientDto(p.getId(), p.getPid(), p.getPidShort(), p.getName(), p.getGender(),
					p.getAge(), p.getDateOfBirth(), p.getMaritalStatus(), p.getPhone(), p.getAddress(),
					p.getAllergies(), p.getCreatedAt(), p.getUpdatedAt());
		}
	}

	public record AppointmentDto(
			Long id,
			LocalDateTime scheduledAt,
			String status,
			String notes,
			String doctorName,
			boolean isUpcoming) {
	}

	public record FollowUpDto(Long visitId, LocalDate followUpDate) {
	}

	public static PatientDetailResponse of(PatientDto patient, List<VisitDto> visits,
			List<AppointmentDto> appointments, List<FollowUpDto> followUps,
			List<MedicalDocumentDto> documents) {
		return new PatientDetailResponse(true, patient, visits, appointments, followUps, documents);
	}
}
