package com.prescriptionscanner.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.prescriptionscanner.config.AppProperties;
import com.prescriptionscanner.domain.Role;
import com.prescriptionscanner.domain.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;

/** Issues and verifies the HS256 JWTs stored in the httpOnly session cookie. */
@Service
public class JwtService {

	private static final Logger log = LoggerFactory.getLogger(JwtService.class);
	private static final int MIN_SECRET_BYTES = 32;

	private final SecretKey key;
	private final long expirationMinutes;
	private final String issuer;

	public JwtService(AppProperties props) {
		String secret = props.getJwt().getSecret();
		if (secret == null || secret.isBlank()) {
			throw new IllegalStateException(
					"app.jwt.secret is not set. Put a long random string in "
					+ "application-local.properties (gitignored) or set the JWT_SECRET environment variable. "
					+ "Generate one with: node -e \"console.log(require('crypto').randomBytes(64).toString('base64url'))\"");
		}
		if ("change_me".equalsIgnoreCase(secret)) {
			throw new IllegalStateException(
					"app.jwt.secret is still the placeholder 'change_me'. Set a real random secret.");
		}
		byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
		if (bytes.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException(
					"app.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes for HS256 (got " + bytes.length + ").");
		}
		this.key = Keys.hmacShaKeyFor(bytes);
		this.expirationMinutes = props.getJwt().getExpirationMinutes();
		this.issuer = props.getJwt().getIssuer();
	}

	public long expirationSeconds() {
		return expirationMinutes * 60;
	}

	public String issue(User user) {
		Instant now = Instant.now();
		return Jwts.builder()
				.subject(String.valueOf(user.getId()))
				.issuer(issuer)
				.claim("name", user.getName())
				.claim("email", user.getEmail())
				.claim("role", user.getRole() == null ? Role.RECEPTIONIST.value() : user.getRole().value())
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
				.signWith(key)
				.compact();
	}

	/** @return the principal, or empty when the token is missing/invalid/expired. */
	public Optional<JwtPrincipal> verify(String token) {
		if (token == null || token.isBlank()) {
			return Optional.empty();
		}
		try {
			Claims claims = Jwts.parser()
					.verifyWith(key)
					.requireIssuer(issuer)
					.build()
					.parseSignedClaims(token)
					.getPayload();

			Long userId = Long.valueOf(claims.getSubject());
			Role role = Role.from(claims.get("role", String.class));
			return Optional.of(new JwtPrincipal(
					userId,
					claims.get("name", String.class),
					claims.get("email", String.class),
					role));
		} catch (JwtException | IllegalArgumentException ex) {
			log.debug("Rejected JWT: {}", ex.getMessage());
			return Optional.empty();
		}
	}
}
