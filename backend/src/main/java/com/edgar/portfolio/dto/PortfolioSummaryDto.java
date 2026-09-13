package com.edgar.portfolio.dto;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioSummaryDto(Long portfolioId, String currency, String priceType,
		BigDecimal totalMarketValue, BigDecimal totalCostBasis, BigDecimal unrealizedGainLoss,
		BigDecimal realizedGainLoss, BigDecimal totalGainLoss, List<AssetAllocationDto> assets) {}
