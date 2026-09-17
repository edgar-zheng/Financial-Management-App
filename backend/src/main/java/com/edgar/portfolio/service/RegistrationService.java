package com.edgar.portfolio.service;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import com.edgar.portfolio.exception.RegistrationConflictException;
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
			// MySQL duplicate-key violations are the known email uniqueness conflict.
            // Other integrity failures must reach the generic, logged 500 handler.
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sql && sql.getErrorCode() == 1062) {
                    throw new RegistrationConflictException();
                }
            }
            throw exception;
		}
	}
}
