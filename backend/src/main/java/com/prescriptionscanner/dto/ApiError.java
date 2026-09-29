package com.prescriptionscanner.dto;

import java.util.List;

/** Consistent error body: { ok:false, error, issues[] }. */
public record ApiError(boolean ok, String error, List<FieldIssue> issues) {

	public record FieldIssue(String path, String message) {
	}

	public static ApiError of(String message) {
		return new ApiError(false, message, List.of());
	}

	public static ApiError of(String message, List<FieldIssue> issues) {
		return new ApiError(false, message, issues);
	}
}
