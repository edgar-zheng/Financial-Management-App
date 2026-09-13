package com.edgar.portfolio;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import com.edgar.portfolio.controller.TransactionController;
import com.edgar.portfolio.entity.Portfolio;
import com.edgar.portfolio.repository.PortfolioRepository;
import com.edgar.portfolio.repository.TransactionRepository;
import jakarta.persistence.EntityManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class TransactionEndpointTests {

	@Autowired private TransactionController controller;
	@Autowired private PortfolioRepository portfolios;
	@Autowired private TransactionRepository transactions;
	@Autowired private EntityManager entityManager;
	private MockMvc mvc;
	private Long portfolioId;
	private static final String VALID = """
			{"symbol":"AAPL","type":"BUY","quantity":1.25,"price":200.12345678}
			""";

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new com.edgar.portfolio.exception.GlobalExceptionHandler()).build();
		portfolioId = portfolios.saveAndFlush(new Portfolio("Endpoint test")).getId();
	}

	private String path(Long id) {
		return "/api/portfolios/" + id + "/transactions";
	}

	@Test
	void createsAndReadsOnlyThisPortfoliosHistory() throws Exception {
		Long otherId = portfolios.saveAndFlush(new Portfolio("Other portfolio")).getId();
		mvc.perform(post(path(otherId)).contentType(MediaType.APPLICATION_JSON).content(VALID))
				.andExpect(status().isCreated());
		mvc.perform(post(path(portfolioId)).contentType(MediaType.APPLICATION_JSON).content(VALID))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.portfolioId").value(portfolioId))
				.andExpect(jsonPath("$.timestamp").isString())
				.andExpect(jsonPath("$.portfolio").doesNotExist());
		mvc.perform(post(path(portfolioId)).contentType(MediaType.APPLICATION_JSON)
				.content(VALID.replace("BUY", "SELL")))
				.andExpect(status().isCreated());
		entityManager.flush();
		entityManager.clear();
		mvc.perform(get(path(portfolioId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].symbol").value("AAPL"))
				.andExpect(jsonPath("$[0].quantity").value(1.25))
				.andExpect(jsonPath("$[0].price").value(200.12345678))
				.andExpect(jsonPath("$[0].type").value("BUY"))
				.andExpect(jsonPath("$[1].type").value("SELL"));
	}

	@Test
	void returnsEmptyHistoryForExistingPortfolio() throws Exception {
		mvc.perform(get(path(portfolioId)))
				.andExpect(status().isOk()).andExpect(content().json("[]"));
	}

	@Test
	void returns404ForMissingPortfolio() throws Exception {
		mvc.perform(get(path(-1L))).andExpect(status().isNotFound());
		mvc.perform(post(path(-1L)).contentType(MediaType.APPLICATION_JSON).content(VALID))
				.andExpect(status().isNotFound());
	}

	@Test
	void rejectsInvalidRequestsWithoutSaving() throws Exception {
		long before = transactions.count();
		for (String body : new String[] {"{}", VALID.replace("AAPL", "   "),
				VALID.replace("BUY", "INVALID"), VALID.replace("1.25", "0"), VALID.replace("1.25", "-1"),
				VALID.replace("1.25", "null"), VALID.replace("200.12345678", "-1"),
				VALID.replace("1.25", "1.123456789")}) {
			mvc.perform(post(path(portfolioId)).contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
		}
		assertEquals(before, transactions.count());
	}
	@Test
	void rejectsOversellsAndAllowsSellingExactBalance() throws Exception {
		mvc.perform(post(path(portfolioId)).contentType(MediaType.APPLICATION_JSON).content(VALID))
				.andExpect(status().isCreated());
		long before = transactions.count();
		mvc.perform(post(path(portfolioId)).contentType(MediaType.APPLICATION_JSON)
				.content(VALID.replace("BUY", "SELL").replace("1.25", "2")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));
		assertEquals(before, transactions.count());
		mvc.perform(post(path(portfolioId)).contentType(MediaType.APPLICATION_JSON)
				.content(VALID.replace("BUY", "SELL").replace("AAPL", " aapl ")))
				.andExpect(status().isCreated());
	}

	@Test
	void cannotSellSharesOwnedOnlyInAnotherPortfolio() throws Exception {
		Long other = portfolios.saveAndFlush(new Portfolio("Other")).getId();
		mvc.perform(post(path(other)).contentType(MediaType.APPLICATION_JSON).content(VALID))
				.andExpect(status().isCreated());
		mvc.perform(post(path(portfolioId)).contentType(MediaType.APPLICATION_JSON)
				.content(VALID.replace("BUY", "SELL")))
				.andExpect(status().isConflict());
	}

}
