package com.prescriptionscanner.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

		@NotBlank(message = "Email is required")
		@Email(message = "Enter a valid email address")
		@Size(max = 180)
		String email,

		@NotBlank(message = "Password is required")
		@Size(max = 200)
		String password) {
}
