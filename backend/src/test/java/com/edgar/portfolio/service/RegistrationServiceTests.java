package com.edgar.portfolio.service;

import org.junit.jupiter.api.Test;
import com.edgar.portfolio.exception.RegistrationConflictException;
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
		when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("sensitive database details", new java.sql.SQLException("duplicate", "23000", 1062)));
		var error = assertThrows(RegistrationConflictException.class, () -> service.register("a@example.com", "long-test-password"));
		assertFalse(error.getMessage().contains("sensitive"));
	}

    @Test void doesNotMisclassifyUnknownIntegrityFailuresAsDuplicateEmail() {
        when(encoder.encode(anyString())).thenReturn("encoded");
        var failure = new DataIntegrityViolationException("unknown constraint");
        when(users.saveAndFlush(any())).thenThrow(failure);
        assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                () -> service.register("a@example.com", "long-test-password")));
    }
}
