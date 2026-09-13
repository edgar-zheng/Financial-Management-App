package com.edgar.portfolio.client;

import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Duration;
import java.time.Instant;
import java.net.http.HttpClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import com.edgar.portfolio.dto.MarketPriceDto;

@Component
public class MarketDataClient {

	private static final Logger log = LoggerFactory.getLogger(MarketDataClient.class);

	private final RestClient client;
	private final String apiKey;

	@org.springframework.beans.factory.annotation.Autowired
	public MarketDataClient(@Value("${market.api.key:}") String apiKey,
			@Value("${market.api.base-url:https://api.massive.com}") String baseUrl) {
		this(apiKey, productionClient(baseUrl));
	}

	// Explicit client injection lets tests use a mock server without real credentials or API calls.
	MarketDataClient(String apiKey, RestClient client) {
		this.apiKey = apiKey;
		this.client = client;
	}

	private static RestClient productionClient(String baseUrl) {
		HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		factory.setReadTimeout(Duration.ofSeconds(10));
		return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
	}

	public MarketPriceDto getLatestClosingPrice(String symbol) {
		if (apiKey == null || apiKey.isBlank()) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Market data is not configured (market.api.key).");
		}
		try {
			log.debug("Requesting previous-session close for {}", symbol);
			AggregateResponse body = client.get().uri("/v2/aggs/ticker/{symbol}/prev?adjusted=true", symbol)
					.headers(headers -> headers.setBearerAuth(apiKey))
					.retrieve()
					.onStatus(status -> status.isError(), (request, response) -> {
						int status = response.getStatusCode().value();
						log.warn("Massive close request failed for {}: HTTP {}", symbol, status);
						if (status == 401) {
							throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
									"Massive rejected market.api.key (provider HTTP 401). Check the configured credential.");
						}
						if (status == 403) {
							throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
									"Massive denied previous-day aggregate access (provider HTTP 403). Check your subscription permissions.");
						}
						if (status == 429) {
							throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
									"Market data rate limit reached (provider HTTP 429). Try again later.");
						}
						if (status == 404) {
							throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Market price not found for " + symbol);
						}
						throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Market data provider is unavailable.");
					}).body(AggregateResponse.class);
			if (body == null || (body.status() != null && !("OK".equals(body.status()) || "DELAYED".equals(body.status())))) {
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Invalid market data response.");
			}
			if (body.ticker() != null && !symbol.equals(body.ticker())) {
				throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Market data ticker mismatch.");
			}
			if (body.results() == null || body.results().isEmpty()) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No closing-price data for " + symbol);
			}
			for (Aggregate aggregate : body.results()) {
				if (aggregate != null && aggregate.c() != null && aggregate.c().signum() > 0
						&& (aggregate.t() == null || aggregate.t() > 0)) {
					// Massive t is the aggregate-window START in milliseconds, not a live quote time.
					Instant asOf = aggregate.t() == null ? null : Instant.ofEpochMilli(aggregate.t());
					return new MarketPriceDto(symbol, aggregate.c(), asOf, "USD", "MASSIVE", "PREVIOUS_CLOSE");
				}
			}
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No valid closing price in market data response.");
		} catch (RestClientException exception) {
			log.warn("Massive close request failed for {}: transport or response decoding error", symbol);
			// Never expose provider bodies, request headers, or exception messages containing credentials.
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not retrieve market data. Try again later.");
		}
	}

	private record AggregateResponse(String ticker, String status, List<Aggregate> results) {}
	private record Aggregate(BigDecimal c, Long t) {}
}
