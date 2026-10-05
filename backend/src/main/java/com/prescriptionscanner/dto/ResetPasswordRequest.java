package com.prescriptionscanner.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload for the public POST /api/auth/reset-password. */
public record ResetPasswordRequest(

		@NotBlank(message = "Email is required")
		@Email(message = "Enter a valid email address")
		@Size(max = 180)
		String email,

		@NotBlank(message = "OTP is required")
		@Size(max = 10)
		String otp,

		@NotBlank(message = "Password is required")
		@Size(max = 100)
		String password) {
}