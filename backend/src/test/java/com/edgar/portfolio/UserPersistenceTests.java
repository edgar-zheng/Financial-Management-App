package com.edgar.portfolio;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import com.edgar.portfolio.entity.User;
import com.edgar.portfolio.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class UserPersistenceTests {
	// Test-only encoded-password fixture; no real credential or authentication flow.
	private static final String HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
	@Autowired private UserRepository users;
	@Autowired private EntityManager entityManager;

	@Test
	void persistsNormalizedEmailAndGeneratedFields() {
		String email = UUID.randomUUID() + "@example.com";
		User saved = users.saveAndFlush(new User("  " + email.toUpperCase() + "  ", HASH));
		Long id = saved.getId();
		assertNotNull(id);
		entityManager.clear();
		User loaded = users.findByEmailIgnoreCase(email.toUpperCase()).orElseThrow();
		assertEquals(id, loaded.getId());
		assertEquals(email, loaded.getEmail());
		assertEquals(HASH, loaded.getPasswordHash());
		assertNotNull(loaded.getCreatedAt());
	}

	@Test
	void enforcesUniqueEmailInDatabase() {
		String email = UUID.randomUUID() + "@example.com";
		users.saveAndFlush(new User(email, HASH));
		assertThrows(DataIntegrityViolationException.class,
				() -> users.saveAndFlush(new User(email.toUpperCase(), HASH)));
	}

	@Test
	void rejectsInvalidEmail() {
		assertThrows(ConstraintViolationException.class,
				() -> users.saveAndFlush(new User("not-an-email", HASH)));
	}

	@Test
	void rejectsBlankPasswordHash() {
		assertThrows(ConstraintViolationException.class,
				() -> users.saveAndFlush(new User("test@example.com", " ")));
	}

	@Test
	void neverSerializesPasswordHash() {
		String json = JsonMapper.builder().build().writeValueAsString(new User("test@example.com", HASH));
		assertFalse(json.contains("passwordHash"));
		assertFalse(json.contains(HASH));
		assertTrue(json.contains("test@example.com"));
	}
}
