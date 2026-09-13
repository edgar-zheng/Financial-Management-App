package com.edgar.portfolio.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketPriceDto(String symbol, BigDecimal price, Instant asOf,
		String currency, String source, String priceType) {
}
