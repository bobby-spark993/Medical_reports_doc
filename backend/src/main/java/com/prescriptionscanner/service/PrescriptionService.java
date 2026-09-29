package com.prescriptionscanner.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.prescriptionscanner.config.AppProperties;
import com.prescriptionscanner.domain.Diagnosis;
import com.prescriptionscanner.domain.Doctor;
import com.prescriptionscanner.domain.LabResult;
import com.prescriptionscanner.domain.Patient;
import com.prescriptionscanner.domain.PrescribedMedicine;
import com.prescriptionscanner.domain.Visit;
import com.prescriptionscanner.dto.ApiError;
import com.prescriptionscanner.dto.GeminiExtraction;
import com.prescriptionscanner.dto.PatientSummaryDto;
import com.prescriptionscanner.dto.ScanDraftResponse;
import com.prescriptionscanner.dto.UploadResponse;
import com.prescriptionscanner.dto.VerifyRequest;
import com.prescriptionscanner.dto.VerifyResponse;
import com.prescriptionscanner.dto.VisitDto;
import com.prescriptionscanner.exception.ApiException;
import com.prescriptionscanner.repository.DoctorRepository;
import com.prescriptionscanner.repository.PatientRepository;
import com.prescriptionscanner.repository.VisitRepository;
import com.prescriptionscanner.util.Dates;

@Service
public class PrescriptionService {

	private static final Logger log = LoggerFactory.getLogger(PrescriptionService.class);

	private final VisitRepository visitRepository;
	private final PatientRepository patientRepository;
	private final DoctorRepository doctorRepository;
	private final StorageService storageService;
	private final GeminiService geminiService;
	private final AuditService auditService;
	private final PiiMasker piiMasker;
	private final RateLimitService rateLimitService;
	private final PatientMatchingService patientMatchingService;
	private final AppProperties props;
	private final ObjectMapper mapper;
	private final TransactionTemplate transactionTemplate;

	public PrescriptionService(VisitRepository visitRepository, PatientRepository patientRepository,
			DoctorRepository doctorRepository, StorageService storageService, GeminiService geminiService,
			AuditService auditService, PiiMasker piiMasker, RateLimitService rateLimitService,
			PatientMatchingService patientMatchingService, AppProperties props, ObjectMapper mapper,
			TransactionTemplate transactionTemplate) {
		this.visitRepository = visitRepository;
		this.patientRepository = patientRepository;
		this.doctorRepository = doctorRepository;
		this.storageService = storageService;
		this.geminiService = geminiService;
		this.auditService = auditService;
		this.piiMasker = piiMasker;
		this.rateLimitService = rateLimitService;
		this.patientMatchingService = patientMatchingService;
		this.props = props;
		this.mapper = mapper;
		this.transactionTemplate = transactionTemplate;
	}

	/**
	 * Validates and stores the scan, asks Gemini to read it, and saves the
	 * unverified draft.
	 *
	 * <p>Deliberately NOT transactional: the Gemini call can take tens of
	 * seconds, and holding a database connection for its duration would exhaust
	 * the pool. Only the final insert runs in a (short) transaction.
	 */
	public UploadResponse upload(MultipartFile file, boolean consent, String contextName, String contextPhone,
			String contextPid, Long actorId, String clientIp) {

		enforceRateLimit(actorId, clientIp);

		// DPDP Act 2023: consent is a hard gate, not a nicety.
		if (!consent) {
			throw ApiException.badRequest(
					"Patient consent is required before a prescription can be processed. "
					+ "Tick the consent box and try again.");
		}

		ValidatedFile validated = validateFile(file);
		byte[] bytes = validated.bytes();
		String mimeType = validated.mimeType();

		StorageService.StoredFile stored = storageService.save(bytes, mimeType);

		try {
			GeminiExtraction extraction = geminiService.extract(bytes, mimeType,
					buildContextHint(contextName, contextPhone, contextPid));

			String rawJson = serializeRawAi(extraction, actorId);

			Long draftId = transactionTemplate.execute(status -> {
				Visit draft = new Visit();
				draft.setScanFilePath(stored.relativePath());
				draft.setRawAiJson(rawJson);
				draft.setVerified(false);
				Visit saved = visitRepository.save(draft);
				auditService.record(actorId, AuditService.CREATE, "DraftVisit", saved.getId());
				return saved.getId();
			});

			PatientSummaryDto matchedPatient = findMatchedPatient(extraction)
					.map(this::toSummary)
					.orElse(null);

			return UploadResponse.of(draftId, extraction, matchedPatient);

		} catch (RuntimeException ex) {
			// Nothing was verified, so do not leave an orphaned scan behind.
			storageService.delete(stored.relativePath());
			throw ex;
		}
	}

