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
	@Autowired tools.jackson.databind.json.JsonMapper json;

	private record BrowserCsrf(MockHttpSession session, String header, String token) {}
	private BrowserCsrf browserCsrf(org.springframework.test.web.servlet.MockMvc mvc, MockHttpSession session) throws Exception {
		var request = get("/api/auth/csrf");
		if (session != null) request.session(session);
		var result = mvc.perform(request).andExpect(status().isOk())
				.andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
				.andExpect(jsonPath("$.token").isNotEmpty()).andReturn();
		var body = json.readTree(result.getResponse().getContentAsString());
		return new BrowserCsrf((MockHttpSession) result.getRequest().getSession(false),
				body.get("headerName").asText(), body.get("token").asText());
	}

	@Test
	void browserTokensProtectRegistrationAndRotateAcrossLoginAndLogout() throws Exception {
		var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		var anonymous = browserCsrf(mvc, null);
		assertNotNull(anonymous.session());
		String email = UUID.randomUUID() + "@example.com";
		String password = "Test-only-password-123";
		String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
		mvc.perform(post("/api/auth/register").session(anonymous.session()).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isForbidden());
		mvc.perform(post("/api/auth/register").session(anonymous.session()).header(anonymous.header(), "invalid")
				.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
		mvc.perform(post("/api/auth/register").session(anonymous.session()).header(anonymous.header(), anonymous.token())
				.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
		mvc.perform(post("/api/auth/login").session(anonymous.session()).param("email", email).param("password", password))
				.andExpect(status().isForbidden());
		mvc.perform(post("/api/auth/login").session(anonymous.session()).header(anonymous.header(), anonymous.token())
				.param("email", email).param("password", password)).andExpect(status().isNoContent());
		mvc.perform(post("/api/portfolios").session(anonymous.session()).header(anonymous.header(), anonymous.token())
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Stale token\"}"))
				.andExpect(status().isForbidden());
		var authenticated = browserCsrf(mvc, anonymous.session());
		mvc.perform(post("/api/portfolios").session(authenticated.session()).header(authenticated.header(), authenticated.token())
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Fresh token\"}"))
				.andExpect(status().isCreated());
		mvc.perform(post("/api/auth/logout").session(authenticated.session()).header(authenticated.header(), authenticated.token()))
				.andExpect(status().isNoContent());
		assertTrue(authenticated.session().isInvalid());
		var loggedOut = browserCsrf(mvc, null);
		mvc.perform(get("/api/auth/me").session(loggedOut.session())).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/auth/login").session(loggedOut.session()).header(authenticated.header(), authenticated.token())
				.param("email", email).param("password", password)).andExpect(status().isForbidden());
		mvc.perform(post("/api/auth/login").session(loggedOut.session()).header(loggedOut.header(), loggedOut.token())
				.param("email", email).param("password", password)).andExpect(status().isNoContent());
		browserCsrf(mvc, loggedOut.session());
		mvc.perform(get("/api/auth/me").session(loggedOut.session())).andExpect(status().isOk());
	}
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
