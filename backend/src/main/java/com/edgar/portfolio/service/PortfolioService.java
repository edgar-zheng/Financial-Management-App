package com.edgar.portfolio.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.edgar.portfolio.entity.Portfolio;
import com.edgar.portfolio.repository.PortfolioRepository;

@Service
public class PortfolioService {

	private final PortfolioRepository portfolioRepository;
	private final PortfolioAccessService access;

	public PortfolioService(PortfolioRepository portfolioRepository, PortfolioAccessService access) {
		this.portfolioRepository = portfolioRepository;
		this.access = access;
	}

	public Portfolio createPortfolio(String name) {
		return portfolioRepository.save(new Portfolio(name, access.currentUser()));
	}

	public Optional<Portfolio> getPortfolioById(Long id) {
		return Optional.of(access.requireOwned(id));
	}
}
