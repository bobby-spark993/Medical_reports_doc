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

import com.prescriptionscanner.domain.Visit;
import com.prescriptionscanner.exception.ApiException;
import com.prescriptionscanner.repository.VisitRepository;
import com.prescriptionscanner.security.SecurityUser;
import com.prescriptionscanner.service.AuditService;
import com.prescriptionscanner.service.StorageService;

/**
 * Streams a stored scan. There is no public folder for uploads: this endpoint
 * is the only way to read a file, and it requires a valid JWT cookie.
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

	private final VisitRepository visitRepository;
	private final StorageService storageService;
	private final AuditService auditService;

	public FileController(VisitRepository visitRepository, StorageService storageService,
			AuditService auditService) {
		this.visitRepository = visitRepository;
		this.storageService = storageService;
		this.auditService = auditService;
	}

	@GetMapping("/{visitId}")
	@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPTIONIST')")
	public ResponseEntity<byte[]> stream(@PathVariable Long visitId,
			@AuthenticationPrincipal SecurityUser user,
			@RequestParam(value = "download", required = false) String download) {

		Visit visit = visitRepository.findById(visitId)
				.orElseThrow(() -> ApiException.notFound("No such visit."));

		if (visit.getScanFilePath() == null || visit.getScanFilePath().isBlank()) {
			throw ApiException.notFound("This visit has no stored scan.");
		}

		StorageService.LoadedFile file = storageService.load(visit.getScanFilePath())
				.orElseThrow(() -> ApiException.notFound("The stored scan file is missing from storage."));

		if (visit.getPatient() != null) {
			auditService.view(user.getId(), "Patient", visit.getPatient().getId());
		} else {
			auditService.view(user.getId(), "DraftVisit", visit.getId());
		}

		boolean wantsDownload = "1".equals(download);
		ContentDisposition disposition = (wantsDownload ? ContentDisposition.attachment()
				: ContentDisposition.inline())
				.filename("scan-visit-" + visitId + extensionFor(file.contentType()))
				.build();

		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
				.header(HttpHeaders.CACHE_CONTROL, "private, no-store")
				.header("X-Content-Type-Options", "nosniff")
				.contentType(MediaType.parseMediaType(file.contentType()))
				.contentLength(file.data().length)
				.body(file.data());
	}

	private static String extensionFor(String contentType) {
		return switch (contentType) {
			case "image/png" -> ".png";
			case "image/webp" -> ".webp";
			case "application/pdf" -> ".pdf";
			default -> ".jpg";
		};
	}
}
