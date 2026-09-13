package com.edgar.portfolio;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.edgar.portfolio.controller.AllocationController;
import com.edgar.portfolio.exception.GlobalExceptionHandler;
import com.edgar.portfolio.entity.Portfolio;
import com.edgar.portfolio.repository.PortfolioRepository;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class AllocationEndpointTests {
	@Autowired AllocationController controller;
	@Autowired PortfolioRepository portfolios;
	@Test
	void savesValidatesReplacesAndClearsTargets() throws Exception {
		var mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
		Long id = portfolios.saveAndFlush(new Portfolio("Allocation test")).getId();
		String path = "/api/portfolios/" + id + "/allocations";
		String valid = "{\"targets\":[{\"symbol\":\" aapl \",\"targetPercent\":30},{\"symbol\":\"VOO\",\"targetPercent\":70}]}";
		mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isOk());
		mvc.perform(get(path)).andExpect(jsonPath("$[0].symbol").value("AAPL")).andExpect(jsonPath("$[0].targetPercent").value(30));
		for (String body : new String[] { valid.replace(":70", ":60"), valid.replace("VOO", "AAPL"),
				valid.replace(":30", ":-1"), "{}", "{\"targets\":[null]}" }) {
			mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
		}
		mvc.perform(get(path)).andExpect(jsonPath("$.length()").value(2));
		mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isOk());
		mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content("{\"targets\":[]}")).andExpect(status().isOk());
		mvc.perform(get(path)).andExpect(content().json("[]"));
		mvc.perform(get("/api/portfolios/-1/allocations")).andExpect(status().isNotFound());
	}
}
