package com.prescriptionscanner.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload for POST /api/users (admin only). */
public record CreateUserRequest(

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

		@NotBlank(message = "Role is required")
		String role) {
}
