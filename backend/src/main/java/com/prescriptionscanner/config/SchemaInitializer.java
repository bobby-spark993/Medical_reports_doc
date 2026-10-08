package com.prescriptionscanner.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adds the indexes Hibernate's {@code @Index} annotations cannot express:
 * functional (expression) indexes, {@code pg_trgm} GIN indexes for the
 * leading-wildcard searches, and composite indexes with a sort direction.
 *
 * <p>Runs after Hibernate has created the schema, and is idempotent
 * ({@code IF NOT EXISTS}) so repeated starts are safe. Each statement is
 * attempted independently: a missing extension or insufficient privilege is
 * logged and skipped, never fatal, because an index only affects performance.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SchemaInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

	/**
	 * The trigram extension powers the GIN indexes below. Created first and in
	 * its own statement so the rest still run even if the extension is denied.
	 */
	private static final String TRGM_EXTENSION = "CREATE EXTENSION IF NOT EXISTS pg_trgm";

	/**
	 * Every index in creation order. Expressions must match the repository
	 * queries byte-for-byte (e.g. the native name normaliser) or PostgreSQL will
	 * not consider the index.
	 */
	private static final List<String> INDEXES = List.of(
			// Duplicate detection: lower(name) + gender + age (also matched in code).
			"CREATE INDEX IF NOT EXISTS patients_name_lower_gender_age_idx "
					+ "ON patients (lower(name), gender, age)",

			// PatientMatchingService native query normalises the same way.
			"CREATE INDEX IF NOT EXISTS patients_name_norm_idx "
					+ "ON patients (regexp_replace(lower(trim(name)), '[[:space:]]+', ' ', 'g'))",

			// Partial name/pid/phone search in PatientRepository.search*.
			"CREATE INDEX IF NOT EXISTS patients_name_trgm_idx ON patients USING gin (name gin_trgm_ops)",
			"CREATE INDEX IF NOT EXISTS patients_pid_trgm_idx ON patients USING gin (pid gin_trgm_ops)",
			"CREATE INDEX IF NOT EXISTS patients_phone_trgm_idx ON patients USING gin (phone gin_trgm_ops)",

			// UserRepository findByEmailIgnoreCase / existsByEmailIgnoreCase.
			"CREATE INDEX IF NOT EXISTS users_email_lower_idx ON users (lower(email))",
			// UserRepository findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase.
			"CREATE INDEX IF NOT EXISTS users_name_trgm_idx ON users USING gin (name gin_trgm_ops)",
			"CREATE INDEX IF NOT EXISTS users_email_trgm_idx ON users USING gin (email gin_trgm_ops)",

			// DoctorRepository findFirstByNameIgnoreCase.
			"CREATE INDEX IF NOT EXISTS doctors_name_lower_idx ON doctors (lower(name))",

			// PasswordResetRepository findFirstByEmailIgnoreCaseOrderByCreatedAtDesc.
			"CREATE INDEX IF NOT EXISTS password_resets_email_created_idx "
					+ "ON password_resets (lower(email), created_at DESC)",

			// LabResultRepository findByIsAbnormalTrue.
			"CREATE INDEX IF NOT EXISTS lab_results_abnormal_idx ON lab_results (is_abnormal)");

	private final JdbcTemplate jdbc;

	public SchemaInitializer(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void run(ApplicationArguments args) {
		ensureTrigramExtension();
		for (String sql : INDEXES) {
			try {
				jdbc.execute(sql);
				log.info("Ensured index: {}", sql);
			} catch (Exception ex) {
				// A missing index only slows queries; it must not stop startup.
				log.warn("Could not ensure index [{}]: {}", sql, ex.getMessage());
			}
		}
	}

	private void ensureTrigramExtension() {
		try {
			jdbc.execute(TRGM_EXTENSION);
			log.info("Ensured extension pg_trgm");
		} catch (Exception ex) {
			log.warn("Could not ensure pg_trgm extension (trigram indexes will be skipped): {}",
					ex.getMessage());
		}
	}
}
