package com.prescriptionscanner.dto;

import java.util.List;

/**
 * Returned by POST /api/prescriptions/page-info: how many pages each uploaded
 * file has, so the client can scan them one page per request.
 */
public record PageInfoResponse(boolean ok, List<FileInfo> files) {

	public record FileInfo(int sourceIndex, String sourceName, int pageCount) {
	}

	public static PageInfoResponse of(List<FileInfo> files) {
		return new PageInfoResponse(true, files);
	}
}
