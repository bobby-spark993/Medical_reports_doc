package com.prescriptionscanner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.prescriptionscanner.domain.ConfidenceLevel;
import com.prescriptionscanner.domain.DocumentLabResult;
import com.prescriptionscanner.domain.LabFlag;
import com.prescriptionscanner.domain.MedicalDocument;
import com.prescriptionscanner.domain.MedicalDocumentType;
import com.prescriptionscanner.domain.Patient;
import com.prescriptionscanner.domain.Prescription;
import com.prescriptionscanner.dto.ConfirmDocumentRequest;
import com.prescriptionscanner.dto.ConfirmDocumentResponse;
import com.prescriptionscanner.dto.MedicalExtraction;
import com.prescriptionscanner.dto.PatientDocumentDetail;
import com.prescriptionscanner.dto.PatientSummaryDto;
import com.prescriptionscanner.dto.ScanResponse;
import com.prescriptionscanner.repository.MedicalDocumentRepository;
import com.prescriptionscanner.repository.PatientRepository;

/**
 * End-to-end scan + confirm flow with Gemini and file storage mocked, using the
 * exact lab-report and prescription examples from the specification. The
 * surrounding transaction rolls every insert back.
 */
@SpringBootTest
@Transactional
class MedicalDocumentFlowIntegrationTest {

