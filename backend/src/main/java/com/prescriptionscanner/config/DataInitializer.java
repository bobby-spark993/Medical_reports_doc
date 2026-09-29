package com.prescriptionscanner.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.prescriptionscanner.domain.Role;
import com.prescriptionscanner.domain.User;
import com.prescriptionscanner.repository.UserRepository;

/**
 * Ensures the configured admin account exists, so there is always a way in.
 * Idempotent: if the admin email already exists, nothing happens (an existing
 * password is never overwritten). Set app.seed.enabled=false to switch off.
 */
@Component
public class DataInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final AppProperties props;

	public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder, AppProperties props) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.props = props;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		AppProperties.Seed seed = props.getSeed();
		if (!seed.isEnabled()) {
			log.info("[seed] disabled (app.seed.enabled=false)");
			return;
		}

		String email = seed.getAdminEmail().toLowerCase();
		if (userRepository.existsByEmailIgnoreCase(email)) {
			log.info("[seed] admin {} already exists, skipping", email);
			return;
		}

		User admin = new User();
		admin.setName(seed.getAdminName());
		admin.setEmail(email);
		admin.setRole(Role.ADMIN);
		admin.setPasswordHash(passwordEncoder.encode(seed.getAdminPassword()));
		userRepository.save(admin);

		log.warn("==========================================================");
		log.warn(" Seeded admin user");
		log.warn("   email    : {}", seed.getAdminEmail());
		log.warn("   password : {}", seed.getAdminPassword());
		log.warn(" Change this password before any real use.");
		log.warn("==========================================================");
	}
}
