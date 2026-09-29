package com.prescriptionscanner.dto;

import com.prescriptionscanner.domain.User;

public record UserDto(Long id, String name, String email, String role) {

	public static UserDto from(User user) {
		return new UserDto(user.getId(), user.getName(), user.getEmail(),
				user.getRole() == null ? null : user.getRole().value());
	}
}
