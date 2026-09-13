package com.edgar.portfolio.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.edgar.portfolio.client.MarketDataClient;
import com.edgar.portfolio.dto.HoldingDto;
import com.edgar.portfolio.dto.MarketPriceDto;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MarketDataServiceTests {
	private MarketPriceDto price() {
		return new MarketPriceDto("AAPL", new BigDecimal("233.40"), Instant.parse("2026-09-11T04:00:00Z"),
				"USD", "MASSIVE", "PREVIOUS_CLOSE");
	}

	@Test
	void cachesNormalizedTickerUntilExpiry() {
		var clock = new AtomicLong();
		var cache = new CaffeineCache("test", Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(6))
				.ticker(clock::get).build(), false);
		var client = mock(MarketDataClient.class);
		when(client.getLatestClosingPrice("AAPL")).thenReturn(price());
		var service = new MarketDataService(client, cache);
		assertEquals(price(), service.getLatestClosingPrice(" aapl "));
		assertEquals(price(), service.getLatestClosingPrice("AAPL"));
		verify(client, times(1)).getLatestClosingPrice("AAPL");
		clock.addAndGet(Duration.ofHours(6).toNanos());
		service.getLatestClosingPrice("AAPL");
		verify(client, times(2)).getLatestClosingPrice("AAPL");
	}

	@Test
	void doesNotCacheFailures() {
		var client = mock(MarketDataClient.class);
		when(client.getLatestClosingPrice("AAPL"))
				.thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "rate limited"))
				.thenReturn(price());
		var service = new MarketDataService(client, new CaffeineCache("test", Caffeine.newBuilder().build(), false));
		assertEquals(503, assertThrows(ResponseStatusException.class,
				() -> service.getLatestClosingPrice("AAPL")).getStatusCode().value());
		assertEquals(price(), service.getLatestClosingPrice("AAPL"));
	}

	@Test
	void valuesExistingHoldingsWithExactDecimalMultiplication() {
		var holdings = mock(HoldingService.class);
		var market = mock(MarketDataService.class);
		when(holdings.getHoldings(1L)).thenReturn(List.of(new HoldingDto("AAPL", new BigDecimal("1.12345678"))));
		when(market.getLatestClosingPrice("AAPL")).thenReturn(price());
		var valuation = new HoldingValuationService(holdings, market).getValuations(1L).getFirst();
		assertEquals(new BigDecimal("262.2148124520"), valuation.marketValue());
		assertEquals(price().asOf(), valuation.asOf());
	}
}
