package com.prescriptionscanner.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * A one-time password reset token (OTP). The code itself is never stored: only
 * a bcrypt hash, so a leaked table does not leak working codes.
 */
@Entity
@Table(name = "password_resets", indexes = {
		@Index(name = "password_resets_email_idx", columnList = "email")
})
public class PasswordReset extends BaseEntity {

	@Column(nullable = false, length = 180)
	private String email;

	@Column(nullable = false, length = 120)
	private String otpHash;

	@Column(nullable = false)
	private Instant expiresAt;

	/** When the OTP was successfully redeemed; null while it is still valid. */
	private Instant usedAt;

	public String getEmail() { return email; }
	public void setEmail(String email) { this.email = email; }
	public String getOtpHash() { return otpHash; }
	public void setOtpHash(String otpHash) { this.otpHash = otpHash; }
	public Instant getExpiresAt() { return expiresAt; }
	public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
	public Instant getUsedAt() { return usedAt; }
	public void setUsedAt(Instant usedAt) { this.usedAt = usedAt; }
}