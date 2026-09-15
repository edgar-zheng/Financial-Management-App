package com.edgar.portfolio.service;

import java.nio.charset.StandardCharsets;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.edgar.portfolio.entity.User;
import com.edgar.portfolio.repository.UserRepository;

@Service
public class RegistrationService {
	private final UserRepository users;
	private final PasswordEncoder encoder;
	public RegistrationService(UserRepository users, PasswordEncoder encoder) {
		this.users = users; this.encoder = encoder;
	}
	@Transactional
	public void register(String email, String password) {
		if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at most 72 UTF-8 bytes");
		}
		try {
			users.saveAndFlush(new User(email, encoder.encode(password)));
		} catch (DataIntegrityViolationException exception) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Unable to register this email");
		}
	}
}
