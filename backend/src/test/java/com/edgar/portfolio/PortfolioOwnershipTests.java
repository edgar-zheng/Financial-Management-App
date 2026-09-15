package com.edgar.portfolio;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.http.MediaType;
import com.edgar.portfolio.entity.User;
import com.edgar.portfolio.entity.Portfolio;
import com.edgar.portfolio.repository.UserRepository;
import com.edgar.portfolio.repository.PortfolioRepository;
import com.edgar.portfolio.repository.TransactionRepository;
import com.edgar.portfolio.repository.TargetAllocationRepository;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class PortfolioOwnershipTests {
	@Autowired WebApplicationContext context;
	@Autowired UserRepository users;
	@Autowired PortfolioRepository portfolios;
	@Autowired TransactionRepository transactions;
	@Autowired TargetAllocationRepository targets;

	@Test
	void isolatesEveryPortfolioRouteAndAssignsOwnerOnCreation() throws Exception {
		var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		var alice = users.saveAndFlush(new User(UUID.randomUUID() + "@example.com", "test-hash"));
		var bob = users.saveAndFlush(new User(UUID.randomUUID() + "@example.com", "test-hash"));
		var result = mvc.perform(post("/api/portfolios").with(user(alice.getEmail())).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Private\",\"ownerId\":" + bob.getId() + "}"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.owner").doesNotExist()).andReturn();
		Long id = JsonMapper.builder().build().readTree(result.getResponse().getContentAsString()).get("id").longValue();
		assertTrue(portfolios.findByIdAndOwnerId(id, alice.getId()).isPresent());
		assertTrue(portfolios.findByIdAndOwnerId(id, bob.getId()).isEmpty());
		for (String suffix : new String[] {"", "/transactions", "/holdings", "/prices", "/holdings/valuation", "/analytics", "/allocations", "/allocations/drift"}) {
			mvc.perform(get("/api/portfolios/" + id + suffix).with(user(bob.getEmail())))
					.andExpect(status().isNotFound());
			mvc.perform(get("/api/portfolios/" + id + suffix).with(user(alice.getEmail())))
					.andExpect(status().isOk());
		}
		long before = transactions.count();
		mvc.perform(post("/api/portfolios/" + id + "/transactions").with(user(bob.getEmail())).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"symbol\":\"AAPL\",\"type\":\"BUY\",\"quantity\":1,\"price\":100}"))
				.andExpect(status().isNotFound());
		mvc.perform(put("/api/portfolios/" + id + "/allocations").with(user(bob.getEmail())).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"targets\":[{\"symbol\":\"AAPL\",\"targetPercent\":100}]}"))
				.andExpect(status().isNotFound());
		assertEquals(before, transactions.count());
		assertTrue(targets.findByPortfolioIdOrderBySymbolAsc(id).isEmpty());
		var legacy = portfolios.saveAndFlush(new Portfolio("Legacy"));
		mvc.perform(get("/api/portfolios/" + legacy.getId()).with(user(alice.getEmail()))).andExpect(status().isNotFound());
		mvc.perform(get("/api/portfolios/" + id)).andExpect(status().isUnauthorized());
	}
}
