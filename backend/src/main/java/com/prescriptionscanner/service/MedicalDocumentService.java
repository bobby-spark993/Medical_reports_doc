package com.prescriptionscanner.service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.prescriptionscanner.domain.ConfidenceLevel;
import com.prescriptionscanner.domain.DocumentLabResult;
import com.prescriptionscanner.domain.LabFlag;
import com.prescriptionscanner.domain.MedicalDocument;
import com.prescriptionscanner.domain.MedicalDocumentType;
import com.prescriptionscanner.domain.Patient;
import com.prescriptionscanner.domain.Prescription;
import com.prescriptionscanner.dto.ConfirmDocumentRequest;
import com.prescriptionscanner.dto.ConfirmDocumentResponse;
import com.prescriptionscanner.dto.MedicalDocumentDto;
import com.prescriptionscanner.dto.MedicalExtraction;
import com.prescriptionscanner.dto.PatientSummaryDto;
import com.prescriptionscanner.dto.ScanResponse;
import com.prescriptionscanner.exception.ApiException;
import com.prescriptionscanner.repository.MedicalDocumentRepository;
import com.prescriptionscanner.repository.PatientRepository;

/**
 * Orchestrates the two-step medical-document flow: scan (extract only, nothing
 * saved) and confirm (persist patient + document in one transaction).
 */
@Service
public class MedicalDocumentService {

	private static final Logger log = LoggerFactory.getLogger(MedicalDocumentService.class);

	private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("d/M/yyyy");
	private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyy/M/d");
	private static final int MAX_FILES_PER_SCAN = 5;

	private final StorageService storage;
	private final GeminiService gemini;
	private final PatientRepository patientRepository;
	private final PatientMatchingService matching;
	private final MedicalDocumentRepository documentRepository;
	private final AuditService auditService;
	private final ObjectMapper mapper;

	public MedicalDocumentService(StorageService storage, GeminiService gemini,
			PatientRepository patientRepository, PatientMatchingService matching,
			MedicalDocumentRepository documentRepository, AuditService auditService, ObjectMapper mapper) {
		this.storage = storage;
		this.gemini = gemini;
		this.patientRepository = patientRepository;
		this.matching = matching;
		this.documentRepository = documentRepository;
		this.auditService = auditService;
		this.mapper = mapper;
	}

	// ── Scan (extract only) ──────────────────────────────────────────────────

	public ScanResponse scan(List<MultipartFile> files, Long actorId) {
		if (files == null || files.isEmpty() || files.stream().allMatch(MultipartFile::isEmpty)) {
			throw ApiException.badRequest("Upload at least one file.");
		}
		if (files.size() > MAX_FILES_PER_SCAN) {
			throw ApiException.badRequest("Upload at most " + MAX_FILES_PER_SCAN + " files at a time.");
		}

		List<ScanResponse.ScanItem> items = new ArrayList<>();
		for (MultipartFile file : files) {
			if (file.isEmpty()) {
				continue;
			}
			items.add(scanOne(file));
		}

		auditService.record(actorId, AuditService.VIEW, "MedicalDocumentScan", null);
		return new ScanResponse(true, items);
	}

	private ScanResponse.ScanItem scanOne(MultipartFile file) {
		byte[] bytes;
		try {
			bytes = file.getBytes();
		} catch (IOException ex) {
			throw ApiException.badRequest("Could not read the uploaded file.");
		}

		if (bytes.length > storage.maxBytes()) {
			throw new ApiException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,
					"That file is larger than the allowed upload size.");
		}
		if (!storage.isAllowedMime(file.getContentType())) {
			throw ApiException.badRequest("Unsupported file type. Use JPG, PNG, WebP or PDF.");
		}
		String sniffed = StorageService.sniffMime(bytes);
		if (sniffed == null) {
			throw ApiException.badRequest("That file does not look like a JPG, PNG, WebP or PDF.");
		}

		StorageService.StoredFile stored = storage.save(bytes, sniffed);

		MedicalExtraction extracted;
		try {
			extracted = gemini.extractMedicalDocument(bytes, sniffed);
		} catch (RuntimeException ex) {
			// Do not leave an orphan file behind when extraction fails.
			storage.delete(stored.relativePath());
			throw ex;
		}

		// Never log patient data, only the processing outcome.
		log.info("Extracted medical document: type={} mime={} bytes={}",
				extracted.documentType(), sniffed, bytes.length);

		Long matchedPatientId = null;
		String matchedPatientName = null;
		List<PatientSummaryDto> possibleMatches = List.of();

