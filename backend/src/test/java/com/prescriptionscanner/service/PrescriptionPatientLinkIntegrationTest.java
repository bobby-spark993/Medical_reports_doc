package com.prescriptionscanner.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.prescriptionscanner.domain.Patient;
import com.prescriptionscanner.domain.Visit;
import com.prescriptionscanner.dto.VerifyRequest;
import com.prescriptionscanner.dto.VerifyResponse;
import com.prescriptionscanner.repository.PatientRepository;
import com.prescriptionscanner.repository.VisitRepository;

/**
 * The "one PID per name + age + gender, data saved date-wise" rule for the
 * /upload (prescription) flow, exercised through the real verify transaction.
 * The surrounding transaction rolls every insert back.
 */
@SpringBootTest
@Transactional
class PrescriptionPatientLinkIntegrationTest {

	@Autowired
	private PrescriptionService prescriptionService;

	@Autowired
	private PatientRepository patientRepository;

	@Autowired
	private VisitRepository visitRepository;

	private Patient existingPatient(String name) {
		Patient patient = new Patient();
		patient.setName(name);
		patient.setGender("Female");
		patient.setAge("43");
		patient.setPid("SNP-TEST-" + System.nanoTime());
		patient.setPidShort(PatientMatchingService.pidShortOf(patient.getPid()));
		return patientRepository.save(patient);
	}

	private Long draft() {
		Visit visit = new Visit();
		visit.setVerified(false);
		return visitRepository.save(visit).getId();
	}

	private VerifyRequest request(String name, String age, String gender, String visitDate, String visitTime) {
		return new VerifyRequest(
				new VerifyRequest.PatientInput(null, name, gender, age, null, null, null, null),
				new VerifyRequest.DoctorInput("Dr. Test", "MD", null, null, null),
				new VerifyRequest.VisitInput(visitDate, visitTime, null, null, null, null, null),
				List.of(), List.of(), List.of(), null);
	}

	@Test
	void verifyReusesThePatientMatchedByNameAgeGenderInsteadOfCreatingANewPid() {
		String name = "Demo Patient " + System.nanoTime();
		Patient existing = existingPatient(name);

		VerifyResponse response = prescriptionService.verify(
				draft(), request(name, "43 years", "F", "2026-04-04", "10:30 AM"), 1L);

		assertThat(response.patientCreated()).isFalse();
		assertThat(response.patientId()).isEqualTo(existing.getId());
		// Still exactly one PID for this name + age + gender.
		assertThat(patientRepository.findAllByNormalizedName(PatientMatchingService.normalizeName(name)))
				.hasSize(1);
	}

	@Test
	void verifySavesDifferentDatesAsSeparateRecordsUnderTheSamePatient() {
		String name = "Demo Patient " + System.nanoTime();
		Patient existing = existingPatient(name);

		VerifyResponse first = prescriptionService.verify(
				draft(), request(name, "43", "Female", "2026-04-04", "09:15 AM"), 1L);
		VerifyResponse second = prescriptionService.verify(
				draft(), request(name, "43", "Female", "2026-05-10", "04:45 PM"), 1L);

		assertThat(first.patientCreated()).isFalse();
		assertThat(second.patientCreated()).isFalse();
		assertThat(first.patientId()).isEqualTo(existing.getId());
		assertThat(second.patientId()).isEqualTo(existing.getId());

		List<Visit> visits = visitRepository
				.findByPatientIdAndIsVerifiedTrueOrderByVisitDateDescIdDesc(existing.getId());
		assertThat(visits).hasSize(2);
		// Newest date first, and the printed time is kept.
		assertThat(visits.get(0).getVisitDate()).isEqualTo(LocalDate.of(2026, 5, 10));
		assertThat(visits.get(0).getVisitTime()).isEqualTo("04:45 PM");
		assertThat(visits.get(1).getVisitDate()).isEqualTo(LocalDate.of(2026, 4, 4));
		assertThat(visits.get(1).getVisitTime()).isEqualTo("09:15 AM");
	}
}
