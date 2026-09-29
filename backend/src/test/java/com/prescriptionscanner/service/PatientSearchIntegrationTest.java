package com.prescriptionscanner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.prescriptionscanner.domain.Patient;
import com.prescriptionscanner.dto.PatientSummaryDto;
import com.prescriptionscanner.exception.ApiException;
import com.prescriptionscanner.repository.PatientRepository;

/**
 * Integration test for patient search against the real persistence layer. The
 * surrounding transaction rolls every insert back.
 */
@SpringBootTest
@Transactional
class PatientSearchIntegrationTest {

	@Autowired
	private PatientLookupService lookupService;

	@Autowired
	private PatientRepository patientRepository;

	private Patient newPatient(String pid, String name, String gender, String age) {
		Patient patient = new Patient();
		patient.setPid(pid);
		patient.setPidShort(PatientMatchingService.pidShortOf(pid));
		patient.setName(name);
		patient.setGender(gender);
		patient.setAge(age);
		return patientRepository.save(patient);
	}

	@Test
	void searchByNameIsPartialAndCaseInsensitive() {
		newPatient("SNP260404071824", "SUSHILA DEVI", "F", "43");

		List<PatientSummaryDto> results = lookupService.search("sushila", null, 1L);

		assertThat(results).extracting(PatientSummaryDto::pid).contains("SNP260404071824");
	}

	@Test
	void searchByPidIsPrefixMatch() {
		newPatient("SNP260404071824", "SUSHILA DEVI", "F", "43");

		List<PatientSummaryDto> results = lookupService.search(null, "SNP2604", 1L);

		assertThat(results).extracting(PatientSummaryDto::pid).contains("SNP260404071824");
	}

	@Test
	void searchByNameAndPidCombines() {
		newPatient("SNP260404071824", "SUSHILA DEVI", "F", "43");

		List<PatientSummaryDto> results = lookupService.search("sushila", "SNP260404071824", 1L);

		assertThat(results).hasSize(1);
		assertThat(results.get(0).name()).isEqualTo("SUSHILA DEVI");
	}

	@Test
	void searchRequiresNameOrPid() {
		assertThatThrownBy(() -> lookupService.search("  ", "  ", 1L))
				.isInstanceOf(ApiException.class);
	}
}
