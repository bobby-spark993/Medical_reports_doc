package com.prescriptionscanner.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.prescriptionscanner.domain.Patient;
import com.prescriptionscanner.dto.MedicalDocumentDto;
import com.prescriptionscanner.dto.PatientDocumentDetail;
import com.prescriptionscanner.dto.PatientSummaryDto;
import com.prescriptionscanner.exception.ApiException;
import com.prescriptionscanner.repository.MedicalDocumentRepository;
import com.prescriptionscanner.repository.PatientRepository;

/** Patient search and document-centric patient lookup. */
@Service
public class PatientLookupService {

	private final PatientRepository patientRepository;
	private final MedicalDocumentRepository documentRepository;
	private final MedicalDocumentService medicalDocumentService;
	private final AuditService auditService;

	public PatientLookupService(PatientRepository patientRepository,
			MedicalDocumentRepository documentRepository,
			MedicalDocumentService medicalDocumentService,
			AuditService auditService) {
		this.patientRepository = patientRepository;
		this.documentRepository = documentRepository;
		this.medicalDocumentService = medicalDocumentService;
		this.auditService = auditService;
	}

	/** Partial case-insensitive name and/or exact-or-prefix pid match. */
	@Transactional
	public List<PatientSummaryDto> search(String name, String pid, Long actorId) {
		boolean hasName = !isBlank(name);
		boolean hasPid = !isBlank(pid);
		if (!hasName && !hasPid) {
			throw ApiException.badRequest("Provide a name and/or a PID to search.");
		}

		String normalizedName = hasName ? name.trim() : "";
		String normalizedPid = hasPid ? pid.trim() : "";
		List<Patient> patients = patientRepository.searchByNameAndPid(normalizedName, normalizedPid);
		auditService.view(actorId, "PatientSearch", null);

		return patients.stream()
				.map(p -> PatientSummaryDto.from(p, documentRepository.countByPatientId(p.getId())))
				.toList();
	}

	/** Full detail by the unique pid: patient info + all documents. */
	@Transactional
	public PatientDocumentDetail detailByPid(String pid, Long actorId) {
		if (isBlank(pid)) {
			throw ApiException.badRequest("A PID is required.");
		}
		Patient patient = patientRepository.findByPid(pid.trim())
				.orElseThrow(() -> ApiException.notFound("No patient with PID " + pid.trim() + "."));

		auditService.view(actorId, "Patient", patient.getId());

		List<MedicalDocumentDto> documents = medicalDocumentService.listForPatient(patient.getId());
		return new PatientDocumentDetail(true,
				PatientSummaryDto.from(patient, documents.size()),
				documents);
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
