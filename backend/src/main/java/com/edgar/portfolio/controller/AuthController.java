package com.edgar.portfolio.controller;

import java.security.Principal;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import com.edgar.portfolio.service.RegistrationService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
	private final RegistrationService registration;
	public AuthController(RegistrationService registration) { this.registration = registration; }
	public record RegistrationRequest(@NotBlank @Email @Size(max = 254) String email,
			@NotBlank @Size(min = 12, max = 72) String password) {
		@Override public String toString() { return "RegistrationRequest[redacted]"; }
	}
	@GetMapping("/csrf")
	public Map<String, String> csrf(CsrfToken token) {
		return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
	}
	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public void register(@Valid @RequestBody RegistrationRequest request) {
		registration.register(request.email(), request.password());
	}
	@GetMapping("/me")
	public Map<String, String> me(Principal principal) { return Map.of("email", principal.getName()); }
}
