package com.prescriptionscanner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.prescriptionscanner.domain.Patient;
import com.prescriptionscanner.repository.PatientRepository;

/** Unit tests for the patient-matching rules (no database). */
@ExtendWith(MockitoExtension.class)
class PatientMatchingServiceTest {

	@Mock
	private PatientRepository patientRepository;

	@InjectMocks
	private PatientMatchingService service;

	@Test
	void normalizeName_trimsCollapsesAndLowercases() {
		assertThat(PatientMatchingService.normalizeName("  SUSHILA   DEVI ")).isEqualTo("sushila devi");
		assertThat(PatientMatchingService.normalizeName(null)).isEmpty();
	}

	@Test
	void pidShortOf_takesTheLastSixDigits() {
		assertThat(PatientMatchingService.pidShortOf("SNP260404071824")).isEqualTo("071824");
		assertThat(PatientMatchingService.pidShortOf("071824")).isEqualTo("071824");
		assertThat(PatientMatchingService.pidShortOf("SUSHILA DEVI 071824")).isEqualTo("071824");
		assertThat(PatientMatchingService.pidShortOf("no digits here")).isNull();
		assertThat(PatientMatchingService.pidShortOf(null)).isNull();
	}

	@Test
	void matchByPid_returnsThePatient() {
		Patient patient = new Patient();
		patient.setId(1L);
		patient.setPid("SNP260404071824");
		when(patientRepository.findByPid("SNP260404071824")).thenReturn(Optional.of(patient));

		assertThat(service.matchByPid("SNP260404071824")).contains(patient);
	}

	@Test
	void matchByPid_blankReturnsEmptyWithoutQuerying() {
		assertThat(service.matchByPid("  ")).isEmpty();
	}

	@Test
	void matchByPidShort_extractsTheTrailingNumber() {
		Patient patient = new Patient();
		patient.setId(2L);
		patient.setPidShort("071824");
		when(patientRepository.findFirstByPidShort("071824")).thenReturn(Optional.of(patient));

		assertThat(service.matchByPidShort("SUSHILA DEVI 071824")).contains(patient);
	}

	@Test
	void possibleMatches_normalizesNameGenderAndAge() {
		Patient candidate = new Patient();
		candidate.setId(3L);
		candidate.setName("SUSHILA DEVI");
		candidate.setGender("Female");
		candidate.setAge("43 years");
		Patient different = new Patient();
		different.setId(4L);
		different.setName("Sushila Devi");
		different.setGender("M");
		different.setAge("43");
		when(patientRepository.findAllByNormalizedName("sushila devi"))
				.thenReturn(List.of(candidate, different));

		assertThat(service.possibleMatches("  SUSHILA   DEVI ", " F ", "43"))
				.containsExactly(candidate);
	}

	@Test
	void possibleMatches_blankNameReturnsEmpty() {
		assertThat(service.possibleMatches("   ", "F", "43")).isEmpty();
	}

	@Test
	void normalizeGender_mapsToTheFirstLetter() {
		assertThat(PatientMatchingService.normalizeGender("Female")).isEqualTo("f");
		assertThat(PatientMatchingService.normalizeGender("F")).isEqualTo("f");
		assertThat(PatientMatchingService.normalizeGender(" MALE ")).isEqualTo("m");
		assertThat(PatientMatchingService.normalizeGender(null)).isEmpty();
	}

	@Test
	void normalizeAge_keepsFirstNumber() {
		assertThat(PatientMatchingService.normalizeAge("43")).isEqualTo("43");
		assertThat(PatientMatchingService.normalizeAge("43 years")).isEqualTo("43");
		assertThat(PatientMatchingService.normalizeAge("043")).isEqualTo("43");
		assertThat(PatientMatchingService.normalizeAge("26 years 6 months")).isEqualTo("26");
		assertThat(PatientMatchingService.normalizeAge(null)).isEmpty();
	}

	@Test
	void matchByDemographics_toleratesFemaleVsF_and_yearsSuffix() {
		Patient candidate = new Patient();
		candidate.setId(7L);
		candidate.setName("Sneha Kumari");
		candidate.setGender("Female");
		candidate.setAge("26 years");
		when(patientRepository.findAllByNormalizedName("sneha kumari")).thenReturn(List.of(candidate));

		assertThat(service.matchByDemographics("SNEHA KUMARI", "26", "F")).contains(candidate);
	}

	@Test
	void matchByDemographics_matchesAfterNormalisingNameAgeGender() {
		Patient candidate = new Patient();
		candidate.setId(5L);
		candidate.setName("Sushila Devi");
		candidate.setGender("Female");
		candidate.setAge("43");
		when(patientRepository.findAllByNormalizedName("sushila devi")).thenReturn(List.of(candidate));

		assertThat(service.matchByDemographics("  SUSHILA   DEVI ", "43 years", "F")).contains(candidate);
	}

	@Test
	void matchByDemographics_requiresNameAndAge() {
		assertThat(service.matchByDemographics("Sushila Devi", "  ", "F")).isEmpty();
		assertThat(service.matchByDemographics("   ", "43", "F")).isEmpty();
	}

	@Test
	void matchByDemographics_skipsDifferentAge() {
		Patient other = new Patient();
		other.setId(6L);
		other.setName("Sushila Devi");
		other.setGender("F");
		other.setAge("44");
		when(patientRepository.findAllByNormalizedName("sushila devi")).thenReturn(List.of(other));

		assertThat(service.matchByDemographics("Sushila Devi", "43", "F")).isEmpty();
	}

	@Test
	void matchByDemographics_matchesByNameAndAge_evenWhenGenderDiffers() {
		Patient other = new Patient();
		other.setId(8L);
		other.setName("Sushila Devi");
		other.setGender("M");
		other.setAge("43");
		when(patientRepository.findAllByNormalizedName("sushila devi")).thenReturn(List.of(other));

		assertThat(service.matchByDemographics("Sushila Devi", "43", "F")).contains(other);
	}

	@Test
	void matchByDemographics_prefersGenderWhenSeveralShareNameAndAge() {
		Patient wrongGender = new Patient();
		wrongGender.setId(9L);
		wrongGender.setName("Sushila Devi");
		wrongGender.setGender("M");
		wrongGender.setAge("43");
		Patient rightGender = new Patient();
		rightGender.setId(10L);
		rightGender.setName("Sushila Devi");
		rightGender.setGender("Female");
		rightGender.setAge("43 years");
		when(patientRepository.findAllByNormalizedName("sushila devi"))
				.thenReturn(List.of(wrongGender, rightGender));

		assertThat(service.matchByDemographics("Sushila Devi", "43", "F")).contains(rightGender);
	}

	@Test
	void matchByDemographics_canMatchWithoutAge_usingGender() {
		Patient candidate = new Patient();
		candidate.setId(11L);
		candidate.setName("Sushila Devi");
		candidate.setGender("Female");
		candidate.setAge(null);
		when(patientRepository.findAllByNormalizedName("sushila devi")).thenReturn(List.of(candidate));

		assertThat(service.matchByDemographics("Sushila Devi", null, "F")).contains(candidate);
	}
}