	/** PNG signature so StorageService's magic-byte sniffing accepts the upload. */
	private static final byte[] PNG_BYTES = new byte[] {
			(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 0, 0, 0, 0
	};

	@Autowired
	private MedicalDocumentService documentService;

	@Autowired
	private PatientLookupService patientLookupService;

	@Autowired
	private PatientRepository patientRepository;

	@Autowired
	private MedicalDocumentRepository documentRepository;

	@MockitoBean
	private GeminiService gemini;

	@MockitoBean
	private StorageService storage;

	private void stubStorage() {
		when(storage.maxBytes()).thenReturn(20L * 1024 * 1024);
		when(storage.isAllowedMime(anyString())).thenReturn(true);
		when(storage.save(any(byte[].class), anyString()))
				.thenReturn(new StorageService.StoredFile("stored.png", "storage/uploads/stored.png"));
	}

	private MockMultipartFile upload() {
		return new MockMultipartFile("files", "report.png", "image/png", PNG_BYTES);
	}

	// ── Example 1: the Samvedna lab report ───────────────────────────────────

	private MedicalExtraction labReportExtraction() {
		return new MedicalExtraction(
				"LAB_REPORT",
				new MedicalExtraction.PatientInfo(
						"SUSHILA DEVI", "071824", null, "F", "43", null, "BANA, GARHWA", "Married"),
				new MedicalExtraction.LabReportInfo(
						"Samvedna Diagnostic Centre", "20260404046", "2026-04-04", "2026-04-04", "Dr. U.K. Sinha"),
				List.of(
						new MedicalExtraction.LabResultItem(
								null, "Blood Sugar (Random)", "115.0", "mg/dl", "70-140", "NORMAL"),
						new MedicalExtraction.LabResultItem(
								"Complete Blood Count", "Haemoglobin", "10.5", "gm/dl", null, "LOW"),
						new MedicalExtraction.LabResultItem(
								"Complete Blood Count", "MCV", "79.7", "fL", null, "LOW"),
						new MedicalExtraction.LabResultItem(
								"Complete Blood Count", "RDW-CV", "16.4", "%", null, "HIGH"),
						new MedicalExtraction.LabResultItem(
								"Complete Blood Count", "P-LCC", "92", null, null, "HIGH")),
				null);
	}

	@Test
	void scanReturnsLabDraftWithoutSaving() {
		stubStorage();
		when(gemini.extractMedicalDocument(any(byte[].class), anyString()))
				.thenReturn(labReportExtraction());

		ScanResponse response = documentService.scan(List.of(upload()), 1L);

		assertThat(response.ok()).isTrue();
		assertThat(response.items()).hasSize(1);

		ScanResponse.ScanItem item = response.items().get(0);
		assertThat(item.extracted().documentType()).isEqualTo("LAB_REPORT");
		assertThat(item.extracted().patient().patientRefNo()).isEqualTo("071824");
		assertThat(item.extracted().patient().name()).isEqualTo("SUSHILA DEVI");
		assertThat(item.extracted().labReport().labReportId()).isEqualTo("20260404046");
		assertThat(item.extracted().results()).hasSize(5);

		// Nothing is persisted by a scan.
		assertThat(patientRepository.count()).isZero();
	}

	@Test
	void confirmCreatesPatientAndLabResultsWithFlags() {
		stubStorage();
		when(gemini.extractMedicalDocument(any(byte[].class), anyString()))
				.thenReturn(labReportExtraction());

		ScanResponse.ScanItem item = documentService.scan(List.of(upload()), 1L).items().get(0);

		ConfirmDocumentRequest request = new ConfirmDocumentRequest(
				"AUTO", null,
				new ConfirmDocumentRequest.PatientInput(
						null, "SUSHILA DEVI", "F", "43", null, "Married", null, "BANA, GARHWA", "071824"),
				"LAB_REPORT", item.originalFilePath(), item.originalFileName(),
				"2026-04-04", "2026-04-04", "Samvedna Diagnostic Centre", "20260404046", "Dr. U.K. Sinha",
				null,
				List.of(
						new ConfirmDocumentRequest.LabResultInput(
								"Complete Blood Count", "Haemoglobin", "10.5", "gm/dl", null, "LOW"),
						new ConfirmDocumentRequest.LabResultInput(
								"Complete Blood Count", "RDW-CV", "16.4", "%", null, "HIGH")),
				null);

		ConfirmDocumentResponse response = documentService.confirm(request, 1L);

		assertThat(response.ok()).isTrue();
		assertThat(response.patientCreated()).isTrue();

		Patient patient = patientRepository.findById(response.patientId()).orElseThrow();
		assertThat(patient.getPidShort()).isEqualTo("071824");
		assertThat(patient.getName()).isEqualTo("SUSHILA DEVI");

		List<MedicalDocument> documents = documentRepository
				.findByPatientIdOrderByDocumentDateDescIdDesc(patient.getId());
		assertThat(documents).hasSize(1);

		MedicalDocument document = documents.get(0);
		assertThat(document.getDocumentType()).isEqualTo(MedicalDocumentType.LAB_REPORT);
		assertThat(document.getLabReportId()).isEqualTo("20260404046");
		assertThat(document.isReviewedByUser()).isTrue();
		assertThat(document.getResults())
				.extracting(DocumentLabResult::getFlag)
				.containsExactlyInAnyOrder(LabFlag.LOW, LabFlag.HIGH);
	}

	// ── Example 2: the neuropsychiatrist prescription ────────────────────────

	private MedicalExtraction prescriptionExtraction() {
		return new MedicalExtraction(
				"PRESCRIPTION",
				new MedicalExtraction.PatientInfo(
						"SUSHILA DEVI", null, "SNP260404071824", "Female", "43", null, "BANA, GARHWA", "Married"),
				null, null,
				new MedicalExtraction.PrescriptionInfo(
						"Dr. Uday Kr. Sinha", "Consultant Neuropsychiatrist", "Sinha Clinic", "12345",
						"2026-04-04", "67", "2026-04-24", "2027-04-03", "Sleep disturbance",
						List.of(new MedicalExtraction.MedicineItem(
								"Clonazepam", "0.5 mg", "0-0-1", "10 days", "after food")),
						"Review after 2 weeks", "Rx illegible handwriting", "LOW"));
	}

	@Test
	void confirmSavesPrescriptionAndFullDetailReturnsIt() {
		stubStorage();
		when(gemini.extractMedicalDocument(any(byte[].class), anyString()))
				.thenReturn(prescriptionExtraction());

		ScanResponse.ScanItem item = documentService.scan(List.of(upload()), 1L).items().get(0);

		documentService.confirm(new ConfirmDocumentRequest(
				"CREATE_NEW", null,
				new ConfirmDocumentRequest.PatientInput(
						"SNP260404071824", "SUSHILA DEVI", "Female", "43", null, "Married", null,
						"BANA, GARHWA", null),
				"PRESCRIPTION", item.originalFilePath(), item.originalFileName(),
				"2026-04-04", null, null, null, null, null, null,
				new ConfirmDocumentRequest.PrescriptionInput(
						"Dr. Uday Kr. Sinha", "Consultant Neuropsychiatrist", "Sinha Clinic", "12345",
						"2026-04-04", "67", "2026-04-24", "2027-04-03", "Sleep disturbance",
						List.of(new ConfirmDocumentRequest.MedicineInput(
								"Clonazepam", "0.5 mg", "0-0-1", "10 days", "after food")),
						"Review after 2 weeks", "Rx illegible handwriting", "LOW")), 1L);

		PatientDocumentDetail detail = patientLookupService.detailByPid("SNP260404071824", 1L);

		assertThat(detail.documents()).hasSize(1);
		assertThat(detail.patient().pid()).isEqualTo("SNP260404071824");
		assertThat(detail.patient().pidShort()).isEqualTo("071824");

		var document = detail.documents().get(0);
		assertThat(document.documentType()).isEqualTo("PRESCRIPTION");
		assertThat(document.prescription()).isNotNull();
		assertThat(document.prescription().doctorName()).isEqualTo("Dr. Uday Kr. Sinha");
		assertThat(document.prescription().confidence()).isEqualTo(ConfidenceLevel.LOW.name());
		assertThat(document.prescription().medicines())
				.singleElement()
				.satisfies(medicine -> assertThat(medicine.name()).isEqualTo("Clonazepam"));
	}

	// ── Matching rules ───────────────────────────────────────────────────────

	@Test
	void scanAutoLinksWhenPidShortMatchesExistingPatient() {
		stubStorage();
		Patient existing = new Patient();
		existing.setPid("SNP260404071824");
		existing.setPidShort("071824");
		existing.setName("SUSHILA DEVI");
		existing.setGender("F");
		existing.setAge("43");
		patientRepository.save(existing);

		when(gemini.extractMedicalDocument(any(byte[].class), anyString()))
				.thenReturn(labReportExtraction());

		ScanResponse.ScanItem item = documentService.scan(List.of(upload()), 1L).items().get(0);

		assertThat(item.matchedPatientId()).isEqualTo(existing.getId());
		assertThat(item.possibleMatches()).isEmpty();
	}

	@Test
	void scanNeverAutoMergesOnDemographicsButReturnsPossibleMatches() {
		stubStorage();
		Patient sameName = new Patient();
		sameName.setName("SUSHILA DEVI");
		sameName.setGender("F");
		sameName.setAge("43");
		patientRepository.save(sameName);

		when(gemini.extractMedicalDocument(any(byte[].class), anyString()))
				.thenReturn(labReportExtraction());

		ScanResponse.ScanItem item = documentService.scan(List.of(upload()), 1L).items().get(0);

		assertThat(item.matchedPatientId()).isNull();
		assertThat(item.possibleMatches())
				.extracting(PatientSummaryDto::id)
				.containsExactly(sameName.getId());
	}
}
