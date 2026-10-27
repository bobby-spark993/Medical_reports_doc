package com.prescriptionscanner.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import com.prescriptionscanner.config.AppProperties;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/**
 * The "could not send" path matters: the SMTP server's own reply is what tells
 * an operator that the app password is wrong, so it must survive the trip out
 * of the exception chain.
 */
class EmailServiceTest {

	private static final AppProperties PROPS = new AppProperties();

	@Test
	void reportsSuccessWhenTheServerAcceptsTheMessage() {
		JavaMailSender sender = mock(JavaMailSender.class);
		when(sender.createMimeMessage()).thenReturn(newMessage());

		EmailService.Delivery delivery = service(sender, "smtp.gmail.com")
				.sendPasswordResetOtp("patient@example.com", "123456", 10);

		assertThat(delivery.sent()).isTrue();
		assertThat(delivery.failure()).isNull();
	}

	@Test
	void surfacesTheSmtpReplyWhenTheServerRefusesTheLogin() {
		JavaMailSender sender = mock(JavaMailSender.class);
		when(sender.createMimeMessage()).thenReturn(newMessage());

		AuthenticationFailedException refused = new AuthenticationFailedException("Authentication failed");
		refused.setNextException(new MessagingException(
				"535-5.7.8 Username and Password not accepted. For more information, go to\n"
						+ "535 5.7.8 https://support.google.com/mail/?p=BadCredentials a92af - gsmtp"));
		// Spring wraps JavaMail failures, so the reply sits one level deeper.
		doThrow(new MailSendException("Mail server connection failed", refused))
				.when(sender).send(any(MimeMessage.class));

		EmailService.Delivery delivery = service(sender, "smtp.gmail.com")
				.sendPasswordResetOtp("patient@example.com", "123456", 10);

		assertThat(delivery.sent()).isFalse();
		assertThat(delivery.failure())
				.startsWith("535-5.7.8 Username and Password not accepted.")
				.contains("https://support.google.com/mail/?p=BadCredentials")
				.doesNotContain("\n");
	}

	@Test
	void putsTheCodeInTheSubjectSoGmailDoesNotThreadCodesTogether() throws Exception {
		JavaMailSender sender = mock(JavaMailSender.class);
		MimeMessage first = newMessage();
		MimeMessage second = newMessage();
		when(sender.createMimeMessage()).thenReturn(first, second);

		EmailService service = service(sender, "smtp.gmail.com");
		service.sendPasswordResetOtp("patient@example.com", "111111", 10);
		service.sendPasswordResetOtp("patient@example.com", "222222", 10);

		assertThat(first.getSubject()).isEqualTo("Your password reset code: 111111");
		assertThat(second.getSubject()).isNotEqualTo(first.getSubject());
	}

	@Test
	void isUnavailableWithoutSmtp() {
		assertThat(service(null, "").isAvailable()).isFalse();
		assertThat(service(null, "smtp.gmail.com").isAvailable()).isFalse();
		assertThat(service(null, "").sendPasswordResetOtp("patient@example.com", "123456", 10).sent())
				.isFalse();
	}

	private static EmailService service(JavaMailSender sender, String host) {
		@SuppressWarnings("unchecked")
		ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(sender);

		SettingsService settings = mock(SettingsService.class);
		when(settings.logo()).thenReturn(Optional.empty());

		return new EmailService(provider, host, "clinic@gmail.com", PROPS, settings);
	}

	private static MimeMessage newMessage() {
		return new MimeMessage(Session.getInstance(new Properties()));
	}
}
