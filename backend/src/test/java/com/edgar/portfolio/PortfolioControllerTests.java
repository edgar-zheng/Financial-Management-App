package com.edgar.portfolio;

import java.time.LocalDateTime;
import com.edgar.portfolio.service.PortfolioAccessService;
import com.edgar.portfolio.entity.User;
import com.edgar.portfolio.exception.GlobalExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.edgar.portfolio.controller.PortfolioController;
import com.edgar.portfolio.entity.Portfolio;
import com.edgar.portfolio.repository.PortfolioRepository;
import com.edgar.portfolio.service.PortfolioService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PortfolioControllerTests {

	private PortfolioRepository repository;
	private MockMvc mvc;
	private PortfolioAccessService access;

	@BeforeEach
	void setUp() {
		repository = mock(PortfolioRepository.class);
		access = mock(PortfolioAccessService.class);
		when(access.currentUser()).thenReturn(new User("test@example.com", "test-only-hash"));
		mvc = MockMvcBuilders.standaloneSetup(
				new PortfolioController(new PortfolioService(repository, access))).setControllerAdvice(new GlobalExceptionHandler()).build();
	}

	@Test
	void createsPortfolioAndReturnsGeneratedFields() throws Exception {
		when(repository.save(any(Portfolio.class))).thenAnswer(invocation -> {
			Portfolio portfolio = invocation.getArgument(0);
			assertEquals("Long-Term Investments", portfolio.getName());
			ReflectionTestUtils.setField(portfolio, "id", 1L);
			ReflectionTestUtils.setField(portfolio, "createdAt", LocalDateTime.of(2026, 9, 11, 22, 0));
			return portfolio;
		});

		mvc.perform(post("/api/portfolios").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"Long-Term Investments\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.name").value("Long-Term Investments"))
				.andExpect(jsonPath("$.createdAt").isString());
		verify(repository).save(any(Portfolio.class));
	}

	@Test
	void rejectsBlankNullAndMissingNamesBeforePersistence() throws Exception {
		for (String body : new String[] {"{\"name\":\"   \"}", "{\"name\":\"\"}", "{\"name\":null}", "{}"}) {
			mvc.perform(post("/api/portfolios").contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isBadRequest());
		}
		verifyNoInteractions(repository);
	}

	@Test
	void retrievesExistingPortfolio() throws Exception {
		Portfolio portfolio = new Portfolio("Long-Term Investments");
		ReflectionTestUtils.setField(portfolio, "id", 1L);
		ReflectionTestUtils.setField(portfolio, "createdAt", LocalDateTime.of(2026, 9, 11, 22, 0));
		when(access.requireOwned(1L)).thenReturn(portfolio);

		mvc.perform(get("/api/portfolios/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.name").value("Long-Term Investments"))
				.andExpect(jsonPath("$.createdAt").value("2026-09-11T22:00:00"));
		verify(access).requireOwned(1L);
	}

	@Test
	void returnsNotFoundForMissingPortfolio() throws Exception {
		when(access.requireOwned(999L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found"));

		mvc.perform(get("/api/portfolios/999"))
				.andExpect(status().isNotFound());
		verify(access).requireOwned(999L);
	}
}
