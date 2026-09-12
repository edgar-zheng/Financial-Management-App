package com.edgar.portfolio.service;

import org.springframework.stereotype.Service;

import com.edgar.portfolio.entity.Portfolio;
import com.edgar.portfolio.repository.PortfolioRepository;

@Service
public class PortfolioService {

	private final PortfolioRepository portfolioRepository;

	public PortfolioService(PortfolioRepository portfolioRepository) {
		this.portfolioRepository = portfolioRepository;
	}

	public Portfolio createPortfolio(String name) {
		return portfolioRepository.save(new Portfolio(name));
	}
}
