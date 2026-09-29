package com.prescriptionscanner.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Adds indexes Hibernate's {@code @Index} annotations cannot express.
 *
 * <p>Duplicate detection compares {@code lower(name)} together with gender and
 * age, which needs a matching functional index; {@code @Index} only accepts
 * plain column lists. Runs after Hibernate has created the schema, and is
 * idempotent so repeated starts are safe.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SchemaInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

	private static final String FUNCTIONAL_NAME_INDEX =
			"CREATE INDEX IF NOT EXISTS patients_name_lower_gender_age_idx "
			+ "ON patients (lower(name), gender, age)";

	private final JdbcTemplate jdbc;

	public SchemaInitializer(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void run(ApplicationArguments args) {
		try {
			jdbc.execute(FUNCTIONAL_NAME_INDEX);
			log.info("Ensured index patients_name_lower_gender_age_idx");
		} catch (Exception ex) {
			// A missing index only slows duplicate detection; it must not stop startup.
			log.warn("Could not ensure functional patient index: {}", ex.getMessage());
		}
	}
}
