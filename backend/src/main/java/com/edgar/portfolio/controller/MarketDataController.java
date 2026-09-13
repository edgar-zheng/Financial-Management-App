package com.edgar.portfolio.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edgar.portfolio.dto.MarketPriceDto;
import com.edgar.portfolio.service.MarketDataService;
import com.edgar.portfolio.service.HoldingValuationService;
import com.edgar.portfolio.dto.HoldingValuationDto;

@RestController
@RequestMapping("/api")
public class MarketDataController {

	private final MarketDataService marketData;
	private final HoldingValuationService valuations;

	public MarketDataController(MarketDataService marketData, HoldingValuationService valuations) {
		this.marketData = marketData;
		this.valuations = valuations;
	}

	@GetMapping("/market/prices/{symbol}")
	public MarketPriceDto getPrice(@PathVariable("symbol") String symbol) {
		return marketData.getLatestClosingPrice(symbol);
	}

	@GetMapping("/portfolios/{id}/prices")
	public List<MarketPriceDto> getPortfolioPrices(@PathVariable("id") Long id) {
		return valuations.getPortfolioPrices(id);
	}

	@GetMapping("/portfolios/{id}/holdings/valuation")
	public List<HoldingValuationDto> getValuations(@PathVariable("id") Long id) {
		return valuations.getValuations(id);
	}
}
