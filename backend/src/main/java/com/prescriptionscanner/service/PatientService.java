package com.prescriptionscanner.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.prescriptionscanner.domain.Appointment;
import com.prescriptionscanner.domain.Patient;
import com.prescriptionscanner.domain.Visit;
import com.prescriptionscanner.dto.MedicalDocumentDto;
import com.prescriptionscanner.dto.PageResponse;
import com.prescriptionscanner.dto.PatientDetailResponse;
import com.prescriptionscanner.dto.PatientListItem;
import com.prescriptionscanner.dto.PatientVisitStats;
import com.prescriptionscanner.dto.VisitDto;
import com.prescriptionscanner.exception.ApiException;
import com.prescriptionscanner.repository.AppointmentRepository;
import com.prescriptionscanner.repository.PatientRepository;
import com.prescriptionscanner.repository.VisitRepository;

@Service
public class PatientService {

	private final PatientRepository patientRepository;
	private final VisitRepository visitRepository;
	private final AppointmentRepository appointmentRepository;
	private final AuditService auditService;
	private final MedicalDocumentService medicalDocumentService;

	public PatientService(PatientRepository patientRepository, VisitRepository visitRepository,
			AppointmentRepository appointmentRepository, AuditService auditService,
			MedicalDocumentService medicalDocumentService) {
		this.patientRepository = patientRepository;
		this.visitRepository = visitRepository;
		this.appointmentRepository = appointmentRepository;
		this.auditService = auditService;
		this.medicalDocumentService = medicalDocumentService;
	}

	// Not readOnly: these methods write an audit entry, and a read-only
	// Hibernate session refuses to flush the insert.
	@Transactional
	public PageResponse<PatientListItem> list(String search, int page, int limit, Long actorId) {
		int safePage = Math.max(1, page);
		int safeLimit = Math.min(Math.max(1, limit), 100);
		Pageable pageable = PageRequest.of(
				safePage - 1,
				safeLimit,
				Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by(Sort.Direction.DESC, "id")));

		boolean hasSearch = search != null && !search.isBlank();
		Page<Patient> result = hasSearch
				? patientRepository.search(search.trim(), pageable)
				: patientRepository.findAll(pageable);

		List<Long> ids = result.getContent().stream().map(Patient::getId).toList();
		Map<Long, PatientVisitStats> stats = loadStats(ids);

		auditService.view(actorId, "PatientList", null);

		List<PatientListItem> items = result.getContent().stream()
				.map(p -> {
					PatientVisitStats s = stats.get(p.getId());
					return PatientListItem.from(p,
							s == null || s.visitCount() == null ? 0L : s.visitCount(),
							s == null ? null : s.nextFollowUp());
				})
				.toList();

		return new PageResponse<>(
				true,
				items,
				new PageResponse.Pagination(
						safePage,
						safeLimit,
						result.getTotalElements(),
						Math.max(1, result.getTotalPages()),
						result.hasNext(),
						result.hasPrevious()));
	}

	/** Two cheap grouped queries beat one CASE expression JPQL handles unevenly. */
	private Map<Long, PatientVisitStats> loadStats(List<Long> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		Map<Long, Long> counts = new HashMap<>();
		for (Object[] row : visitRepository.countVerifiedByPatient(ids)) {
			counts.put((Long) row[0], ((Number) row[1]).longValue());
		}
		Map<Long, LocalDate> followUps = new HashMap<>();
		for (Object[] row : visitRepository.nextFollowUpByPatient(ids, LocalDate.now())) {
			followUps.put((Long) row[0], (LocalDate) row[1]);
		}

		Map<Long, PatientVisitStats> out = new HashMap<>();
		for (Long id : ids) {
			out.put(id, new PatientVisitStats(id, counts.getOrDefault(id, 0L), followUps.get(id)));
		}
		return out;
	}

	@Transactional
	public PatientDetailResponse detail(Long patientId, Long actorId) {
		Patient patient = patientRepository.findById(patientId)
				.orElseThrow(() -> ApiException.notFound("No such patient."));

		// Every view of a patient record is auditable (DPDP Act 2023).
		auditService.view(actorId, "Patient", patient.getId());

		List<Visit> visits = visitRepository
				.findByPatientIdAndIsVerifiedTrueOrderByVisitDateDescIdDesc(patient.getId());

		List<Appointment> appointments = appointmentRepository
				.findByPatientIdOrderByScheduledAtDesc(patient.getId());

		LocalDate today = LocalDate.now();
		LocalDateTime now = LocalDateTime.now();

		List<VisitDto> visitDtos = visits.stream().map(VisitDto::from).toList();

		List<PatientDetailResponse.AppointmentDto> appointmentDtos = appointments.stream()
				.map(a -> new PatientDetailResponse.AppointmentDto(
						a.getId(),
						a.getScheduledAt(),
						a.getStatus() == null ? null : a.getStatus().value(),
						a.getNotes(),
						a.getDoctor() == null ? null : a.getDoctor().getName(),
						a.getScheduledAt() != null
								&& a.getScheduledAt().isAfter(now)
								&& a.getStatus() == com.prescriptionscanner.domain.AppointmentStatus.SCHEDULED))
				.toList();

		List<PatientDetailResponse.FollowUpDto> followUps = new ArrayList<>();
		for (Visit v : visits) {
			if (v.getFollowUpDate() != null && !v.getFollowUpDate().isBefore(today)) {
				followUps.add(new PatientDetailResponse.FollowUpDto(v.getId(), v.getFollowUpDate()));
			}
		}
		followUps.sort((a, b) -> a.followUpDate().compareTo(b.followUpDate()));

		List<MedicalDocumentDto> documents = medicalDocumentService.listForPatient(patient.getId());

		return PatientDetailResponse.of(
				PatientDetailResponse.PatientDto.from(patient),
				visitDtos,
				appointmentDtos,
				followUps,
				documents);
	}

	/** Used by the PDF report, which wants visits oldest-first. */
	@Transactional(readOnly = true)
	public List<Visit> verifiedVisitsOldestFirst(Long patientId) {
		return visitRepository.findByPatientIdAndIsVerifiedTrueOrderByVisitDateAscIdAsc(patientId);
	}

	@Transactional(readOnly = true)
	public Patient requirePatient(Long patientId) {
		return patientRepository.findById(patientId)
				.orElseThrow(() -> ApiException.notFound("No such patient."));
	}

	@Transactional(readOnly = true)
	public List<Appointment> appointmentsOldestFirst(Long patientId) {
		return appointmentRepository.findByPatientIdOrderByScheduledAtAsc(patientId);
	}
}
