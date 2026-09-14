package com.edgar.portfolio;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.edgar.portfolio.repository.UserRepository;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class SecurityIntegrationTests {
	@Autowired WebApplicationContext context;
	@Autowired UserRepository users;
	@Autowired PasswordEncoder encoder;
	@Test
	void registrationLoginProtectedAccessAndLogout() throws Exception {
		var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		String email = UUID.randomUUID() + "@example.com";
		String password = "Test-only-password-123";
		mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/portfolios/1")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.token").isString());
		String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
		mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
		mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
		var saved = users.findByEmailIgnoreCase(email).orElseThrow();
		assertNotEquals(password, saved.getPasswordHash()); assertTrue(encoder.matches(password, saved.getPasswordHash()));
		mvc.perform(post("/api/auth/login").with(csrf()).param("email", email).param("password", "wrong"))
				.andExpect(status().isUnauthorized());
		var login = mvc.perform(post("/api/auth/login").with(csrf()).param("email", email).param("password", password))
				.andExpect(status().isNoContent()).andReturn();
		var session = (MockHttpSession) login.getRequest().getSession(false);
		assertNotNull(session);
		mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
		mvc.perform(post("/api/portfolios").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Authenticated portfolio\"}")).andExpect(status().isCreated());
		mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
		assertTrue(session.isInvalid());
		mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
	}
}
