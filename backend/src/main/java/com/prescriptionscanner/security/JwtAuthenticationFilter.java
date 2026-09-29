package com.prescriptionscanner.security;

import java.io.IOException;
import java.util.Optional;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Reads the JWT from the httpOnly cookie (falling back to an Authorization
 * header) and populates the SecurityContext. Never rejects on its own: a
 * missing/invalid token just leaves the request anonymous, and the
 * authorization rules decide what happens next.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	public static final String COOKIE_NAME = "ps_token";

	private final JwtService jwtService;

	public JwtAuthenticationFilter(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain chain) throws ServletException, IOException {

		if (SecurityContextHolder.getContext().getAuthentication() == null) {
			extractToken(request)
					.flatMap(jwtService::verify)
					.ifPresent(principal -> {
						SecurityUser user = new SecurityUser(principal);
						UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
								user, null, user.getAuthorities());
						auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
						SecurityContextHolder.getContext().setAuthentication(auth);
					});
		}

		chain.doFilter(request, response);
	}

	private Optional<String> extractToken(HttpServletRequest request) {
		if (request.getCookies() != null) {
			for (Cookie cookie : request.getCookies()) {
				if (COOKIE_NAME.equals(cookie.getName()) && cookie.getValue() != null
						&& !cookie.getValue().isBlank()) {
					return Optional.of(cookie.getValue());
				}
			}
		}
		String header = request.getHeader("Authorization");
		if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
			String token = header.substring(7).trim();
			if (!token.isEmpty()) {
				return Optional.of(token);
			}
		}
		return Optional.empty();
	}
}
