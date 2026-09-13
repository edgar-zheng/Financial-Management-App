package com.edgar.portfolio.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record HoldingValuationDto(String symbol, BigDecimal quantity, BigDecimal closingPrice,
		BigDecimal marketValue, Instant asOf, String currency, String priceType) {
}
