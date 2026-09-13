package com.edgar.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import com.edgar.portfolio.client.MarketDataClient;
import com.edgar.portfolio.controller.MarketDataController;
import com.edgar.portfolio.dto.HoldingDto;
import com.edgar.portfolio.dto.MarketPriceDto;
import com.edgar.portfolio.exception.GlobalExceptionHandler;
import com.edgar.portfolio.service.HoldingService;
import com.edgar.portfolio.service.MarketDataService;
import com.edgar.portfolio.service.HoldingValuationService;
import com.edgar.portfolio.config.MarketCacheConfiguration;
import java.time.Duration;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MarketDataEndpointTests {
	@Test
	void loadsPricesForHeldSymbols() throws Exception {
		var client = mock(MarketDataClient.class);
		var holdings = mock(HoldingService.class);
		when(holdings.getHoldings(1L)).thenReturn(List.of(new HoldingDto("AAPL", BigDecimal.TEN)));
		when(client.getLatestClosingPrice("AAPL")).thenReturn(new MarketPriceDto("AAPL", new BigDecimal("245.30"),
				Instant.parse("2026-09-11T20:00:00Z"), "USD", "MASSIVE", "PREVIOUS_CLOSE"));
		var market = new MarketDataService(client, new MarketCacheConfiguration().closingPricesCache(Duration.ofHours(6)));
		var mvc = MockMvcBuilders.standaloneSetup(new MarketDataController(market, new HoldingValuationService(holdings, market)))
				.setControllerAdvice(new GlobalExceptionHandler()).build();
		mvc.perform(get("/api/portfolios/1/prices")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].symbol").value("AAPL"))
				.andExpect(jsonPath("$[0].price").value(245.30));
		mvc.perform(get("/api/market/prices/aapl")).andExpect(status().isOk())
				.andExpect(jsonPath("$.priceType").value("PREVIOUS_CLOSE"));
		mvc.perform(get("/api/portfolios/1/holdings/valuation")).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].quantity").value(10))
				.andExpect(jsonPath("$[0].closingPrice").value(245.30))
				.andExpect(jsonPath("$[0].marketValue").value(2453.00));
		verify(client, times(1)).getLatestClosingPrice("AAPL");
	}

	@Test
	void handlesMissingEmptyAndInvalidRequestsWithoutProviderCalls() throws Exception {
		var client = mock(MarketDataClient.class);
		var holdings = mock(HoldingService.class);
		when(holdings.getHoldings(1L)).thenReturn(List.of());
		when(holdings.getHoldings(-1L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found"));
		var market = new MarketDataService(client, new MarketCacheConfiguration().closingPricesCache(Duration.ofHours(6)));
		var mvc = MockMvcBuilders.standaloneSetup(new MarketDataController(market, new HoldingValuationService(holdings, market)))
				.setControllerAdvice(new GlobalExceptionHandler()).build();
		mvc.perform(get("/api/portfolios/1/prices")).andExpect(status().isOk()).andExpect(content().json("[]"));
		mvc.perform(get("/api/portfolios/-1/prices")).andExpect(status().isNotFound());
		mvc.perform(get("/api/market/prices/!!!")).andExpect(status().isBadRequest());
		verifyNoInteractions(client);
	}
}
