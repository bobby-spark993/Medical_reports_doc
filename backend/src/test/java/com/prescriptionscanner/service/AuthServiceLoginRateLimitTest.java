package com.prescriptionscanner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.prescriptionscanner.config.AppProperties;
import com.prescriptionscanner.domain.Role;
import com.prescriptionscanner.domain.User;
import com.prescriptionscanner.dto.LoginRequest;
import com.prescriptionscanner.exception.ApiException;
import com.prescriptionscanner.repository.UserRepository;
import com.prescriptionscanner.security.JwtService;

/**
 * The sign-in rate limit: five failed attempts per email + IP, then the page
 * is told how long to wait. A successful sign-in clears the streak.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceLoginRateLimitTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtService jwtService;

	@Mock
	private AuditService auditService;

	private AuthService service;

	@BeforeEach
	void setUp() {
		AppProperties props = new AppProperties();
		service = new AuthService(userRepository, passwordEncoder, jwtService, auditService,
				new RateLimitService(), props);
	}

	private static LoginRequest request(String email, String password) {
		return new LoginRequest(email, password);
	}

	@Test
	void fiveFailedAttemptsAreAllowedTheSixthIsBlocked() {
		when(userRepository.findByEmailIgnoreCase("staff@clinic.local")).thenReturn(Optional.empty());
		when(passwordEncoder.matches(anyString(), any())).thenReturn(false);

		for (int i = 0; i < 5; i++) {
			assertThatThrownBy(() -> service.login(request("staff@clinic.local", "wrong"), "10.0.0.1"))
					.isInstanceOf(ApiException.class)
					.hasMessage("Incorrect email or password");
		}

		assertThatThrownBy(() -> service.login(request("staff@clinic.local", "wrong"), "10.0.0.1"))
				.isInstanceOf(ApiException.class)
				.satisfies(ex -> {
					ApiException api = (ApiException) ex;
					assertThat(api.getStatus().value()).isEqualTo(429);
					assertThat(api.getRetryAfterSeconds()).isPositive();
					assertThat(api.getMessage()).contains("Too many failed sign-in attempts");
				});
	}

	@Test
	void aDifferentAccountIsNotLockedOutBySomeoneElsesMistakes() {
		when(userRepository.findByEmailIgnoreCase("staff@clinic.local")).thenReturn(Optional.empty());
		when(userRepository.findByEmailIgnoreCase("other@clinic.local")).thenReturn(Optional.empty());
		when(passwordEncoder.matches(anyString(), any())).thenReturn(false);

		for (int i = 0; i < 5; i++) {
			assertThatThrownBy(() -> service.login(request("staff@clinic.local", "wrong"), "10.0.0.1"))
					.hasMessage("Incorrect email or password");
		}

		// The lock belongs to the account that failed, not to the whole IP.
		assertThatThrownBy(() -> service.login(request("other@clinic.local", "wrong"), "10.0.0.1"))
				.hasMessage("Incorrect email or password");
	}

	@Test
	void theLockHoldsEvenForTheRightPassword() {
		User user = new User();
		user.setId(7L);
		user.setName("Staff");
		user.setEmail("staff@clinic.local");
		user.setPasswordHash("hash");
		user.setRole(Role.RECEPTIONIST);

		when(userRepository.findByEmailIgnoreCase("staff@clinic.local")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches(anyString(), any())).thenReturn(false);

		for (int i = 0; i < 5; i++) {
			assertThatThrownBy(() -> service.login(request("staff@clinic.local", "wrong"), "10.0.0.1"))
					.hasMessage("Incorrect email or password");
		}

		// The sixth attempt is refused before the password is even checked:
		// the stubs for a successful sign-in are never reached.
		assertThatThrownBy(() -> service.login(request("staff@clinic.local", "correct"), "10.0.0.1"))
				.satisfies(ex -> assertThat(((ApiException) ex).getStatus().value()).isEqualTo(429));
	}

	@Test
	void aSuccessfulSignInClearsTheStreak() {
		User user = new User();
		user.setId(7L);
		user.setName("Staff");
		user.setEmail("staff@clinic.local");
		user.setPasswordHash("hash");
		user.setRole(Role.RECEPTIONIST);

		when(userRepository.findByEmailIgnoreCase("staff@clinic.local")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches(anyString(), any())).thenReturn(false);
		when(passwordEncoder.matches("correct", "hash")).thenReturn(true);
		when(jwtService.issue(user)).thenReturn("token");

		for (int i = 0; i < 4; i++) {
			assertThatThrownBy(() -> service.login(request("staff@clinic.local", "wrong"), "10.0.0.1"))
					.hasMessage("Incorrect email or password");
		}

		assertThat(service.login(request("staff@clinic.local", "correct"), "10.0.0.1").token())
				.isEqualTo("token");

		// The streak is gone, so a fresh run of five failures is allowed again.
		for (int i = 0; i < 5; i++) {
			assertThatThrownBy(() -> service.login(request("staff@clinic.local", "wrong"), "10.0.0.1"))
					.hasMessage("Incorrect email or password");
		}
	}
}
