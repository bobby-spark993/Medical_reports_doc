package com.prescriptionscanner.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Hashtable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.prescriptionscanner.config.AppProperties;
import com.prescriptionscanner.domain.PasswordReset;
import com.prescriptionscanner.domain.User;
import com.prescriptionscanner.dto.ApiError;
import com.prescriptionscanner.exception.ApiException;
import com.prescriptionscanner.repository.PasswordResetRepository;
import com.prescriptionscanner.repository.UserRepository;

/**
 * "Forgot password" flow backed by a 6-digit OTP.
 *
 * <p>The email is validated (registered account + domain can receive mail)
 * before a code is issued. The code is emailed to the account when SMTP is
 * configured, otherwise it is written to the application log in a banner so a
 * password can always be reset. The code itself is stored only as a bcrypt
 * hash and expires after 10 minutes.
 */
@Service
public class PasswordResetService {

	private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

	private static final int OTP_LENGTH = 6;
	private static final Duration OTP_TTL = Duration.ofMinutes(10);
	private static final int MIN_PASSWORD_LENGTH = 8;

	private final PasswordResetRepository resetRepository;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuditService auditService;
	private final RateLimitService rateLimitService;
	private final AppProperties props;
	private final EmailService emailService;
	private final SecureRandom random = new SecureRandom();

	public PasswordResetService(PasswordResetRepository resetRepository, UserRepository userRepository,
			PasswordEncoder passwordEncoder, AuditService auditService, RateLimitService rateLimitService,
			AppProperties props, EmailService emailService) {
		this.resetRepository = resetRepository;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.auditService = auditService;
		this.rateLimitService = rateLimitService;
		this.props = props;
		this.emailService = emailService;
	}

	/**
	 * Issues an OTP for the given email and emails it to the account. The email
	 * is validated first: it must be a registered account whose domain can
	 * receive mail, otherwise an error is returned instead of sending anything.
	 */
	@Transactional
	public void requestReset(String rawEmail, String clientIp) {
		checkRateLimit("otp-request:" + clientIp);

		String email = rawEmail.trim().toLowerCase();

		if (!isDeliverableDomain(email)) {
			throw ApiException.badRequest(
					"This email domain cannot receive mail. Check the address and try again.");
		}

		User user = userRepository.findByEmailIgnoreCase(email)
				.orElseThrow(() -> ApiException.notFound("No account found with that email."));

		resetRepository.deleteByEmailIgnoreCase(email);

		String otp = randomOtp();
		PasswordReset reset = new PasswordReset();
		reset.setEmail(email);
		reset.setOtpHash(passwordEncoder.encode(otp));
		reset.setExpiresAt(Instant.now().plus(OTP_TTL));
		resetRepository.save(reset);

		String subject = "Your password reset code";
		String body = "Your Prescription Scanner password reset code is: " + otp + "\n\n"
				+ "It expires in " + OTP_TTL.toMinutes() + " minutes. Enter it on the reset page "
				+ "to choose a new password.\n\nIf you did not request this, you can ignore this email.";

		if (emailService.isAvailable() && emailService.send(email, subject, body)) {
			log.info("Password reset OTP emailed to {}", email);
			return;
		}

		log.warn("==========================================================");
		log.warn(" Password reset OTP for {}", email);
		log.warn("   otp : {}", otp);
		log.warn("   expires in {} minutes", OTP_TTL.toMinutes());
		log.warn(" Email is not configured, so the code is shown here instead.");
		log.warn(" Set spring.mail.host/username/password to deliver it by email.");
		log.warn("==========================================================");
	}

	/**
	 * Confirms the email's domain publishes MX records (so mail can actually be
	 * delivered there). Fails open when DNS is unavailable, so a local network
	 * hiccup never blocks password resets entirely.
	 */
	private static boolean isDeliverableDomain(String email) {
		int at = email.indexOf('@');
		String domain = at >= 0 ? email.substring(at + 1) : "";
		if (domain.isBlank() || domain.contains("@")) {
			return false;
		}
		try {
			Hashtable<String, String> env = new Hashtable<>();
			env.put(javax.naming.Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
			javax.naming.directory.DirContext ctx = new javax.naming.directory.InitialDirContext(env);
			javax.naming.directory.Attributes attrs = ctx.getAttributes(domain, new String[] { "MX" });
			return attrs.get("MX") != null;
		} catch (javax.naming.NameNotFoundException ex) {
			// The domain exists but has no MX records: mail cannot be delivered.
			return false;
		} catch (Exception ex) {
			// DNS lookup unavailable (no resolver, corporate firewall): do not block.
			return true;
		}
	}

	/** Redeems a previously issued OTP and sets a new password. */
	@Transactional
	public void reset(String rawEmail, String otp, String rawPassword, String clientIp) {
		checkRateLimit("otp-verify:" + clientIp);

		String email = rawEmail.trim().toLowerCase();
		String password = requirePassword(rawPassword);

		User user = userRepository.findByEmailIgnoreCase(email)
				.orElseThrow(() -> ApiException.badRequest("Invalid or expired OTP. Request a new one."));

		PasswordReset reset = resetRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtDesc(email)
				.orElseThrow(() -> ApiException.badRequest("Invalid or expired OTP. Request a new one."));

		if (reset.getUsedAt() != null
				|| reset.getExpiresAt().isBefore(Instant.now())
				|| !passwordEncoder.matches(otp == null ? "" : otp, reset.getOtpHash())) {
			throw ApiException.badRequest("Invalid or expired OTP. Request a new one.");
		}

		user.setPasswordHash(passwordEncoder.encode(password));
		userRepository.save(user);

		reset.setUsedAt(Instant.now());
		resetRepository.save(reset);

		auditService.record(user.getId(), AuditService.UPDATE, "User", user.getId());
	}

	private void checkRateLimit(String key) {
		Duration window = Duration.ofMinutes(props.getRateLimit().getLoginWindowMinutes());
		RateLimitService.Decision decision = rateLimitService.check(
				key, props.getRateLimit().getLoginMax(), window);
		if (!decision.allowed()) {
			throw ApiException.tooManyRequests(
					"Too many attempts. Try again in "
					+ Math.max(1, decision.retryAfterSeconds() / 60) + " minute(s).");
		}
	}

	private String randomOtp() {
		return String.format("%0" + OTP_LENGTH + "d", random.nextInt(1_000_000));
	}

	private static String requirePassword(String password) {
		String value = password == null ? "" : password;
		if (value.length() < MIN_PASSWORD_LENGTH) {
			throw ApiException.badRequest(
					"Password must be at least " + MIN_PASSWORD_LENGTH + " characters.",
					java.util.List.of(new ApiError.FieldIssue("password",
							"Must be at least " + MIN_PASSWORD_LENGTH + " characters")));
		}
		return value;
	}
}