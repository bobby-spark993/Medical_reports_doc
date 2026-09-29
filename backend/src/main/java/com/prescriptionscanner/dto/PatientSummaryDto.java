package com.prescriptionscanner.dto;

import java.time.LocalDate;

import com.prescriptionscanner.domain.Patient;

/** Compact patient view used by search results and match suggestions. */
public record PatientSummaryDto(
		Long id,
		String pid,
		String pidShort,
		String name,
		String gender,
		String age,
		LocalDate dateOfBirth,
		String phone,
		String address,
		long documentCount) {

	public static PatientSummaryDto from(Patient p, long documentCount) {
		return new PatientSummaryDto(
				p.getId(),
				p.getPid(),
				p.getPidShort(),
				p.getName(),
				p.getGender(),
				p.getAge(),
				p.getDateOfBirth(),
				p.getPhone(),
				p.getAddress(),
				documentCount);
	}
}
