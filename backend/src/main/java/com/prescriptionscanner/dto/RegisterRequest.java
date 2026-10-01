package com.prescriptionscanner.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload for the public POST /api/auth/register. */
public record RegisterRequest(

		@NotBlank(message = "Name is required")
		@Size(max = 120)
		String name,

		@NotBlank(message = "Email is required")
		@Email(message = "Enter a valid email address")
		@Size(max = 180)
		String email,

		@NotBlank(message = "Password is required")
		@Size(max = 100)
		String password,

		/** "admin", "doctor" or "receptionist". */
		String role) {
}
