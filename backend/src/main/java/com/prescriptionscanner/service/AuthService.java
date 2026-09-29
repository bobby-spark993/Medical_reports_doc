package com.prescriptionscanner.service;

import java.time.Duration;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.prescriptionscanner.config.AppProperties;
import com.prescriptionscanner.domain.User;
import com.prescriptionscanner.dto.LoginRequest;
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

	@Transactional(readOnly = true)
	public Optional<UserDto> currentUser(Long userId) {
		return userRepository.findById(userId).map(UserDto::from);
	}
}
