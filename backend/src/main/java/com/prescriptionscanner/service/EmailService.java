package com.prescriptionscanner.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.prescriptionscanner.config.AppProperties;

/**
 * Sends emails (currently only password-reset OTPs). Spring Boot only creates
 * a {@link JavaMailSender} when {@code spring.mail.host} is configured, so when
 * no SMTP server is set up this service simply reports unavailable and the
 * caller falls back to logging the OTP to the console.
 */
@Service
public class EmailService {

	private static final Logger log = LoggerFactory.getLogger(EmailService.class);

	private final ObjectProvider<JavaMailSender> senderProvider;
	private final String smtpHost;
	private final String from;

	public EmailService(ObjectProvider<JavaMailSender> senderProvider,
			@Value("${spring.mail.host:}") String smtpHost, AppProperties props) {
		this.senderProvider = senderProvider;
		this.smtpHost = smtpHost;
		this.from = props.getMail().getFrom();
	}

	public boolean isAvailable() {
		return smtpHost != null && !smtpHost.isBlank() && senderProvider.getIfAvailable() != null;
	}

	/** Sends a plain-text email. Returns true when delivered, false when no SMTP is configured. */
	public boolean send(String to, String subject, String body) {
		JavaMailSender sender = senderProvider.getIfAvailable();
		if (sender == null) {
			return false;
		}
		try {
			SimpleMailMessage message = new SimpleMailMessage();
			message.setFrom(from);
			message.setTo(to);
			message.setSubject(subject);
			message.setText(body);
			sender.send(message);
			return true;
		} catch (Exception ex) {
			log.warn("Could not send email to {}: {}", to, ex.getMessage());
			return false;
		}
	}
}