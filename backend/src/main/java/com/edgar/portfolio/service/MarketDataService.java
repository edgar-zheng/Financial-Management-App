package com.edgar.portfolio.service;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.edgar.portfolio.client.MarketDataClient;
import com.edgar.portfolio.dto.MarketPriceDto;

@Service
public class MarketDataService {
	private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);
	private final MarketDataClient client;
	private final Cache closingPrices;

	public MarketDataService(MarketDataClient client, CaffeineCache closingPricesCache) {
		this.client = client;
		this.closingPrices = closingPricesCache;
	}

	public MarketPriceDto getLatestClosingPrice(String symbol) {
		String normalized = symbol == null ? "" : symbol.strip().toUpperCase(Locale.ROOT);
		if (!normalized.matches("[A-Z][A-Z0-9.-]{0,31}")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid stock symbol.");
		}
		try {
			// Atomic loading coalesces concurrent requests for the same symbol. Failures aren't cached.
			return closingPrices.get(normalized, () -> {
				log.debug("Closing-price cache miss for {}", normalized);
				return client.getLatestClosingPrice(normalized);
			});
		} catch (Cache.ValueRetrievalException exception) {
			if (exception.getCause() instanceof ResponseStatusException status) throw status;
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not retrieve closing price.");
		}
	}
}
