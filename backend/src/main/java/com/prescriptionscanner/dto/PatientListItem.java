package com.prescriptionscanner.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.prescriptionscanner.domain.Patient;

/** Row shape for the patient list. */
public record PatientListItem(
		Long id,
		String pid,
		String name,
		String gender,
		String age,
		String phone,
		String address,
		long visitCount,
		LocalDate nextFollowUp,
		Instant updatedAt) {

	public static PatientListItem from(Patient p, long visitCount, LocalDate nextFollowUp) {
		return new PatientListItem(
				p.getId(),
				p.getPid(),
				p.getName(),
				p.getGender(),
				p.getAge(),
				p.getPhone(),
				p.getAddress(),
				visitCount,
				nextFollowUp,
				p.getUpdatedAt());
	}
}