		MedicalExtraction.PatientInfo info = extracted.patient();
		if (info != null) {
			Optional<Patient> match = matchPatient(info);
			if (match.isPresent()) {
				Patient p = match.get();
				matchedPatientId = p.getId();
				matchedPatientName = p.getName();
			} else {
				possibleMatches = matching.possibleMatches(info.name(), info.gender(), info.age())
						.stream()
						.map(p -> PatientSummaryDto.from(p, documentRepository.countByPatientId(p.getId())))
						.toList();
			}
		}

		return new ScanResponse.ScanItem(extracted, stored.relativePath(),
				file.getOriginalFilename(), matchedPatientId, matchedPatientName, possibleMatches);
	}

	/**
	 * Auto-link is deliberately restricted to identifiers printed on the
	 * document: the exact pid, or the 6-digit number printed next to the name on
	 * lab reports. Name + age + gender are never auto-merged - a collision is
	 * returned as {@code possibleMatches} for the user to resolve.
	 */
	private Optional<Patient> matchPatient(MedicalExtraction.PatientInfo info) {
		Optional<Patient> byPid = matching.matchByPid(info.pid());
		if (byPid.isPresent()) {
			return byPid;
		}
		return matching.matchByPidShort(info.patientRefNo());
	}

	// ── Confirm (persist) ────────────────────────────────────────────────────

	@Transactional
	public ConfirmDocumentResponse confirm(ConfirmDocumentRequest req, Long actorId) {
		if (req == null) {
			throw ApiException.badRequest("Missing request body.");
		}

		PatientResolution resolution = resolvePatient(req, actorId);
		Patient patient = resolution.patient();

		MedicalDocument doc = new MedicalDocument();
		doc.setPatient(patient);
		doc.setDocumentType(MedicalDocumentType.from(req.documentType()));
		doc.setOriginalFilePath(trim(req.originalFilePath()));
		doc.setOriginalFileName(trim(req.originalFileName()));
		doc.setDocumentDate(parseDate(req.documentDate(), "documentDate"));
		doc.setReportedDate(parseDate(req.reportedDate(), "reportedDate"));
		doc.setLabName(trim(req.labName()));
		doc.setLabReportId(trim(req.labReportId()));
		doc.setReferredBy(trim(req.referredBy()));
		doc.setRawExtractedJson(writeJson(req.rawExtractedJson()));
		doc.setReviewedByUser(true);
		doc.setReviewedBy(actorId);

		if (doc.getDocumentType() == MedicalDocumentType.LAB_REPORT && req.results() != null) {
			for (ConfirmDocumentRequest.LabResultInput in : req.results()) {
				if (isBlank(in.testName())) {
					continue;
				}
				DocumentLabResult result = new DocumentLabResult();
				result.setTestGroup(trim(in.testGroup()));
				result.setTestName(trim(in.testName()));
				result.setResultValue(trim(in.resultValue()));
				result.setUnit(trim(in.unit()));
				result.setReferenceRange(trim(in.referenceRange()));
				result.setFlag(LabFlag.from(in.flag()));
				doc.addResult(result);
			}
		}

		if (doc.getDocumentType() == MedicalDocumentType.PRESCRIPTION && req.prescription() != null) {
			doc.setPrescription(buildPrescription(req.prescription()));
		}

		MedicalDocument saved = documentRepository.save(doc);
		auditService.record(actorId, AuditService.CREATE, "MedicalDocument", saved.getId());

		return new ConfirmDocumentResponse(true, saved.getId(), patient.getId(),
				resolution.created(), patient.getPid(), "Document saved.");
	}

	private record PatientResolution(Patient patient, boolean created) {
	}

	private PatientResolution resolvePatient(ConfirmDocumentRequest req, Long actorId) {
		String action = req.patientAction() == null ? "AUTO" : req.patientAction().trim().toUpperCase();
		ConfirmDocumentRequest.PatientInput input = req.patient();

		return switch (action) {
			case "LINK_EXISTING" -> {
				if (req.patientId() == null) {
					throw ApiException.badRequest("patientId is required when linking to an existing patient.");
				}
				Patient patient = patientRepository.findById(req.patientId())
						.orElseThrow(() -> ApiException.notFound("No such patient."));
				if (fillMissingFields(patient, input)) {
					patientRepository.save(patient);
				}
				yield new PatientResolution(patient, false);
			}
			case "CREATE_NEW" -> createPatient(input, actorId);
			case "AUTO" -> {
				String pid = input == null ? null : trim(input.pid());
				String refNo = input == null ? null : trim(input.patientRefNo());
				Optional<Patient> match = matching.matchByPid(pid);
				if (match.isEmpty()) {
					match = matching.matchByPidShort(refNo);
				}
				if (match.isPresent()) {
					Patient patient = match.get();
					if (fillMissingFields(patient, input)) {
						patientRepository.save(patient);
					}
					yield new PatientResolution(patient, false);
				}
				yield createPatient(input, actorId);
			}
			default -> throw ApiException.badRequest("Unknown patientAction: " + action);
		};
	}

	private PatientResolution createPatient(ConfirmDocumentRequest.PatientInput input, Long actorId) {
		String pid = input == null ? null : trim(input.pid());
		if (pid != null && patientRepository.findByPid(pid).isPresent()) {
			throw ApiException.conflict(
					"A patient with this PID already exists. Link the document to that patient instead.");
		}
		Patient patient = new Patient();
		applyFields(patient, input);
		patientRepository.save(patient);
		auditService.record(actorId, AuditService.CREATE, "Patient", patient.getId());
		return new PatientResolution(patient, true);
	}

	private static void applyFields(Patient patient, ConfirmDocumentRequest.PatientInput in) {
		patient.setName(in != null && notBlank(in.name()) ? in.name().trim() : "Unknown");
		if (in == null) {
			return;
		}
		patient.setPid(trim(in.pid()));
		// Lab reports never print the full pid, only the 6-digit ref number; keep
		// it in pidShort so future reports link to this patient automatically.
		String pidShort = PatientMatchingService.pidShortOf(in.pid());
		if (pidShort == null) {
			pidShort = PatientMatchingService.pidShortOf(in.patientRefNo());
		}
		patient.setPidShort(pidShort);
		patient.setGender(trim(in.gender()));
		patient.setAge(trim(in.age()));
		patient.setDateOfBirth(parseDateLenient(in.dateOfBirth()));
		patient.setMaritalStatus(trim(in.maritalStatus()));
		patient.setPhone(trim(in.phone()));
		patient.setAddress(trim(in.address()));
	}

	/** Fills blanks only; existing non-empty values are never overwritten. */
	private static boolean fillMissingFields(Patient patient, ConfirmDocumentRequest.PatientInput in) {
		if (in == null) {
			return false;
		}
		boolean changed = false;
		if (isBlank(patient.getName()) && notBlank(in.name())) {
			patient.setName(in.name().trim());
			changed = true;
		}
		if (isBlank(patient.getPid()) && notBlank(in.pid())) {
			patient.setPid(in.pid().trim());
			changed = true;
		}
		if (isBlank(patient.getPidShort())) {
			String pidShort = notBlank(patient.getPid())
					? PatientMatchingService.pidShortOf(patient.getPid())
					: PatientMatchingService.pidShortOf(in.patientRefNo());
			if (pidShort != null) {
				patient.setPidShort(pidShort);
				changed = true;
			}
		}
		if (isBlank(patient.getGender()) && notBlank(in.gender())) {
			patient.setGender(in.gender().trim());
			changed = true;
		}
		if (isBlank(patient.getAge()) && notBlank(in.age())) {
			patient.setAge(in.age().trim());
			changed = true;
		}
		if (patient.getDateOfBirth() == null && notBlank(in.dateOfBirth())) {
			LocalDate dob = parseDateLenient(in.dateOfBirth());
			if (dob != null) {
				patient.setDateOfBirth(dob);
				changed = true;
			}
		}
		if (isBlank(patient.getMaritalStatus()) && notBlank(in.maritalStatus())) {
			patient.setMaritalStatus(in.maritalStatus().trim());
			changed = true;
		}
		if (isBlank(patient.getPhone()) && notBlank(in.phone())) {
			patient.setPhone(in.phone().trim());
			changed = true;
		}
		if (isBlank(patient.getAddress()) && notBlank(in.address())) {
			patient.setAddress(in.address().trim());
			changed = true;
		}
		return changed;
	}

	private Prescription buildPrescription(ConfirmDocumentRequest.PrescriptionInput in) {
		Prescription pres = new Prescription();
		pres.setDoctorName(trim(in.doctorName()));
		pres.setDoctorQualification(trim(in.doctorQualification()));
		pres.setClinicName(trim(in.clinicName()));
		pres.setRegistrationNo(trim(in.registrationNo()));
		pres.setAppointmentDate(parseDate(in.appointmentDate(), "appointmentDate"));
		pres.setAppointmentNo(trim(in.appointmentNo()));
		pres.setValidUpTo(parseDate(in.validUpTo(), "validUpTo"));
		pres.setPatientRegdValidUpTo(parseDate(in.patientRegdValidUpTo(), "patientRegdValidUpTo"));
		pres.setDiagnosisOrComplaints(trim(in.diagnosisOrComplaints()));
		pres.setMedicinesJson(writeJson(mapper.valueToTree(in.medicines() == null ? List.of() : in.medicines())));
		pres.setAdvice(trim(in.advice()));
		pres.setHandwrittenRawText(trim(in.handwrittenRawText()));
		pres.setConfidence(ConfidenceLevel.from(in.confidence()));
		return pres;
	}

	// ── Reads ────────────────────────────────────────────────────────────────

	@Transactional(readOnly = true)
	public List<MedicalDocumentDto> listForPatient(Long patientId) {
		return documentRepository.findByPatientIdOrderByDocumentDateDescIdDesc(patientId)
				.stream()
				.map(this::toDto)
				.toList();
	}

	@Transactional(readOnly = true)
	public StorageService.LoadedFile loadFile(Long documentId) {
		MedicalDocument doc = documentRepository.findById(documentId)
				.orElseThrow(() -> ApiException.notFound("No such document."));
		if (isBlank(doc.getOriginalFilePath())) {
			throw ApiException.notFound("This document has no stored file.");
		}
		return storage.load(doc.getOriginalFilePath())
				.orElseThrow(() -> ApiException.notFound("The stored file is missing from storage."));
	}

	public MedicalDocumentDto toDto(MedicalDocument doc) {
		List<MedicalDocumentDto.LabResultDto> results = doc.getResults().stream()
				.map(r -> new MedicalDocumentDto.LabResultDto(r.getId(), r.getTestGroup(), r.getTestName(),
						r.getResultValue(), r.getUnit(), r.getReferenceRange(),
						r.getFlag() == null ? null : r.getFlag().name()))
				.toList();

		MedicalDocumentDto.PrescriptionDto prescription = doc.getPrescription() == null
				? null
				: toPrescriptionDto(doc.getPrescription());

		return new MedicalDocumentDto(
				doc.getId(),
				doc.getDocumentType() == null ? null : doc.getDocumentType().name(),
				doc.getOriginalFileName(),
				doc.getOriginalFilePath(),
				doc.getDocumentDate(),
				doc.getReportedDate(),
				doc.getLabName(),
				doc.getLabReportId(),
				doc.getReferredBy(),
				doc.isReviewedByUser(),
				doc.getCreatedAt(),
				readJson(doc.getRawExtractedJson()),
				results,
				prescription);
	}

	private JsonNode readJson(String json) {
		if (isBlank(json)) {
			return null;
		}
		try {
			return mapper.readTree(json);
		} catch (Exception ex) {
			log.warn("Could not parse stored raw JSON: {}", ex.getMessage());
			return null;
		}
	}

	private MedicalDocumentDto.PrescriptionDto toPrescriptionDto(Prescription pres) {
		List<MedicalDocumentDto.MedicineDto> medicines = List.of();
		if (notBlank(pres.getMedicinesJson())) {
			try {
				medicines = mapper.readValue(pres.getMedicinesJson(),
						mapper.getTypeFactory().constructCollectionType(List.class,
								MedicalDocumentDto.MedicineDto.class));
			} catch (Exception ex) {
				log.warn("Could not parse stored medicines JSON for prescription {}: {}",
						pres.getId(), ex.getMessage());
			}
		}
		return new MedicalDocumentDto.PrescriptionDto(
				pres.getId(),
				pres.getDoctorName(),
				pres.getDoctorQualification(),
				pres.getClinicName(),
				pres.getRegistrationNo(),
				pres.getAppointmentDate(),
				pres.getAppointmentNo(),
				pres.getValidUpTo(),
				pres.getPatientRegdValidUpTo(),
				pres.getDiagnosisOrComplaints(),
				medicines,
				pres.getAdvice(),
				pres.getHandwrittenRawText(),
				pres.getConfidence() == null ? null : pres.getConfidence().name());
	}

	// ── Helpers ──────────────────────────────────────────────────────────────

	private String writeJson(Object value) {
		if (value == null) {
			return null;
		}
		try {
			return mapper.writeValueAsString(value);
		} catch (Exception ex) {
			log.warn("Could not serialise JSON for storage: {}", ex.getMessage());
			return null;
		}
	}

	private static LocalDate parseDate(String raw, String field) {
		LocalDate parsed = parseDateLenient(raw);
		if (parsed == null && notBlank(raw)) {
			throw ApiException.badRequest("Invalid date: " + field + " (expected yyyy-MM-dd or dd/MM/yyyy).",
					List.of(new com.prescriptionscanner.dto.ApiError.FieldIssue(field,
							"Expected yyyy-MM-dd or dd/MM/yyyy")));
		}
		return parsed;
	}

	private static LocalDate parseDateLenient(String raw) {
		if (isBlank(raw)) {
			return null;
		}
		String value = raw.trim();
		for (DateTimeFormatter formatter : List.of(DateTimeFormatter.ISO_LOCAL_DATE, DMY, YMD)) {
			try {
				return LocalDate.parse(value, formatter);
			} catch (DateTimeParseException ignored) {
				// try the next format
			}
		}
		return null;
	}

	private static String trim(String value) {
		return isBlank(value) ? null : value.trim();
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static boolean notBlank(String value) {
		return !isBlank(value);
	}
}
