package com.prescriptionscanner.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Convenience access to the authenticated user inside controllers/services. */
public final class CurrentUser {

	private CurrentUser() {
	}

	/** @return the logged-in user, or null when anonymous. */
	public static SecurityUser get() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()) {
			return null;
		}
		if (authentication.getPrincipal() instanceof SecurityUser user) {
			return user;
		}
		return null;
	}

	public static Long id() {
		SecurityUser user = get();
		return user == null ? null : user.getId();
	}

	public static String role() {
		SecurityUser user = get();
		return user == null ? null : user.getRole();
	}
}
