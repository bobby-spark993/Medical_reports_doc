package com.prescriptionscanner.service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.prescriptionscanner.config.AppProperties;

import jakarta.mail.internet.MimeMessage;

/**
 * Sends emails (currently only password-reset OTPs). Spring Boot only creates
 * a {@link JavaMailSender} when {@code spring.mail.host} is configured, so when
 * no SMTP server is set up this service simply reports unavailable and the
 * caller falls back to logging the OTP to the console.
 *
 * <p>The OTP email is sent as multipart HTML with the clinic's uploaded brand
 * logo embedded inline (referenced as {@code cid:clinicLogo}), so the logo
 * renders in the inbox without needing a publicly reachable URL.
 */
@Service
public class EmailService {

	private static final Logger log = LoggerFactory.getLogger(EmailService.class);

	private static final String LOGO_CID = "clinicLogo";

	private final ObjectProvider<JavaMailSender> senderProvider;
	private final SettingsService settingsService;
	private final String smtpHost;
	private final String from;
	private final String smtpUsername;

	public EmailService(ObjectProvider<JavaMailSender> senderProvider,
			@Value("${spring.mail.host:}") String smtpHost,
			@Value("${spring.mail.username:}") String smtpUsername,
			AppProperties props, SettingsService settingsService) {
		this.senderProvider = senderProvider;
		this.smtpHost = smtpHost;
		this.smtpUsername = smtpUsername;
		this.from = props.getMail().getFrom();
		this.settingsService = settingsService;
	}

	public boolean isAvailable() {
		return smtpHost != null && !smtpHost.isBlank() && senderProvider.getIfAvailable() != null;
	}

	/**
	 * Emails a password-reset OTP as branded HTML with the clinic logo embedded
	 * inline. Returns true when delivered, false when no SMTP is configured or
	 * the send failed (the caller then logs the code to the console instead).
	 */
	public boolean sendPasswordResetOtp(String to, String otp, long expiresInMinutes) {
		JavaMailSender sender = senderProvider.getIfAvailable();
		if (sender == null) {
			return false;
		}

		Optional<StorageService.LoadedFile> logo = settingsService.logo();
		String html = otpHtml(otp, expiresInMinutes, logo.isPresent());
		String text = "Your Prescription Scanner password reset code is: " + otp + "\n\n"
				+ "It expires in " + expiresInMinutes + " minutes. Enter it on the reset page "
				+ "to choose a new password.\n\nIf you did not request this, you can ignore this email.";

		try {
			MimeMessage message = sender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
			String senderAddress = (from == null || from.isBlank()) ? smtpUsername : from;
			if (senderAddress != null && !senderAddress.isBlank()) {
				helper.setFrom(senderAddress);
			}
			helper.setTo(to);
			helper.setSubject("Your password reset code");
			helper.setText(text, html);
			if (logo.isPresent()) {
				StorageService.LoadedFile file = logo.get();
				helper.addInline(LOGO_CID, new ByteArrayResource(file.data()), file.contentType());
			}
			sender.send(message);
			return true;
		} catch (Exception ex) {
			log.warn("Could not send email to {}: {}", to, ex.getMessage());
			return false;
		}
	}

	private static String otpHtml(String otp, long expiresInMinutes, boolean hasLogo) {
		String logo = hasLogo
				? "<img src=\"cid:" + LOGO_CID + "\" alt=\"Prescription Scanner\" "
						+ "style=\"max-width:180px;max-height:72px;height:auto;display:block;margin:0 auto 6px;\">"
				: "";
		return OTP_TEMPLATE
				.replace("{{LOGO}}", logo)
				.replace("{{OTP}}", otp == null ? "" : otp)
				.replace("{{MINUTES}}", Long.toString(expiresInMinutes));
	}

	private static final String OTP_TEMPLATE = """
			<!doctype html>
			<html lang="en">
			<head>
			<meta charset="utf-8">
			<meta name="viewport" content="width=device-width,initial-scale=1">
			<title>Your password reset code</title>
			</head>
			<body style="margin:0;padding:0;background:#eef2f4;font-family:-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
			<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#eef2f4;padding:32px 12px;">
			<tr><td align="center">
			<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:480px;background:#ffffff;border:1px solid #e2e8ec;border-radius:16px;">
			<tr><td style="padding:28px 28px 6px;text-align:center;">
			{{LOGO}}
			<div style="font-size:17px;font-weight:700;color:#0f2b33;letter-spacing:.2px;">Prescription Scanner</div>
			</td></tr>
			<tr><td style="padding:10px 28px 0;">
			<h1 style="margin:0;font-size:20px;line-height:1.3;color:#0f2b33;text-align:center;">Password reset code</h1>
			<p style="margin:10px 0 0;font-size:14px;line-height:1.5;color:#5a6b72;text-align:center;">Enter this code on the reset page to choose a new password.</p>
			</td></tr>
			<tr><td style="padding:20px 28px 4px;">
			<div style="background:#f0fbf9;border:1px solid #b7e6df;border-radius:12px;padding:18px;text-align:center;">
			<div style="font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;font-size:34px;line-height:1;letter-spacing:8px;font-weight:700;color:#0f766e;">{{OTP}}</div>
			</div>
			<p style="margin:14px 0 0;font-size:13px;color:#7a8a90;text-align:center;">This code expires in {{MINUTES}} minutes.</p>
			</td></tr>
			<tr><td style="padding:18px 28px 28px;">
			<p style="margin:0;font-size:12px;line-height:1.6;color:#93a2a8;text-align:center;">If you didn't request a password reset, you can safely ignore this email.</p>
			</td></tr>
			</table>
			</td></tr>
			</table>
			</body>
			</html>
			""";
}
