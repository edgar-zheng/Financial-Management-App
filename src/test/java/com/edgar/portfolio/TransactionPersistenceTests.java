package com.edgar.portfolio;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.edgar.portfolio.entity.Portfolio;
import com.edgar.portfolio.entity.Transaction;
import com.edgar.portfolio.entity.TransactionType;
import com.edgar.portfolio.repository.PortfolioRepository;
import com.edgar.portfolio.repository.TransactionRepository;

import jakarta.persistence.EntityManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Transactional
class TransactionPersistenceTests {

	@Autowired
	private PortfolioRepository portfolios;

	@Autowired
	private TransactionRepository transactions;

	@Autowired
	private EntityManager entityManager;

	@Test
	void persistsTransactionsWithPortfolioAndExactDecimals() {
		Portfolio portfolio = portfolios.save(new Portfolio("Persistence test"));
		Transaction buy = transactions.saveAndFlush(new Transaction(portfolio, "AAPL",
				TransactionType.BUY, new BigDecimal("1.12345678"), new BigDecimal("200.12345678")));
		Transaction sell = transactions.saveAndFlush(new Transaction(portfolio, "AAPL",
				TransactionType.SELL, new BigDecimal("0.5"), new BigDecimal("210.25")));
		Long portfolioId = portfolio.getId();
		Long buyId = buy.getId();
		Long sellId = sell.getId();
		entityManager.clear();

		Transaction loaded = transactions.findById(buyId).orElseThrow();
		assertEquals(portfolioId, loaded.getPortfolio().getId());
		assertEquals("AAPL", loaded.getSymbol());
		assertEquals(TransactionType.BUY, loaded.getType());
		assertEquals(new BigDecimal("1.12345678"), loaded.getQuantity());
		assertEquals(new BigDecimal("200.12345678"), loaded.getPrice());
		assertNotNull(loaded.getTimestamp());
		Transaction loadedSell = transactions.findById(sellId).orElseThrow();
		assertEquals(portfolioId, loadedSell.getPortfolio().getId());
		assertEquals(TransactionType.SELL, loadedSell.getType());
	}
}
