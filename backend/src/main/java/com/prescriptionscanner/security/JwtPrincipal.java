package com.prescriptionscanner.security;

import com.prescriptionscanner.domain.Role;

/** What we read back out of a verified JWT. */
public record JwtPrincipal(Long userId, String name, String email, Role role) {
}
