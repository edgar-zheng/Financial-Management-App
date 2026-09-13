package com.edgar.portfolio.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AssetAllocationDto(String symbol, BigDecimal quantity, BigDecimal closingPrice,
		BigDecimal marketValue, BigDecimal weightPercent, BigDecimal costBasis,
		BigDecimal unrealizedGainLoss, Instant asOf) {}
