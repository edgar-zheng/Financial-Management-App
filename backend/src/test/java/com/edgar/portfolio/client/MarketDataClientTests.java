package com.edgar.portfolio.client;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class MarketDataClientTests {
	private MockRestServiceServer server;
	private MarketDataClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder().baseUrl("https://api.massive.com");
		server = MockRestServiceServer.bindTo(builder).build();
		client = new MarketDataClient("test-only-key", builder.build());
	}

	@Test
	void mapsClosingPriceAndMillisecondTimestampAndUsesHeaderAuthentication() {
		server.expect(requestTo("https://api.massive.com/v2/aggs/ticker/AAPL/prev?adjusted=true"))
				.andExpect(header("Authorization", "Bearer test-only-key"))
				.andRespond(withSuccess("""
					{"ticker":"AAPL","status":"OK","results":[{"c":129.8473,"t":1605042000000,"T":"AAPL","o":120}]}
					""", MediaType.APPLICATION_JSON));
		var price = client.getLatestClosingPrice("AAPL");
		assertEquals(new BigDecimal("129.8473"), price.price());
		assertEquals(Instant.ofEpochMilli(1605042000000L), price.asOf());
		assertEquals("PREVIOUS_CLOSE", price.priceType());
		server.verify();
	}

	@Test
	void sanitizesProviderFailures() {
		for (HttpStatus status : new HttpStatus[] {HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN,
				HttpStatus.TOO_MANY_REQUESTS, HttpStatus.INTERNAL_SERVER_ERROR}) {
			server.reset();
			server.expect(anything()).andRespond(withStatus(status).body("secret provider details"));
			var error = assertThrows(ResponseStatusException.class, () -> client.getLatestClosingPrice("AAPL"));
			assertEquals(status == HttpStatus.INTERNAL_SERVER_ERROR ? 502 : 503, error.getStatusCode().value());
			assertFalse(error.getReason().contains("secret"));
			if (status.value() == 401 || status.value() == 403 || status.value() == 429) {
				assertTrue(error.getReason().contains(String.valueOf(status.value())));
			}
			server.verify();
		}
	}

	@Test
	void rejectsMissingKeyWithoutNetworkCall() {
		var missing = new MarketDataClient("", RestClient.create());
		assertEquals(503, assertThrows(ResponseStatusException.class,
				() -> missing.getLatestClosingPrice("AAPL")).getStatusCode().value());
	}

	@Test
	void handlesEmptyResultsAsNotFound() {
		server.expect(anything()).andRespond(withSuccess("{\"status\":\"OK\",\"results\":[]}", MediaType.APPLICATION_JSON));
		assertEquals(404, assertThrows(ResponseStatusException.class,
				() -> client.getLatestClosingPrice("AAPL")).getStatusCode().value());
	}

	@Test
	void returnsFirstValidCloseWithoutInventingMissingTimestamp() {
		server.expect(anything()).andRespond(withSuccess("""
				{"ticker":"AAPL","results":[{"c":null},{"c":-1},{"c":233.40}]}
				""", MediaType.APPLICATION_JSON));
		var price = client.getLatestClosingPrice("AAPL");
		assertEquals(new BigDecimal("233.40"), price.price());
		assertNull(price.asOf());
	}

	@Test
	void rejectsMalformedAndInvalidResults() {
		for (String json : new String[] {"not json", "{\"results\":[{\"c\":-1}]}",
				"{\"ticker\":\"MSFT\",\"results\":[{\"c\":1}]}"}) {
			server.reset();
			server.expect(anything()).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
			assertEquals(502, assertThrows(ResponseStatusException.class,
					() -> client.getLatestClosingPrice("AAPL")).getStatusCode().value());
		}
	}
}
