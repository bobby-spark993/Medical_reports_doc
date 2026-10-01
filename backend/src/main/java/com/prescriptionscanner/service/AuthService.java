package com.prescriptionscanner.service;

import java.time.Duration;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import com.prescriptionscanner.config.AppProperties;
import com.prescriptionscanner.domain.Role;
import com.prescriptionscanner.domain.User;
import com.prescriptionscanner.dto.ApiError;
import com.prescriptionscanner.dto.LoginRequest;
import com.prescriptionscanner.dto.RegisterRequest;
import com.prescriptionscanner.dto.UserDto;
import com.prescriptionscanner.exception.ApiException;
import com.prescriptionscanner.repository.UserRepository;
import com.prescriptionscanner.security.JwtService;

import jakarta.annotation.PostConstruct;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final AuditService auditService;
	private final RateLimitService rateLimitService;
	private final AppProperties props;

	/**
	 * A real bcrypt hash of a random value, compared against when the email is
	 * unknown. Without it a missing account would answer measurably faster than a
	 * wrong password, letting an attacker enumerate valid emails by timing.
	 */
	private String dummyHash;

	private static final int MIN_PASSWORD_LENGTH = 8;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
			AuditService auditService, RateLimitService rateLimitService, AppProperties props) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.auditService = auditService;
		this.rateLimitService = rateLimitService;
		this.props = props;
	}

	@PostConstruct
	void initDummyHash() {
		this.dummyHash = passwordEncoder.encode(java.util.UUID.randomUUID().toString());
	}

	public record LoginResult(String token, UserDto user) {
	}

	@Transactional
	public LoginResult login(LoginRequest request, String clientIp) {
		Duration window = Duration.ofMinutes(props.getRateLimit().getLoginWindowMinutes());
		RateLimitService.Decision decision = rateLimitService.check(
				"login:" + clientIp, props.getRateLimit().getLoginMax(), window);
		if (!decision.allowed()) {
			throw ApiException.tooManyRequests(
					"Too many login attempts. Try again in "
					+ Math.max(1, decision.retryAfterSeconds() / 60) + " minute(s).");
		}

		String email = request.email().trim().toLowerCase();
		Optional<User> maybe = userRepository.findByEmailIgnoreCase(email);

		boolean passwordMatches = passwordEncoder.matches(
				request.password(),
				maybe.map(User::getPasswordHash).orElse(dummyHash));

		if (maybe.isEmpty() || !passwordMatches) {
			auditService.record(maybe.map(User::getId).orElse(null), AuditService.LOGIN, "User",
					maybe.map(User::getId).orElse(null));
			throw ApiException.unauthorized("Incorrect email or password");
		}

		User user = maybe.get();
		String token = jwtService.issue(user);
		auditService.record(user.getId(), AuditService.LOGIN, "User", user.getId());
		return new LoginResult(token, UserDto.from(user));
	}

	/**
	 * Public self-registration. Creates a staff account (admin, doctor or
	 * receptionist) and signs the new user straight in.
	 */
	@Transactional
	public LoginResult register(RegisterRequest request, String clientIp) {
		Duration window = Duration.ofMinutes(props.getRateLimit().getLoginWindowMinutes());
		RateLimitService.Decision decision = rateLimitService.check(
				"register:" + clientIp, props.getRateLimit().getLoginMax(), window);
		if (!decision.allowed()) {
			throw ApiException.tooManyRequests(
					"Too many sign-up attempts. Try again in "
					+ Math.max(1, decision.retryAfterSeconds() / 60) + " minute(s).");
		}

		String email = request.email().trim().toLowerCase();
		if (userRepository.existsByEmailIgnoreCase(email)) {
			throw ApiException.conflict("A user with that email already exists. Sign in instead.");
		}

		Role role = selfRegistrationRole(request.role());
		String password = requirePassword(request.password());

		User user = new User();
		user.setName(request.name().trim());
		user.setEmail(email);
		user.setRole(role);
		user.setPasswordHash(passwordEncoder.encode(password));

		User saved = userRepository.save(user);
		auditService.record(saved.getId(), AuditService.CREATE, "User", saved.getId());

		String token = jwtService.issue(saved);
		return new LoginResult(token, UserDto.from(saved));
	}

	/** Self-registration may create admin, doctor or receptionist accounts. */
	private static Role selfRegistrationRole(String raw) {
		if (raw == null || raw.isBlank()) {
			return Role.RECEPTIONIST;
		}
		String normalized = raw.trim().toLowerCase();
		for (Role role : Role.values()) {
			if (role.value().equals(normalized)) {
				return role;
			}
		}
		throw ApiException.badRequest("Unknown role.",
				List.of(new ApiError.FieldIssue("role", "Must be admin, doctor or receptionist")));
	}

	private static String requirePassword(String password) {
		String value = password == null ? "" : password;
		if (value.length() < MIN_PASSWORD_LENGTH) {
			throw ApiException.badRequest(
					"Password must be at least " + MIN_PASSWORD_LENGTH + " characters.",
					List.of(new ApiError.FieldIssue("password",
							"Must be at least " + MIN_PASSWORD_LENGTH + " characters")));
		}
		return value;
	}

	@Transactional(readOnly = true)
	public Optional<UserDto> currentUser(Long userId) {
		return userRepository.findById(userId).map(UserDto::from);
	}
}
