package com.edgar.portfolio.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.edgar.portfolio.dto.TargetAllocationDto;
import com.edgar.portfolio.entity.TargetAllocation;
import com.edgar.portfolio.repository.TargetAllocationRepository;

@Service
public class AllocationService {
	private final PortfolioAccessService access;
	private final TargetAllocationRepository targets;
	public AllocationService(PortfolioAccessService access, TargetAllocationRepository targets) {
		this.access = access; this.targets = targets;
	}
	@Transactional(readOnly = true)
	public List<TargetAllocationDto> getTargets(Long id) {
		access.requireOwned(id);
		return targets.findByPortfolioIdOrderBySymbolAsc(id).stream()
				.map(target -> new TargetAllocationDto(target.getSymbol(), target.getTargetPercent())).toList();
	}
	@Transactional
	public List<TargetAllocationDto> saveTargets(Long id, List<TargetAllocationDto> requested) {
		var portfolio = access.requireOwnedForUpdate(id);
		var normalized = new TreeMap<String, BigDecimal>();
		BigDecimal total = BigDecimal.ZERO;
		for (var target : requested) {
			String symbol = target.symbol().strip().toUpperCase(Locale.ROOT);
			if (!symbol.matches("[A-Z][A-Z0-9.-]{0,31}") || normalized.containsKey(symbol)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or duplicate target symbol");
			}
			normalized.put(symbol, target.targetPercent());
			total = total.add(target.targetPercent());
		}
		if (!requested.isEmpty() && total.compareTo(new BigDecimal("100")) != 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target percentages must total 100");
		}
		targets.deleteAll(targets.findByPortfolioIdOrderBySymbolAsc(id));
		targets.flush();
		normalized.forEach((symbol, percent) -> targets.save(new TargetAllocation(portfolio, symbol, percent)));
		return normalized.entrySet().stream().map(entry -> new TargetAllocationDto(entry.getKey(), entry.getValue())).toList();
	}
}
