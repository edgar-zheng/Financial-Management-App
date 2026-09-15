package com.edgar.portfolio.controller;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import com.edgar.portfolio.dto.PortfolioSummaryDto;
import com.edgar.portfolio.exception.GlobalExceptionHandler;
import com.edgar.portfolio.service.PortfolioAnalyticsService;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PortfolioAnalyticsControllerTests {
	private final PortfolioAnalyticsService service = mock(PortfolioAnalyticsService.class);
	private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders.standaloneSetup(new PortfolioAnalyticsController(service))
			.setControllerAdvice(new GlobalExceptionHandler()).build();
	@Test void returnsAnalyticsJson() throws Exception {
		when(service.getAnalytics(7L)).thenReturn(new PortfolioSummaryDto(7L, "USD", "PREVIOUS_CLOSE", new BigDecimal("5000"),
				new BigDecimal("4000"), new BigDecimal("1000"), BigDecimal.ZERO, new BigDecimal("1000"), List.of()));
		mvc.perform(get("/api/portfolios/7/analytics")).andExpect(status().isOk())
				.andExpect(jsonPath("$.totalMarketValue").value(5000)).andExpect(jsonPath("$.totalGainLoss").value(1000));
	}
	@Test void rejectsNonNumericIdBeforeService() throws Exception {
		mvc.perform(get("/api/portfolios/invalid/analytics")).andExpect(status().isBadRequest());
		verifyNoInteractions(service);
	}
	@Test void preservesNotFoundAndProviderFailureStatus() throws Exception {
		for (HttpStatus status : new HttpStatus[] {HttpStatus.NOT_FOUND, HttpStatus.SERVICE_UNAVAILABLE}) {
			doThrow(new ResponseStatusException(status, "Unavailable")).when(service).getAnalytics(7L);
			mvc.perform(get("/api/portfolios/7/analytics")).andExpect(status().is(status.value()))
					.andExpect(jsonPath("$.status").value(status.value()));
		}
	}
}
