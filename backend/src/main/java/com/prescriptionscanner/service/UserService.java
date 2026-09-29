package com.prescriptionscanner.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.prescriptionscanner.domain.Role;
import com.prescriptionscanner.domain.User;
import com.prescriptionscanner.dto.ApiError;
import com.prescriptionscanner.dto.CreateUserRequest;
import com.prescriptionscanner.dto.UpdateUserRequest;
import com.prescriptionscanner.dto.UserDto;
import com.prescriptionscanner.exception.ApiException;
import com.prescriptionscanner.repository.UserRepository;

/**
 * Admin-only user management.
 *
 * <p>Safety rails: nobody can delete their own account, drop their own admin
 * role, or remove the last admin - each would otherwise lock everyone out.
 */
@Service
public class UserService {

	private static final int MIN_PASSWORD_LENGTH = 8;

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final AuditService auditService;

	public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder,
			AuditService auditService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.auditService = auditService;
	}

	@Transactional(readOnly = true)
	public List<UserDto> list(String search) {
		Sort byName = Sort.by(Sort.Direction.ASC, "name");
		String term = search == null ? "" : search.trim();

		List<User> users = term.isEmpty()
				? userRepository.findAll(byName)
				: userRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
						term, term, byName);

		return users.stream().map(UserDto::from).toList();
	}

	@Transactional
	public UserDto create(CreateUserRequest request, Long actorId) {
		Role role = requireRole(request.role());
		String email = normalizeEmail(request.email());

		if (userRepository.existsByEmailIgnoreCase(email)) {
			throw ApiException.conflict("A user with that email already exists.");
		}

		String password = requirePassword(request.password());

		User user = new User();
		user.setName(request.name().trim());
		user.setEmail(email);
		user.setRole(role);
		user.setPasswordHash(passwordEncoder.encode(password));

		User saved = userRepository.save(user);
		auditService.record(actorId, AuditService.CREATE, "User", saved.getId());
		return UserDto.from(saved);
	}

	@Transactional
	public UserDto update(Long id, UpdateUserRequest request, Long actorId) {
		User user = userRepository.findById(id)
				.orElseThrow(() -> ApiException.notFound("No such user."));

		Role role = requireRole(request.role());
		String email = normalizeEmail(request.email());

		if (!user.getEmail().equalsIgnoreCase(email) && userRepository.existsByEmailIgnoreCase(email)) {
			throw ApiException.conflict("A user with that email already exists.");
		}

		if (user.getId().equals(actorId) && role != Role.ADMIN) {
			throw ApiException.badRequest("You cannot remove your own admin access.");
		}

		boolean demotingAdmin = user.getRole() == Role.ADMIN && role != Role.ADMIN;
		if (demotingAdmin && userRepository.countByRole(Role.ADMIN) <= 1) {
			throw ApiException.badRequest("At least one admin must remain.");
		}

		user.setName(request.name().trim());
		user.setEmail(email);
		user.setRole(role);

		if (request.password() != null && !request.password().isBlank()) {
			user.setPasswordHash(passwordEncoder.encode(requirePassword(request.password())));
		}

		auditService.record(actorId, AuditService.UPDATE, "User", user.getId());
		return UserDto.from(user);
	}

	@Transactional
	public void delete(Long id, Long actorId) {
		User user = userRepository.findById(id)
				.orElseThrow(() -> ApiException.notFound("No such user."));

		if (user.getId().equals(actorId)) {
			throw ApiException.badRequest("You cannot delete your own account.");
		}

		if (user.getRole() == Role.ADMIN && userRepository.countByRole(Role.ADMIN) <= 1) {
			throw ApiException.badRequest("At least one admin must remain.");
		}

		userRepository.delete(user);
		auditService.record(actorId, AuditService.DELETE, "User", id);
	}

	private static String normalizeEmail(String email) {
		return email == null ? "" : email.trim().toLowerCase();
	}

	private static String requirePassword(String password) {
		String value = password == null ? "" : password;
		if (value.length() < MIN_PASSWORD_LENGTH) {
			throw ApiException.badRequest(
					"Password must be at least " + MIN_PASSWORD_LENGTH + " characters.",
					List.of(new ApiError.FieldIssue("password",
							"Must be at least " + MIN_PASSWORD_LENGTH + " characters")));
		}
		return value;
	}

	private static Role requireRole(String raw) {
		if (raw == null || raw.isBlank()) {
			throw ApiException.badRequest("Role is required.",
					List.of(new ApiError.FieldIssue("role", "Role is required")));
		}
		String normalized = raw.trim().toLowerCase();
		for (Role role : Role.values()) {
			if (role.value().equals(normalized)) {
				return role;
			}
		}
		throw ApiException.badRequest("Unknown role.",
				List.of(new ApiError.FieldIssue("role", "Must be admin, doctor or receptionist")));
	}
}
