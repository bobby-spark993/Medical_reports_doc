package com.prescriptionscanner.web;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.prescriptionscanner.dto.PageResponse;
import com.prescriptionscanner.dto.PatientDetailResponse;
import com.prescriptionscanner.dto.PatientListItem;
import com.prescriptionscanner.security.SecurityUser;
import com.prescriptionscanner.service.AuditService;
import com.prescriptionscanner.service.PatientService;
import com.prescriptionscanner.service.ReportService;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

	private final PatientService patientService;
	private final ReportService reportService;
	private final AuditService auditService;

	public PatientController(PatientService patientService, ReportService reportService,
			AuditService auditService) {
		this.patientService = patientService;
		this.reportService = reportService;
		this.auditService = auditService;
	}

	/** Search by name/pid/phone, paged. */
	@GetMapping
	@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPTIONIST')")
	public PageResponse<PatientListItem> list(
			@RequestParam(value = "search", required = false) String search,
			@RequestParam(value = "page", defaultValue = "1") int page,
			@RequestParam(value = "limit", defaultValue = "20") int limit,
			@AuthenticationPrincipal SecurityUser user) {
		return patientService.list(search, page, limit, user.getId());
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPTIONIST')")
	public PatientDetailResponse detail(@PathVariable Long id, @AuthenticationPrincipal SecurityUser user) {
		return patientService.detail(id, user.getId());
	}

	/** PDF export. Limited to doctors and admins. */
	@GetMapping("/{id}/report")
	@PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
	public ResponseEntity<byte[]> report(@PathVariable Long id,
			@AuthenticationPrincipal SecurityUser user,
			@RequestParam(value = "download", defaultValue = "1") String download) {

		byte[] pdf = reportService.buildPatientReport(id, user.getName());

		auditService.record(user.getId(), AuditService.DOWNLOAD_REPORT, "Patient", id);

		boolean inline = "0".equals(download);
		ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
				.filename("patient-report-" + id + ".pdf")
				.build();

		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
				.header(HttpHeaders.CACHE_CONTROL, "private, no-store")
				.contentType(MediaType.APPLICATION_PDF)
				.contentLength(pdf.length)
				.body(pdf);
	}
}
