package com.edgar.portfolio.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import com.edgar.portfolio.dto.AllocationDriftDto;

@Service
public class AllocationDriftService {
	private final AllocationService allocations;
	private final PortfolioAnalyticsService analytics;
	public AllocationDriftService(AllocationService allocations, PortfolioAnalyticsService analytics) {
		this.allocations = allocations; this.analytics = analytics;
	}
	public List<AllocationDriftDto> getDrift(Long id) {
		var targets = new TreeMap<String, BigDecimal>();
		allocations.getTargets(id).forEach(target -> targets.put(target.symbol(), target.targetPercent()));
		var actual = new TreeMap<String, BigDecimal>();
		analytics.getAnalytics(id).assets().forEach(asset -> actual.put(asset.symbol(), asset.weightPercent()));
		actual.keySet().forEach(symbol -> targets.putIfAbsent(symbol, BigDecimal.ZERO));
		return targets.entrySet().stream().map(entry -> {
			BigDecimal weight = actual.getOrDefault(entry.getKey(), BigDecimal.ZERO);
			return new AllocationDriftDto(entry.getKey(), entry.getValue(), weight, weight.subtract(entry.getValue()));
		}).toList();
	}
}
