package com.prescriptionscanner.dto;

import java.util.List;

/**
 * Returned by POST /api/prescriptions/upload-pages: one saved draft per page.
 * {@code batchId} groups all pages of this upload; {@code error} is set (and
 * the draft id null) when an individual page could not be scanned.
 */
public record PageUploadResponse(boolean ok, String batchId, List<PageResult> pages) {

	public record PageResult(
			int sourceIndex,
			String sourceName,
			int page,
			int pageCount,
			String documentType,
			Long draftId,
			GeminiExtraction extracted,
			PatientSummaryDto matchedPatient,
			String error) {
	}

	public static PageUploadResponse of(String batchId, List<PageResult> pages) {
		return new PageUploadResponse(true, batchId, pages);
	}
}
