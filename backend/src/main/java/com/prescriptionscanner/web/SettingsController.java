package com.prescriptionscanner.web;

import java.io.IOException;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.prescriptionscanner.dto.OkResponse;
import com.prescriptionscanner.security.SecurityUser;
import com.prescriptionscanner.service.AuditService;
import com.prescriptionscanner.service.SettingsService;
import com.prescriptionscanner.service.StorageService;

/**
 * Clinic brand settings. The logo endpoint is public (the login/register pages
 * show the logo before anyone signs in); uploading or removing it is admin-only.
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

	private final SettingsService settingsService;
	private final AuditService auditService;

	public SettingsController(SettingsService settingsService, AuditService auditService) {
		this.settingsService = settingsService;
		this.auditService = auditService;
	}

	/** Streams the uploaded logo, or 204 when none has been set. */
	@GetMapping("/logo")
	public ResponseEntity<byte[]> logo() {
		StorageService.LoadedFile file = settingsService.logo()
				.orElse(null);
		if (file == null) {
			return ResponseEntity.noContent().build();
		}
		return ResponseEntity.ok()
				.header(HttpHeaders.CACHE_CONTROL, CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePublic().getHeaderValue())
				.header("X-Content-Type-Options", "nosniff")
				.contentType(MediaType.parseMediaType(file.contentType()))
				.contentLength(file.data().length)
				.body(file.data());
	}

	@PostMapping("/logo")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<OkResponse> uploadLogo(@RequestParam("file") MultipartFile file,
			@org.springframework.security.core.annotation.AuthenticationPrincipal SecurityUser user)
			throws IOException {

		settingsService.saveLogo(file.getBytes(), file.getContentType());
		auditService.record(user.getId(), AuditService.UPDATE, "Settings", null);
		return ResponseEntity.ok(OkResponse.success());
	}

	@DeleteMapping("/logo")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<OkResponse> deleteLogo(
			@org.springframework.security.core.annotation.AuthenticationPrincipal SecurityUser user) {

		boolean removed = settingsService.deleteLogo();
		auditService.record(user.getId(), AuditService.UPDATE, "Settings", null);
		return ResponseEntity.ok(new OkResponse(removed));
	}
}