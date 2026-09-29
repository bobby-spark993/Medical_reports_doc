package com.prescriptionscanner.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for PUT /api/users/{id} (admin only).
 *
 * <p>{@code password} is optional: leave it null or blank to keep the current
 * password, or supply a new value to reset it.
 */
public record UpdateUserRequest(

		@NotBlank(message = "Name is required")
		@Size(max = 120)
		String name,

		@NotBlank(message = "Email is required")
		@Email(message = "Enter a valid email address")
		@Size(max = 180)
		String email,

		@NotBlank(message = "Role is required")
		String role,

		@Size(max = 100)
		String password) {
}
