package com.prescriptionscanner.exception;

import java.util.List;

import org.springframework.http.HttpStatus;

import com.prescriptionscanner.dto.ApiError;

/** An error with an HTTP status and a message that is safe to show staff. */
public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final List<ApiError.FieldIssue> issues;

	public ApiException(HttpStatus status, String message) {
		this(status, message, List.of());
	}

	public ApiException(HttpStatus status, String message, List<ApiError.FieldIssue> issues) {
		super(message);
		this.status = status;
		this.issues = issues;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public List<ApiError.FieldIssue> getIssues() {
		return issues;
	}

	public static ApiException badRequest(String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, message);
	}

	public static ApiException badRequest(String message, List<ApiError.FieldIssue> issues) {
		return new ApiException(HttpStatus.BAD_REQUEST, message, issues);
	}

	public static ApiException unauthorized(String message) {
		return new ApiException(HttpStatus.UNAUTHORIZED, message);
	}

	public static ApiException forbidden(String message) {
		return new ApiException(HttpStatus.FORBIDDEN, message);
	}

	public static ApiException notFound(String message) {
		return new ApiException(HttpStatus.NOT_FOUND, message);
	}

	public static ApiException conflict(String message) {
		return new ApiException(HttpStatus.CONFLICT, message);
	}

	public static ApiException tooManyRequests(String message) {
		return new ApiException(HttpStatus.TOO_MANY_REQUESTS, message);
	}

	public static ApiException badGateway(String message) {
		return new ApiException(HttpStatus.BAD_GATEWAY, message);
	}

	public static ApiException internal(String message) {
		return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, message);
	}
}
