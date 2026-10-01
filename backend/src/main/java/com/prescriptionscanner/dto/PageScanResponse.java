package com.prescriptionscanner.dto;

import java.util.List;

/**
 * Returned by POST /api/prescriptions/scan-pages: one item per page across all
 * uploaded files, nothing persisted. {@code documentType} is decided by the
 * printed PID (see {@code DocumentClassifier}), not by the model.
 */
public record PageScanResponse(boolean ok, List<PageItem> pages) {

	public record PageItem(
			int sourceIndex,
			String sourceName,
			int page,
			int pageCount,
			String documentType,
			GeminiExtraction extracted,
			PatientSummaryDto matchedPatient,
			String thumbnail) {
	}

	public static PageScanResponse of(List<PageItem> pages) {
		return new PageScanResponse(true, pages);
	}
}
