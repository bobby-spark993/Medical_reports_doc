package com.prescriptionscanner.dto;

public record AuthResponse(boolean ok, UserDto user) {

	public static AuthResponse of(UserDto user) {
		return new AuthResponse(true, user);
	}
}
