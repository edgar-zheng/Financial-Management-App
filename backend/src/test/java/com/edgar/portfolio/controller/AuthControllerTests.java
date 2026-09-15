package com.edgar.portfolio.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import com.edgar.portfolio.exception.GlobalExceptionHandler;
import com.edgar.portfolio.service.RegistrationService;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerTests {
	private final RegistrationService registration = mock(RegistrationService.class);
	private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders.standaloneSetup(new AuthController(registration))
			.setControllerAdvice(new GlobalExceptionHandler()).build();
	@Test void acceptsRegistrationWithoutReturningCredentials() throws Exception {
		mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"test@example.com\",\"password\":\"Test-password-123\"}"))
				.andExpect(status().isCreated()).andExpect(content().string(""));
		verify(registration).register("test@example.com", "Test-password-123");
	}
	@Test void rejectsInvalidEmailAndPasswordBeforeService() throws Exception {
		for (String body : new String[] {"{}", "{\"email\":\"invalid\",\"password\":\"Test-password-123\"}",
				"{\"email\":\"test@example.com\",\"password\":\"short\"}"}) {
			mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors").isMap());
		}
		verifyNoInteractions(registration);
	}
	@Test void translatesDuplicateRegistrationToConflict() throws Exception {
		doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Unable to register this email"))
				.when(registration).register(anyString(), anyString());
		mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"test@example.com\",\"password\":\"Test-password-123\"}"))
				.andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
	}
}
