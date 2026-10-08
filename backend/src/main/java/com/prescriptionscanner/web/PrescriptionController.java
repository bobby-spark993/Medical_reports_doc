package com.prescriptionscanner.web;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.prescriptionscanner.dto.DraftResponse;
import com.prescriptionscanner.dto.OkResponse;
import com.prescriptionscanner.dto.PageInfoResponse;
import com.prescriptionscanner.dto.PageScanResponse;
import com.prescriptionscanner.dto.PageUploadResponse;
import com.prescriptionscanner.dto.ScanDraftResponse;
import com.prescriptionscanner.dto.UploadResponse;
import com.prescriptionscanner.dto.VerifyRequest;
import com.prescriptionscanner.dto.VerifyResponse;
import com.prescriptionscanner.security.SecurityUser;
import com.prescriptionscanner.service.PrescriptionService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/prescriptions")
public class PrescriptionController {

	private final PrescriptionService prescriptionService;

	public PrescriptionController(PrescriptionService prescriptionService) {
		this.prescriptionService = prescriptionService;
	}

	/**
	 * Accepts multipart form-data: file + consent flag, plus optional context
	 * hints. Validates type and size, stores privately, calls Gemini, and
	 * returns the draft id with the extracted JSON.
	 */
	@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPTIONIST')")
	public UploadResponse upload(
			@RequestParam("file") MultipartFile file,
			@RequestParam(value = "consent", required = false) String consent,
			@RequestParam(value = "contextName", required = false) String contextName,
			@RequestParam(value = "contextPhone", required = false) String contextPhone,
			@RequestParam(value = "contextPid", required = false) String contextPid,
			@AuthenticationPrincipal SecurityUser user,
			HttpServletRequest http) {

		return prescriptionService.upload(
				file,
				isTruthy(consent),
				contextName,
				contextPhone,
				contextPid,
				user.getId(),
				ClientIp.of(http));
	}

	/**
	 * Scan-only: validates the file and runs Gemini, but stores nothing and
	 * writes no draft. Used by the upload screen to pre-fill the patient
	 * context before the user saves.
	 */
	@PostMapping(value = "/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPTIONIST')")
	public ScanDraftResponse scan(
			@RequestParam("file") MultipartFile file,
			@AuthenticationPrincipal SecurityUser user,
			HttpServletRequest http) {

		return prescriptionService.scan(file, user.getId(), ClientIp.of(http));
	}

	/**
	 * Page-wise scan-only: accepts one or more files (images and/or multi-page
	 * PDFs), splits each into pages and extracts every page independently.
	 * Nothing is persisted.
	 */
	@PostMapping(value = "/scan-pages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPTIONIST')")
	public PageScanResponse scanPages(
			@RequestParam("files") List<MultipartFile> files,
			@AuthenticationPrincipal SecurityUser user,
			HttpServletRequest http) {

		return prescriptionService.scanPages(files, user.getId(), ClientIp.of(http));
	}

	/**
	 * Page-wise save: stores one draft per page, all grouped under a batch id.
	 * {@code isVerified} stays false until each page is reviewed.
	 */
	@PostMapping(value = "/upload-pages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPTIONIST')")
	public PageUploadResponse uploadPages(
			@RequestParam("files") List<MultipartFile> files,
			@RequestParam(value = "consent", required = false) String consent,
			@RequestParam(value = "contextName", required = false) String contextName,
			@RequestParam(value = "contextPhone", required = false) String contextPhone,
			@RequestParam(value = "contextPid", required = false) String contextPid,
			@AuthenticationPrincipal SecurityUser user,
			HttpServletRequest http) {

		return prescriptionService.uploadPages(files, isTruthy(consent), contextName, contextPhone,
				contextPid, user.getId(), ClientIp.of(http));
	}

	/** How many pages each uploaded file has (for one-page-at-a-time scanning). */
	@PostMapping(value = "/page-info", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPTIONIST')")
	public PageInfoResponse pageInfo(
			@RequestParam("files") List<MultipartFile> files,
			@AuthenticationPrincipal SecurityUser user,
			HttpServletRequest http) {

		return prescriptionService.pageInfo(files, user.getId(), ClientIp.of(http));
	}

	/** Scans exactly one page of one file. Nothing is persisted. */
	@PostMapping(value = "/scan-page", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPTIONIST')")
	public PageScanResponse scanPage(
			@RequestParam("file") MultipartFile file,
			@RequestParam("page") int page,
			@AuthenticationPrincipal SecurityUser user,
			HttpServletRequest http) {

		return prescriptionService.scanSinglePage(file, page, user.getId(), ClientIp.of(http));
	}

	/** Saves exactly one page as its own draft. */
	@PostMapping(value = "/save-page", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPTIONIST')")
	public UploadResponse savePage(
			@RequestParam("file") MultipartFile file,
			@RequestParam("page") int page,
			@RequestParam(value = "pageCount", required = false) Integer pageCount,
			@RequestParam(value = "batchId", required = false) String batchId,
			@RequestParam(value = "consent", required = false) String consent,
			@RequestParam(value = "contextName", required = false) String contextName,
			@RequestParam(value = "contextPhone", required = false) String contextPhone,
			@RequestParam(value = "contextPid", required = false) String contextPid,
			@RequestParam(value = "rawAiJson", required = false) String rawAiJson,
			@AuthenticationPrincipal SecurityUser user,
			HttpServletRequest http) {

		return prescriptionService.uploadSinglePage(file, page, pageCount, batchId, isTruthy(consent),
				contextName, contextPhone, contextPid, rawAiJson, user.getId(), ClientIp.of(http));
	}

	/** Draft/visit data for the review screen. */
	@GetMapping("/{id}")
	@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPTIONIST')")
	public DraftResponse getDraft(@PathVariable Long id, @AuthenticationPrincipal SecurityUser user) {
		PrescriptionService.DraftResult result = prescriptionService.getDraft(id, user.getId());

		DraftResponse.Ref patient = result.patientId() == null ? null
				: new DraftResponse.Ref(result.patientId(), null, result.patientName());
		DraftResponse.Ref doctor = result.doctorId() == null ? null
				: new DraftResponse.Ref(result.doctorId(), null, result.doctorName());

		return DraftResponse.of(result.visit(), result.extracted(), result.rawJson(),
				result.matchedPatient(), patient, doctor);
	}

	/** Saves the verified data in ONE transaction. */
	@PostMapping("/{id}/verify")
	@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPTIONIST')")
	public VerifyResponse verify(@PathVariable Long id, @RequestBody VerifyRequest request,
			@AuthenticationPrincipal SecurityUser user) {
		return prescriptionService.verify(id, request, user.getId());
	}

	/** Deletes a saved document (visit) and its stored scan. */
	@DeleteMapping("/{id}")
	@PreAuthorize("hasAnyRole('ADMIN','DOCTOR','RECEPTIONIST')")
	public OkResponse delete(@PathVariable Long id, @AuthenticationPrincipal SecurityUser user) {
		prescriptionService.deleteVisit(id, user.getId());
		return OkResponse.success();
	}

	private static boolean isTruthy(String value) {
		if (value == null) {
			return false;
		}
		String normalized = value.trim().toLowerCase();
		return List.of("true", "on", "1", "yes").contains(normalized);
	}
}