	/**
	 * Scan-only step for the upload screen: the same validation and Gemini
	 * extraction as {@link #upload}, but stores nothing and writes no draft, so
	 * the user can pre-fill the patient context before saving. Returns the
	 * existing patient matched by pid first, else name + age + gender.
	 */
	public ScanDraftResponse scan(MultipartFile file, Long actorId, String clientIp) {
		enforceRateLimit(actorId, clientIp);
		ValidatedFile validated = validateFile(file);
		GeminiExtraction extraction = geminiService.extract(validated.bytes(), validated.mimeType(),
				buildContextHint(null, null, null));
		PatientSummaryDto matchedPatient = findMatchedPatient(extraction)
				.map(this::toSummary)
				.orElse(null);
		return ScanDraftResponse.of(extraction, matchedPatient);
	}

	private record ValidatedFile(byte[] bytes, String mimeType) {
	}

	/** Shared upload throttling for the scan and save steps. */
	private void enforceRateLimit(Long actorId, String clientIp) {
		Duration window = Duration.ofMinutes(props.getRateLimit().getUploadWindowMinutes());
		RateLimitService.Decision decision = rateLimitService.check(
				"upload:" + clientIp + ":" + actorId,
				props.getRateLimit().getUploadMax(),
				window);
		if (!decision.allowed()) {
			throw ApiException.tooManyRequests("Upload limit reached. Try again in "
					+ Math.max(1, decision.retryAfterSeconds() / 60) + " minute(s).");
		}
	}

	/** Size, emptiness and MIME checks shared by the scan and save steps. */
	private ValidatedFile validateFile(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw ApiException.badRequest("No file was uploaded. Choose a JPG, PNG or PDF first.");
		}

		long maxBytes = storageService.maxBytes();
		if (file.getSize() > maxBytes) {
			throw ApiException.badRequest(String.format(
					"That file is %.1f MB. The maximum is %d MB.",
					file.getSize() / (1024.0 * 1024.0), props.getUpload().getMaxMb()));
		}

		byte[] bytes;
		try {
			bytes = file.getBytes();
		} catch (Exception ex) {
			throw ApiException.badRequest("Could not read the uploaded file.");
		}
		if (bytes.length == 0) {
			throw ApiException.badRequest("That file is empty.");
		}

		String declaredMime = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
		if (!storageService.isAllowedMime(declaredMime)) {
			throw ApiException.badRequest("Only JPG, PNG, WebP or PDF files are accepted.");
		}

