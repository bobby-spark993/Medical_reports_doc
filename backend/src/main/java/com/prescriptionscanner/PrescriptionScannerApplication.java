package com.prescriptionscanner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

// UserDetailsServiceAutoConfiguration is excluded because login is handled by
// AuthService (manual bcrypt check + our own JWT cookie). Without this,
// Spring Boot prints a pointless generated security password on every start.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
public class PrescriptionScannerApplication {

	public static void main(String[] args) {
		SpringApplication.run(PrescriptionScannerApplication.class, args);
	}

}
