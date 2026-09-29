package com.prescriptionscanner.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.prescriptionscanner.domain.User;

/** Adapts our User entity to Spring Security, and exposes the id to controllers. */
public class SecurityUser implements UserDetails {

	private final Long id;
	private final String name;
	private final String email;
	private final String passwordHash;
	private final String role;

	public SecurityUser(User user) {
		this.id = user.getId();
		this.name = user.getName();
		this.email = user.getEmail();
		this.passwordHash = user.getPasswordHash();
		this.role = user.getRole() == null ? "receptionist" : user.getRole().value();
	}

	public SecurityUser(JwtPrincipal principal) {
		this.id = principal.userId();
		this.name = principal.name();
		this.email = principal.email();
		this.passwordHash = "";
		this.role = principal.role() == null ? "receptionist" : principal.role().value();
	}

	public Long getId() { return id; }
	public String getRole() { return role; }
	public String getName() { return name; }
	public String getEmail() { return email; }

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
	}

	@Override
	public String getPassword() { return passwordHash; }

	@Override
	public String getUsername() { return email; }

	public boolean hasRole(String... accepted) {
		for (String r : accepted) {
			if (r.equalsIgnoreCase(role)) {
				return true;
			}
		}
		return false;
	}
}
