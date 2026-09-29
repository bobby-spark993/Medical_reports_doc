package com.prescriptionscanner.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.prescriptionscanner.config.AppProperties;
import com.prescriptionscanner.dto.AuthResponse;
import com.prescriptionscanner.dto.LoginRequest;
import com.prescriptionscanner.dto.OkResponse;
import com.prescriptionscanner.dto.UserDto;
import com.prescriptionscanner.exception.ApiException;
import com.prescriptionscanner.security.JwtAuthenticationFilter;
import com.prescriptionscanner.security.JwtService;
import com.prescriptionscanner.security.SecurityUser;
import com.prescriptionscanner.service.AuditService;
import com.prescriptionscanner.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
	private final JwtService jwtService;
	private final AppProperties props;
	private final AuditService auditService;

	public AuthController(AuthService authService, JwtService jwtService, AppProperties props,
			AuditService auditService) {
		this.authService = authService;
		this.jwtService = jwtService;
		this.props = props;
		this.auditService = auditService;
	}

	@PostMapping("/login")
	public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
			HttpServletRequest http) {

		AuthService.LoginResult result = authService.login(request, ClientIp.of(http));

		ResponseCookie cookie = ResponseCookie.from(JwtAuthenticationFilter.COOKIE_NAME, result.token())
				.httpOnly(true)
				.secure(props.getJwt().isCookieSecure())
				.sameSite(props.getJwt().getCookieSameSite())
				.path("/")
				.maxAge(jwtService.expirationSeconds())
				.build();

		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, cookie.toString())
				.body(AuthResponse.of(result.user()));
	}

	@PostMapping("/logout")
	public ResponseEntity<OkResponse> logout(@AuthenticationPrincipal SecurityUser user) {
		if (user != null) {
			auditService.record(user.getId(), AuditService.LOGOUT, "User", user.getId());
		}

		ResponseCookie cleared = ResponseCookie.from(JwtAuthenticationFilter.COOKIE_NAME, "")
				.httpOnly(true)
				.secure(props.getJwt().isCookieSecure())
				.sameSite(props.getJwt().getCookieSameSite())
				.path("/")
				.maxAge(0)
				.build();

		return ResponseEntity.ok()
				.header(HttpHeaders.SET_COOKIE, cleared.toString())
				.body(OkResponse.success());
	}

	/**
	 * Lets the SPA restore the session on a page refresh. Not in the original
	 * spec, but without it a single-page app cannot know who is logged in.
	 */
	@GetMapping("/me")
	public ResponseEntity<AuthResponse> me(@AuthenticationPrincipal SecurityUser user) {
		if (user == null) {
			throw ApiException.unauthorized("Not authenticated");
		}
		return ResponseEntity.ok(AuthResponse.of(
				new UserDto(user.getId(), user.getName(), user.getEmail(), user.getRole())));
	}
}
