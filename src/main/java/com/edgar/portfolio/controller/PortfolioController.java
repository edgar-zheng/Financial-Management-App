package com.edgar.portfolio.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edgar.portfolio.dto.CreatePortfolioRequest;
import com.edgar.portfolio.entity.Portfolio;
import com.edgar.portfolio.service.PortfolioService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/portfolios")
public class PortfolioController {

	private final PortfolioService portfolioService;

	public PortfolioController(PortfolioService portfolioService) {
		this.portfolioService = portfolioService;
	}

	@PostMapping
	public ResponseEntity<Portfolio> createPortfolio(@Valid @RequestBody CreatePortfolioRequest request) {
		Portfolio portfolio = portfolioService.createPortfolio(request.name());
		return ResponseEntity.status(HttpStatus.CREATED).body(portfolio);
	}
}