		String detectedMime = StorageService.sniffMime(bytes);
		if (detectedMime == null) {
			throw ApiException.badRequest(
					"That file is not a valid JPG, PNG, WebP or PDF. It may be corrupted or renamed.");
		}
		// Trust the bytes over the browser's guess.
		String mimeType = "application/pdf".equals(detectedMime) ? "application/pdf" : detectedMime;
		return new ValidatedFile(bytes, mimeType);
	}

	/**
	 * The existing patient a new document should be attached to: exact pid first,
	 * then name + age + gender. Empty means "show a new patient folder".
	 */
	private Optional<Patient> findMatchedPatient(GeminiExtraction extraction) {
		if (extraction == null || extraction.patient() == null) {
			log.info("Patient match skipped: extraction has no patient block");
			return Optional.empty();
		}
		GeminiExtraction.PatientInfo info = extraction.patient();
		String pid = blankToNull(info.pid());
		if (pid != null) {
			Optional<Patient> byPid = patientRepository.findByPid(pid);
			if (byPid.isPresent()) {
				log.info("Patient match: found by PID");
				return byPid;
			}
			log.info("Patient match: PID not in DB, falling back to name + age");
		}
		if (blankToNull(info.name()) == null) {
			log.info("Patient match skipped: no patient name in extraction");
			return Optional.empty();
		}
		// Fall back to age_years when the model only filled the numeric age.
		String age = blankToNull(info.age());
		if (age == null && info.ageYears() != null) {
			age = String.valueOf(info.ageYears());
		}
		return patientMatchingService.matchByDemographics(info.name(), age, info.gender());
	}

	private PatientSummaryDto toSummary(Patient patient) {
		long documents = 0;
		List<Object[]> rows = visitRepository.countVerifiedByPatient(List.of(patient.getId()));
		if (!rows.isEmpty() && rows.get(0)[1] != null) {
			documents = ((Number) rows.get(0)[1]).longValue();
		}
		return PatientSummaryDto.from(patient, documents);
	}

	private String buildContextHint(String name, String phone, String pid) {
		String resolvedName = piiMasker.enabled() ? piiMasker.maskName(name) : blankToNull(name);
		String resolvedPhone = piiMasker.enabled() ? piiMasker.maskPhone(phone) : blankToNull(phone);

		String hint = Stream.of(
				blankToNull(pid) == null ? null : "PID: " + pid.trim(),
				resolvedName == null ? null : "Name: " + resolvedName,
				resolvedPhone == null ? null : "Phone: " + resolvedPhone)
				.filter(Objects::nonNull)
				.collect(Collectors.joining(" | "));

		return piiMasker.maskFreeText(hint);
	}

	private static String blankToNull(String s) {
		if (s == null) {
			return null;
		}
		String trimmed = s.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private String serializeRawAi(GeminiExtraction extraction, Long actorId) {
		try {
			JsonNode tree = mapper.valueToTree(extraction);
			if (tree instanceof ObjectNode objectNode) {
				objectNode.put("_uploadedBy", actorId);
				objectNode.put("_uploadedAt", Instant.now().toString());
				objectNode.put("_model", props.getGemini().getModel());
			}
			return mapper.writeValueAsString(tree);
		} catch (Exception ex) {
			log.warn("Could not serialise the AI extraction: {}", ex.getMessage());
			return "{}";
		}
	}

	// Not readOnly: it writes an audit entry.
	@Transactional
	public DraftResult getDraft(Long visitId, Long actorId) {
		Visit visit = visitRepository.findWithRefsById(visitId)
				.orElseThrow(() -> ApiException.notFound("No such draft or visit."));

		auditService.view(actorId, visit.isVerified() ? "Patient" : "DraftVisit",
				visit.isVerified() && visit.getPatient() != null ? visit.getPatient().getId() : visit.getId());

		GeminiExtraction extracted = readRawAi(visit.getRawAiJson());

		PatientSummaryDto matchedPatient = findMatchedPatient(extracted)
				.map(this::toSummary)
				.orElse(null);

		return new DraftResult(
				VisitDto.from(visit),
				extracted,
				visit.getRawAiJson(),
				matchedPatient,
				visit.getPatient() == null ? null : visit.getPatient().getId(),
				visit.getPatient() == null ? null : visit.getPatient().getName(),
				visit.getDoctor() == null ? null : visit.getDoctor().getId(),
				visit.getDoctor() == null ? null : visit.getDoctor().getName());
	}

	public record DraftResult(VisitDto visit, GeminiExtraction extracted, String rawJson,
			PatientSummaryDto matchedPatient, Long patientId, String patientName, Long doctorId,
			String doctorName) {
	}

	private GeminiExtraction readRawAi(String rawJson) {
		if (rawJson == null || rawJson.isBlank()) {
			return emptyExtraction();
		}
		try {
			return mapper.readValue(rawJson, GeminiExtraction.class).normalized();
		} catch (Exception ex) {
			log.warn("Stored rawAiJson could not be parsed: {}", ex.getMessage());
			return emptyExtraction();
		}
	}

	private GeminiExtraction emptyExtraction() {
		return GeminiExtraction.empty();
	}

	/**
	 * THE VERIFY STEP. Patient, doctor, visit, diagnoses, medicines, lab results
	 * and the audit entries all commit together, or none of them do.
	 */
	@Transactional
	public VerifyResponse verify(Long visitId, VerifyRequest request, Long actorId) {
		List<ApiError.FieldIssue> issues = new ArrayList<>();
		if (request.patient() == null || blankToNull(request.patient().name()) == null) {
			issues.add(new ApiError.FieldIssue("patient.name", "Patient name is required"));
		}
		if (request.doctor() == null || blankToNull(request.doctor().name()) == null) {
			issues.add(new ApiError.FieldIssue("doctor.name", "Doctor name is required"));
		}
		if (!issues.isEmpty()) {
			throw ApiException.badRequest(
					issues.stream().map(i -> i.path() + ": " + i.message()).collect(Collectors.joining("; ")),
					issues);
		}

		Visit draft = visitRepository.findById(visitId)
				.orElseThrow(() -> ApiException.notFound("No such draft."));

		if (draft.isVerified()) {
			throw ApiException.conflict("This record was already verified. Create a new draft instead.");
		}

		VerifyRequest.PatientInput p = request.patient();
		VerifyRequest.DoctorInput d = request.doctor();
		VerifyRequest.VisitInput v = request.visit() == null
				? new VerifyRequest.VisitInput(null, null, null, null, null, null, null)
				: request.visit();

		// --- 1. Patient: reuse by pid, else by name+phone --------------------
		boolean patientCreated = false;
		Patient patient = null;

		String pid = blankToNull(p.pid());
		if (pid != null) {
			patient = patientRepository.findByPid(pid).orElse(null);
		}
		String name = p.name().trim();
		String phone = blankToNull(p.phone());
		if (patient == null && phone != null) {
			List<Patient> candidates = patientRepository.findByNameAndPhone(name, phone);
			patient = candidates.isEmpty() ? null : candidates.get(0);
		}
		// Same name + age + gender: add the document to that existing patient folder.
		if (patient == null) {
			patient = patientMatchingService.matchByDemographics(name, p.age(), p.gender()).orElse(null);
		}
		if (patient == null) {
			patient = new Patient();
			patient.setPid(pid);
			patient.setName(name);
			patient.setGender(blankToNull(p.gender()));
			patient.setAge(blankToNull(p.age()));
			patient.setMaritalStatus(blankToNull(p.maritalStatus()));
			patient.setPhone(phone);
			patient.setAddress(blankToNull(p.address()));
			patient.setAllergies(blankToNull(p.allergies()));
			patient = patientRepository.save(patient);
			patientCreated = true;
			auditService.record(actorId, AuditService.CREATE, "Patient", patient.getId());
		} else {
			// Fill gaps the OCR left, without overwriting data we already trust.
			boolean changed = false;
			changed |= fillIfBlank(pid, patient::getPid, patient::setPid);
			changed |= fillIfBlank(blankToNull(p.gender()), patient::getGender, patient::setGender);
			changed |= fillIfBlank(blankToNull(p.age()), patient::getAge, patient::setAge);
			changed |= fillIfBlank(blankToNull(p.maritalStatus()), patient::getMaritalStatus,
					patient::setMaritalStatus);
			changed |= fillIfBlank(blankToNull(p.address()), patient::getAddress, patient::setAddress);
			changed |= fillIfBlank(blankToNull(p.allergies()), patient::getAllergies, patient::setAllergies);
			changed |= fillIfBlank(phone, patient::getPhone, patient::setPhone);
			if (changed) {
				patient = patientRepository.save(patient);
				auditService.record(actorId, AuditService.UPDATE, "Patient", patient.getId());
			}
		}

		// --- 2. Doctor: reuse by registration number, else by name -----------
		Doctor doctor = null;
		String registrationNo = blankToNull(d.registrationNo());
		if (registrationNo != null) {
			doctor = doctorRepository.findFirstByRegistrationNo(registrationNo).orElse(null);
		}
		if (doctor == null) {
			doctor = doctorRepository.findFirstByNameIgnoreCase(d.name().trim()).orElse(null);
		}
		if (doctor == null) {
			doctor = new Doctor();
			doctor.setName(d.name().trim());
			doctor.setQualification(blankToNull(d.qualification()));
			doctor.setRegistrationNo(registrationNo);
			doctor.setDesignation(blankToNull(d.designation()));
			doctor.setClinic(blankToNull(d.clinic()));
			doctor = doctorRepository.save(doctor);
			auditService.record(actorId, AuditService.CREATE, "Doctor", doctor.getId());
		}

		// --- 3. Promote the draft into a real visit --------------------------
		draft.setPatient(patient);
		draft.setDoctor(doctor);
		draft.setVisitDate(Dates.parseFlexible(v.visitDate()));
		draft.setVisitTime(blankToNull(v.visitTime()));
		draft.setValidUpTo(Dates.parseFlexible(v.validUpTo()));
		draft.setAppointmentNo(blankToNull(v.appointmentNo()));
		draft.setMode(blankToNull(v.mode()));
		draft.setFollowUpDate(Dates.parseFlexible(v.followUpDate()));
		draft.setNotes(blankToNull(v.notes()));
		draft.setVerified(true);
		draft.setVerifiedBy(actorId);
		if (request.rawAiJson() != null) {
			try {
				draft.setRawAiJson(mapper.writeValueAsString(request.rawAiJson()));
			} catch (Exception ex) {
				log.warn("Could not store submitted rawAiJson: {}", ex.getMessage());
			}
		}
		if (request.reviewedJson() != null) {
			try {
				draft.setReviewedJson(mapper.writeValueAsString(request.reviewedJson()));
			} catch (Exception ex) {
				log.warn("Could not store submitted reviewedJson: {}", ex.getMessage());
			}
		}

		// --- 4. Children ------------------------------------------------------
		List<VerifyRequest.DiagnosisInput> diagnoses = request.diagnoses() == null ? List.of() : request.diagnoses();
		List<VerifyRequest.MedicineInput> medicines = request.medicines() == null ? List.of() : request.medicines();
		List<VerifyRequest.LabResultInput> labs = request.labResults() == null ? List.of() : request.labResults();

		draft.getDiagnoses().clear();
		for (VerifyRequest.DiagnosisInput input : diagnoses) {
			String description = blankToNull(input.description());
			if (description == null) {
				continue;
			}
			Diagnosis entity = new Diagnosis();
			entity.setVisit(draft);
			entity.setDescription(description);
			draft.getDiagnoses().add(entity);
		}

		draft.getMedicines().clear();
		for (VerifyRequest.MedicineInput input : medicines) {
			String medicineName = blankToNull(input.name());
			if (medicineName == null) {
				continue;
			}
			PrescribedMedicine entity = new PrescribedMedicine();
			entity.setVisit(draft);
			entity.setName(medicineName);
			entity.setDose(blankToNull(input.dose()));
			entity.setFrequency(blankToNull(input.frequency()));
			entity.setDuration(blankToNull(input.duration()));
			entity.setInstructions(blankToNull(input.instructions()));
			draft.getMedicines().add(entity);
		}

		draft.getLabResults().clear();
		for (VerifyRequest.LabResultInput input : labs) {
			String testName = blankToNull(input.testName());
			if (testName == null) {
				continue;
			}
			LabResult entity = new LabResult();
			entity.setVisit(draft);
			entity.setTestName(testName);
			entity.setValue(blankToNull(input.value()));
			entity.setUnit(blankToNull(input.unit()));
			entity.setReferenceRange(blankToNull(input.referenceRange()));
			entity.setAbnormal(Boolean.TRUE.equals(input.isAbnormal()));
			draft.getLabResults().add(entity);
		}

		visitRepository.save(draft);

		auditService.record(actorId, AuditService.VERIFY, "Visit", draft.getId());
		auditService.record(actorId, AuditService.VIEW, "Patient", patient.getId());

		return new VerifyResponse(
				true,
				draft.getId(),
				patient.getId(),
				patientCreated,
				patient.getPid(),
				doctor.getId(),
				new VerifyResponse.Counts(draft.getDiagnoses().size(), draft.getMedicines().size(),
						draft.getLabResults().size()),
				"Verified and saved.");
	}

	private boolean fillIfBlank(String incoming, java.util.function.Supplier<String> getter,
			java.util.function.Consumer<String> setter) {
		if (incoming == null) {
			return false;
		}
		String current = getter.get();
		if (current != null && !current.isBlank()) {
			return false;
		}
		setter.accept(incoming);
		return true;
	}
}
