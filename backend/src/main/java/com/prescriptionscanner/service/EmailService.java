package com.prescriptionscanner.service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.prescriptionscanner.config.AppProperties;

import jakarta.mail.MessagingException;
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

	/** Matches an SMTP reply such as "535-5.7.8" or "535 5.7.8". */
	private static final Pattern SMTP_REPLY = Pattern.compile("\\b\\d{3}[- ]\\d\\.\\d+\\.\\d+\\b");

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
	 * Outcome of one send attempt. {@code sent} is false when SMTP is missing or
	 * the server refused the message; {@code failure} then holds the provider's
	 * own reason (for example Gmail's "535-5.7.8 Username and Password not
	 * accepted"), which is safe for logs but never shown to the user.
	 */
	public record Delivery(boolean sent, String failure) {
		static Delivery ok() {
			return new Delivery(true, null);
		}

		static Delivery error(String failure) {
			return new Delivery(false, failure);
		}
	}

	/**
	 * Emails a password-reset OTP as branded HTML with the clinic logo embedded
	 * inline. The result distinguishes "delivered" from "refused", so the caller
	 * can tell the user instead of pretending the code was sent.
	 */
	public Delivery sendPasswordResetOtp(String to, String otp, long expiresInMinutes) {
		JavaMailSender sender = senderProvider.getIfAvailable();
		if (sender == null) {
			return Delivery.error("no JavaMailSender bean is configured");
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
			helper.setSubject("Your password reset code: " + otp);
			helper.setText(text, html);
			if (logo.isPresent()) {
				StorageService.LoadedFile file = logo.get();
				helper.addInline(LOGO_CID, new ByteArrayResource(file.data()), file.contentType());
			}
			sender.send(message);
			return Delivery.ok();
		} catch (Exception ex) {
			String reason = describe(ex);
			log.warn("Could not send email to {}: {}", to, reason);
			return Delivery.error(reason);
		}
	}

	/**
	 * Picks the most useful message out of the mail exception chain. Spring wraps
	 * JavaMail failures (MailSendException -> AuthenticationFailedException) and
	 * JavaMail nests the server's reply in {@code nextException}, so both chains
	 * are walked. The SMTP reply, e.g. "535-5.7.8 Username and Password not
	 * accepted", is preferred because it says what actually needs fixing.
	 */
	private static String describe(Exception ex) {
		List<String> messages = new ArrayList<>();
		collectMessages(ex, messages, 0);
		if (messages.isEmpty()) {
			return ex.getClass().getSimpleName();
		}
		for (String message : messages) {
			if (SMTP_REPLY.matcher(message).find()) {
				return message;
			}
		}
		// No SMTP reply: the deepest message is normally the most specific one.
		return messages.get(messages.size() - 1);
	}

	private static void collectMessages(Throwable ex, List<String> out, int depth) {
		if (ex == null || depth > 8) {
			return;
		}
		String message = normalize(ex.getMessage());
		if (message != null && !out.contains(message)) {
			out.add(message);
		}
		if (ex instanceof MessagingException mail) {
			collectMessages(mail.getNextException(), out, depth + 1);
		}
		collectMessages(ex.getCause(), out, depth + 1);
	}

	/** SMTP replies are multi-line; keep each log entry and JSON message on one line. */
	private static String normalize(String message) {
		if (message == null || message.isBlank()) {
			return null;
		}
		return message.replaceAll("\\s+", " ").trim();
	}

	/**
	 * Gmail threads messages that share a subject and the same participants, and
	 * a muted or archived thread keeps later messages out of the inbox - which
	 * silently hides every new OTP. Putting the code in the subject keeps each
	 * message in its own conversation (and shows the code without opening it).
	 */
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
