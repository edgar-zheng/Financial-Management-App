package com.edgar.portfolio;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import com.edgar.portfolio.controller.HoldingController;
import com.edgar.portfolio.entity.Portfolio;
import com.edgar.portfolio.entity.Transaction;
import com.edgar.portfolio.entity.TransactionType;
import com.edgar.portfolio.repository.PortfolioRepository;
import com.edgar.portfolio.repository.TransactionRepository;
import com.edgar.portfolio.service.HoldingService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class HoldingEndpointTests {

	@Autowired private HoldingController controller;
	@Autowired private HoldingService service;
	@Autowired private PortfolioRepository portfolios;
	@Autowired private TransactionRepository transactions;
	private MockMvc mvc;
	private Portfolio portfolio;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new com.edgar.portfolio.exception.GlobalExceptionHandler()).build();
		portfolio = portfolios.saveAndFlush(new Portfolio("Holdings test"));
	}

	private void add(Portfolio owner, String symbol, TransactionType type, String quantity) {
		transactions.saveAndFlush(new Transaction(owner, symbol, type,
				new BigDecimal(quantity), new BigDecimal("100")));
	}

	@Test
	void aggregatesBySymbolAndIsolatesPortfolios() throws Exception {
		add(portfolio, "MSFT", TransactionType.BUY, "2");
		add(portfolio, "AAPL", TransactionType.BUY, "10");
		add(portfolio, " aapl ", TransactionType.BUY, "5");
		add(portfolio, "AAPL", TransactionType.SELL, "3");
		Portfolio other = portfolios.saveAndFlush(new Portfolio("Other holdings"));
		add(other, "AAPL", TransactionType.BUY, "100");
		mvc.perform(get("/api/portfolios/{id}/holdings", portfolio.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].symbol").value("AAPL"))
				.andExpect(jsonPath("$[0].quantity").value(12))
				.andExpect(jsonPath("$[1].symbol").value("MSFT"))
				.andExpect(jsonPath("$[1].quantity").value(2));
	}

	@Test
	void preservesFractionalPrecisionOmitsZeroAndKeepsNegativeBalances() {
		add(portfolio, "AAPL", TransactionType.BUY, "0.12345678");
		add(portfolio, "AAPL", TransactionType.SELL, "0.02345677");
		add(portfolio, "MSFT", TransactionType.BUY, "1");
		add(portfolio, "MSFT", TransactionType.SELL, "1");
		add(portfolio, "TSLA", TransactionType.SELL, "2");
		var holdings = service.getHoldings(portfolio.getId());
		assertEquals(2, holdings.size());
		assertEquals("AAPL", holdings.get(0).symbol());
		assertEquals(0, new BigDecimal("0.10000001").compareTo(holdings.get(0).quantity()));
		assertEquals("TSLA", holdings.get(1).symbol());
		assertEquals(0, new BigDecimal("-2").compareTo(holdings.get(1).quantity()));
	}

	@Test
	void returnsEmptyArrayWithoutTransactions() throws Exception {
		mvc.perform(get("/api/portfolios/{id}/holdings", portfolio.getId()))
				.andExpect(status().isOk()).andExpect(content().json("[]"));
	}

	@Test
	void returns404ForMissingPortfolio() throws Exception {
		mvc.perform(get("/api/portfolios/-1/holdings"))
				.andExpect(status().isNotFound());
	}
}
