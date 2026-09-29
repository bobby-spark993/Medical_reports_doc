package com.prescriptionscanner.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.prescriptionscanner.dto.CreateUserRequest;
import com.prescriptionscanner.dto.OkResponse;
import com.prescriptionscanner.dto.UpdateUserRequest;
import com.prescriptionscanner.dto.UserDto;
import com.prescriptionscanner.security.SecurityUser;
import com.prescriptionscanner.service.UserService;

import jakarta.validation.Valid;

/** Staff account management. Every endpoint is restricted to admins. */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	public List<UserDto> list(@RequestParam(value = "search", required = false) String search) {
		return userService.list(search);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public UserDto create(@Valid @RequestBody CreateUserRequest request,
			@AuthenticationPrincipal SecurityUser user) {
		return userService.create(request, user.getId());
	}

	@PutMapping("/{id}")
	public UserDto update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request,
			@AuthenticationPrincipal SecurityUser user) {
		return userService.update(id, request, user.getId());
	}

	@DeleteMapping("/{id}")
	public OkResponse delete(@PathVariable Long id, @AuthenticationPrincipal SecurityUser user) {
		userService.delete(id, user.getId());
		return OkResponse.success();
	}
}
