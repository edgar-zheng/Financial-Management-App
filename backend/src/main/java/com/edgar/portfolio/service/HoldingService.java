package com.edgar.portfolio.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.edgar.portfolio.dto.HoldingDto;
import com.edgar.portfolio.entity.Transaction;
import com.edgar.portfolio.repository.PortfolioRepository;
import com.edgar.portfolio.repository.TransactionRepository;

@Service
public class HoldingService {

	private final PortfolioRepository portfolios;
	private final TransactionRepository transactions;

	public HoldingService(PortfolioRepository portfolios, TransactionRepository transactions) {
		this.portfolios = portfolios;
		this.transactions = transactions;
	}

	@Transactional(readOnly = true)
	public List<HoldingDto> getHoldings(Long portfolioId) {
		if (!portfolios.existsById(portfolioId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found");
		}

		Map<String, BigDecimal> quantities = new TreeMap<>();
		for (Transaction transaction : transactions.findByPortfolioIdOrderByTimestampAscIdAsc(portfolioId)) {
			String symbol = transaction.getSymbol().strip().toUpperCase(Locale.ROOT);
			BigDecimal quantity = switch (transaction.getType()) {
				case BUY -> transaction.getQuantity();
				case SELL -> transaction.getQuantity().negate();
			};
			BigDecimal existingQuantity = quantities.get(symbol);
			if (existingQuantity == null) {
				quantities.put(symbol, quantity);
			} else {
				quantities.put(symbol, existingQuantity.add(quantity));
			}
		}

		return quantities.entrySet().stream()
				.filter(entry -> entry.getValue().signum() != 0)
				.map(entry -> new HoldingDto(entry.getKey(), entry.getValue()))
				.toList();
	}
}
