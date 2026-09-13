package com.edgar.portfolio.dto;

import java.math.BigDecimal;

public record HoldingDto(String symbol, BigDecimal quantity) {
}
