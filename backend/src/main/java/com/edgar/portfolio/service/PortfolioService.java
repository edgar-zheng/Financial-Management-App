package com.edgar.portfolio.service;

import org.springframework.transaction.annotation.Transactional;
import com.edgar.portfolio.dto.PortfolioResponse;

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

	@Transactional
	public PortfolioResponse createPortfolio(String name) {
		return PortfolioResponse.from(portfolioRepository.save(new Portfolio(name, access.currentUser())));
	}

	@Transactional(readOnly = true)
	public PortfolioResponse getPortfolioById(Long id) {
		return PortfolioResponse.from(access.requireOwned(id));
	}
}
