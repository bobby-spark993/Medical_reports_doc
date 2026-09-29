package com.prescriptionscanner.web;

import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.prescriptionscanner.dto.ConfirmDocumentRequest;
import com.prescriptionscanner.dto.ConfirmDocumentResponse;
import com.prescriptionscanner.dto.ScanResponse;
import com.prescriptionscanner.security.SecurityUser;
import com.prescriptionscanner.service.AuditService;
import com.prescriptionscanner.service.MedicalDocumentService;
import com.prescriptionscanner.service.StorageService;

import jakarta.validation.Valid;

/** Scan (extract), confirm (save) and stream medical documents. */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

	private final MedicalDocumentService documentService;
	private final AuditService auditService;

	public DocumentController(MedicalDocumentService documentService, AuditService auditService) {
		this.documentService = documentService;
		this.auditService = auditService;
	}

	/**
	 * Accepts one or more files (jpg/jpeg/png/pdf, up to 20 MB each), sends each
	 * to Gemini and returns the extracted draft plus patient-match suggestions.
	 * Nothing is written to the database.
	 */
	@PostMapping(value = "/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPTIONIST')")
	public ScanResponse scan(@RequestParam("files") List<MultipartFile> files,
			@AuthenticationPrincipal SecurityUser user) {
		return documentService.scan(files, user.getId());
	}

	/** Saves the reviewed data + patient in one transaction. */
	@PostMapping("/confirm")
	@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPTIONIST')")
	public ConfirmDocumentResponse confirm(@Valid @RequestBody ConfirmDocumentRequest request,
			@AuthenticationPrincipal SecurityUser user) {
		return documentService.confirm(request, user.getId());
	}

	/** Streams the original file. Never publicly served. */
	@GetMapping("/{id}/file")
	@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPTIONIST')")
	public ResponseEntity<byte[]> file(@PathVariable Long id,
			@AuthenticationPrincipal SecurityUser user,
			@RequestParam(value = "download", required = false) String download) {

		StorageService.LoadedFile file = documentService.loadFile(id);
		auditService.view(user.getId(), "MedicalDocument", id);

		boolean wantsDownload = "1".equals(download);
		ContentDisposition disposition = (wantsDownload ? ContentDisposition.attachment()
				: ContentDisposition.inline())
				.filename(file.filename())
				.build();

		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
				.header(HttpHeaders.CACHE_CONTROL, "private, no-store")
				.header("X-Content-Type-Options", "nosniff")
				.contentType(MediaType.parseMediaType(file.contentType()))
				.contentLength(file.data().length)
				.body(file.data());
	}
}
