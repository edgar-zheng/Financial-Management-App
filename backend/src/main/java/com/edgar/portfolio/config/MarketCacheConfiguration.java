package com.edgar.portfolio.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.github.benmanes.caffeine.cache.Caffeine;

@Configuration
public class MarketCacheConfiguration {
	@Bean
	public CaffeineCache closingPricesCache(@Value("${market.cache.ttl:6h}") Duration ttl) {
		if (ttl.isZero() || ttl.isNegative()) {
			throw new IllegalArgumentException("market.cache.ttl must be positive");
		}
		// Six hours balances daily data freshness against Basic-plan limits. Not a live quote cache.
		return new CaffeineCache("closingPrices", Caffeine.newBuilder()
				.maximumSize(1000).expireAfterWrite(ttl).build(), false);
	}
}
