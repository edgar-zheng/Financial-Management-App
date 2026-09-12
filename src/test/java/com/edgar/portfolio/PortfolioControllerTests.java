package com.edgar.portfolio;

import java.time.LocalDateTime;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PortfolioControllerTests {

	private PortfolioRepository repository;
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		repository = mock(PortfolioRepository.class);
		mvc = MockMvcBuilders.standaloneSetup(
				new PortfolioController(new PortfolioService(repository))).build();
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
}
