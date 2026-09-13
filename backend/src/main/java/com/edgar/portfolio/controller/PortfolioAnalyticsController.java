package com.edgar.portfolio.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import com.edgar.portfolio.dto.PortfolioSummaryDto;
import com.edgar.portfolio.service.PortfolioAnalyticsService;

@RestController
public class PortfolioAnalyticsController {
	private final PortfolioAnalyticsService analytics;

	public PortfolioAnalyticsController(PortfolioAnalyticsService analytics) {
		this.analytics = analytics;
	}

	@GetMapping("/api/portfolios/{id}/analytics")
	public PortfolioSummaryDto getAnalytics(@PathVariable("id") Long id) {
		return analytics.getAnalytics(id);
	}
}
