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
	void possibleMatches_normalizesNameBeforeQuerying() {
		Patient candidate = new Patient();
		candidate.setId(3L);
		when(patientRepository.findPossibleMatches("sushila devi", "F", "43"))
				.thenReturn(List.of(candidate));

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
	void normalizeAge_keepsDigitsOnly() {
		assertThat(PatientMatchingService.normalizeAge("43")).isEqualTo("43");
		assertThat(PatientMatchingService.normalizeAge("43 years")).isEqualTo("43");
		assertThat(PatientMatchingService.normalizeAge("043")).isEqualTo("43");
		assertThat(PatientMatchingService.normalizeAge(null)).isEmpty();
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
	void matchByDemographics_requiresNameAgeAndGender() {
		assertThat(service.matchByDemographics("Sushila Devi", "  ", "F")).isEmpty();
		assertThat(service.matchByDemographics("Sushila Devi", "43", null)).isEmpty();
	}

	@Test
	void matchByDemographics_skipsDifferentAgeOrGender() {
		Patient other = new Patient();
		other.setId(6L);
		other.setName("Sushila Devi");
		other.setGender("M");
		other.setAge("43");
		when(patientRepository.findAllByNormalizedName("sushila devi")).thenReturn(List.of(other));

		assertThat(service.matchByDemographics("Sushila Devi", "43", "F")).isEmpty();
	}
}
