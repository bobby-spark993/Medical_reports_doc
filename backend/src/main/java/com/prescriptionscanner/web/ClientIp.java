package com.prescriptionscanner.web;

import jakarta.servlet.http.HttpServletRequest;

/** Best-effort client IP, used only for rate limiting. */
public final class ClientIp {

	private ClientIp() {
	}

	public static String of(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].trim();
		}
		String realIp = request.getHeader("X-Real-IP");
		if (realIp != null && !realIp.isBlank()) {
			return realIp.trim();
		}
		return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
	}
}
