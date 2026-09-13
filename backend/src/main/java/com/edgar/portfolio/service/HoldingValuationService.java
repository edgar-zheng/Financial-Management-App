package com.edgar.portfolio.service;

import java.util.List;
import org.springframework.stereotype.Service;
import com.edgar.portfolio.dto.HoldingValuationDto;
import com.edgar.portfolio.dto.MarketPriceDto;

@Service
public class HoldingValuationService {
	private final HoldingService holdings;
	private final MarketDataService marketData;

	public HoldingValuationService(HoldingService holdings, MarketDataService marketData) {
		this.holdings = holdings;
		this.marketData = marketData;
	}

	public List<HoldingValuationDto> getValuations(Long portfolioId) {
		// Existing quantity calculation completes its DB transaction before external requests.
		return holdings.getHoldings(portfolioId).stream().map(holding -> {
			MarketPriceDto price = marketData.getLatestClosingPrice(holding.symbol());
			return new HoldingValuationDto(holding.symbol(), holding.quantity(), price.price(),
					holding.quantity().multiply(price.price()), price.asOf(), price.currency(), price.priceType());
		}).toList();
	}

	public List<MarketPriceDto> getPortfolioPrices(Long portfolioId) {
		return holdings.getHoldings(portfolioId).stream()
				.map(holding -> marketData.getLatestClosingPrice(holding.symbol())).toList();
	}
}
