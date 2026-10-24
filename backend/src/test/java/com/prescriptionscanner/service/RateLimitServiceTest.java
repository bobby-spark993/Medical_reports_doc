package com.prescriptionscanner.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/** Unit tests for the in-memory sliding-window limiter (no database). */
class RateLimitServiceTest {

	private final RateLimitService service = new RateLimitService();

	@Test
	void allowsUpToTheLimit() {
		for (int i = 0; i < 5; i++) {
			assertThat(service.check("key", 5, Duration.ofMinutes(15)).allowed()).isTrue();
		}
	}

	@Test
	void blocksTheAttemptAfterTheLimit() {
		for (int i = 0; i < 5; i++) {
			service.check("key", 5, Duration.ofMinutes(15));
		}

		RateLimitService.Decision decision = service.check("key", 5, Duration.ofMinutes(15));

		assertThat(decision.allowed()).isFalse();
		assertThat(decision.remaining()).isZero();
		assertThat(decision.retryAfterSeconds()).isPositive();
	}

	@Test
	void blockedAttemptsDoNotPushTheUnlockTimeFurtherOut() {
		for (int i = 0; i < 5; i++) {
			service.check("key", 5, Duration.ofMinutes(15));
		}
		long first = service.check("key", 5, Duration.ofMinutes(15)).retryAfterSeconds();
		long second = service.check("key", 5, Duration.ofMinutes(15)).retryAfterSeconds();

		assertThat(second).isLessThanOrEqualTo(first);
	}

	@Test
	void keysAreIndependent() {
		for (int i = 0; i < 5; i++) {
			service.check("one", 5, Duration.ofMinutes(15));
		}

		assertThat(service.check("one", 5, Duration.ofMinutes(15)).allowed()).isFalse();
		assertThat(service.check("two", 5, Duration.ofMinutes(15)).allowed()).isTrue();
	}

	@Test
	void resetClearsTheRecordedAttempts() {
		for (int i = 0; i < 5; i++) {
			service.check("key", 5, Duration.ofMinutes(15));
		}
		assertThat(service.check("key", 5, Duration.ofMinutes(15)).allowed()).isFalse();

		service.reset("key");

		assertThat(service.check("key", 5, Duration.ofMinutes(15)).allowed()).isTrue();
	}

	@Test
	void reportsHowManyAttemptsAreLeft() {
		assertThat(service.check("key", 5, Duration.ofMinutes(15)).remaining()).isEqualTo(4);
		assertThat(service.check("key", 5, Duration.ofMinutes(15)).remaining()).isEqualTo(3);
	}
}
