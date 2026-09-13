package com.edgar.portfolio.dto;

import java.math.BigDecimal;

public record AllocationDriftDto(String symbol, BigDecimal targetPercent,
		BigDecimal actualPercent, BigDecimal driftPercentagePoints) {}
