package com.edgar.portfolio.service;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.mockito.ArgumentCaptor;
import com.edgar.portfolio.entity.User;
import com.edgar.portfolio.repository.UserRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegistrationServiceTests {
	private final UserRepository users = mock(UserRepository.class);
	private final PasswordEncoder encoder = mock(PasswordEncoder.class);
	private final RegistrationService service = new RegistrationService(users, encoder);

	@Test
	void savesEncodedPasswordAndNormalizedEmail() {
		when(encoder.encode("long-test-password")).thenReturn("encoded-test-password");
		service.register(" Alice@Example.com ", "long-test-password");
		var saved = ArgumentCaptor.forClass(User.class);
		verify(users).saveAndFlush(saved.capture());
		assertEquals("alice@example.com", saved.getValue().getEmail());
		assertEquals("encoded-test-password", saved.getValue().getPasswordHash());
	}
	@Test
	void rejectsMultibytePasswordsOverBcryptLimitBeforeEncoding() {
		var error = assertThrows(ResponseStatusException.class, () -> service.register("a@example.com", "é".repeat(37)));
		assertEquals(400, error.getStatusCode().value());
		verifyNoInteractions(users, encoder);
	}
	@Test
	void convertsUniqueConstraintFailureToConflictWithoutLeakingDatabaseDetails() {
		when(encoder.encode(anyString())).thenReturn("encoded");
		when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("sensitive database details"));
		var error = assertThrows(ResponseStatusException.class, () -> service.register("a@example.com", "long-test-password"));
		assertEquals(409, error.getStatusCode().value());
		assertFalse(error.getReason().contains("sensitive"));
	}
}
