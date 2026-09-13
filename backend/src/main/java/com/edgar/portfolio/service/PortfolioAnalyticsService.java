package com.edgar.portfolio.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.edgar.portfolio.dto.AssetAllocationDto;
import com.edgar.portfolio.dto.PortfolioSummaryDto;
import com.edgar.portfolio.entity.TransactionType;

@Service
public class PortfolioAnalyticsService {
	private final TransactionService transactions;
	private final HoldingValuationService valuations;

	public PortfolioAnalyticsService(TransactionService transactions, HoldingValuationService valuations) {
		this.transactions = transactions;
		this.valuations = valuations;
	}

	public PortfolioSummaryDto getAnalytics(Long portfolioId) {
		Map<String, Position> positions = new TreeMap<>();
		BigDecimal realized = BigDecimal.ZERO;
		for (var transaction : transactions.getTransactions(portfolioId)) {
			String symbol = transaction.symbol().strip().toUpperCase(Locale.ROOT);
			Position position = positions.computeIfAbsent(symbol, ignored -> new Position());
			BigDecimal quantity = transaction.quantity();
			BigDecimal amount = quantity.multiply(transaction.price());
			if (transaction.type() == TransactionType.BUY) {
				position.quantity = position.quantity.add(quantity);
				position.cost = position.cost.add(amount);
			} else {
				if (quantity.compareTo(position.quantity) > 0) {
					throw new ResponseStatusException(HttpStatus.CONFLICT, "Transaction history sells more shares than owned for " + symbol);
				}
				// Moving weighted-average cost; full liquidation removes all residual cost.
				BigDecimal removedCost = quantity.compareTo(position.quantity) == 0 ? position.cost
						: position.cost.multiply(quantity).divide(position.quantity, MathContext.DECIMAL128);
				realized = realized.add(amount.subtract(removedCost));
				position.quantity = position.quantity.subtract(quantity);
				position.cost = position.cost.subtract(removedCost);
			}
		}

		var holdings = valuations.getValuations(portfolioId);
		BigDecimal total = holdings.stream().map(holding -> holding.marketValue())
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		BigDecimal cost = BigDecimal.ZERO;
		var assets = new ArrayList<AssetAllocationDto>();
		for (var holding : holdings) {
			Position position = positions.get(holding.symbol());
			if (position == null || position.quantity.compareTo(holding.quantity()) != 0) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "Portfolio changed while calculating analytics. Refresh to retry.");
			}
			cost = cost.add(position.cost);
			BigDecimal weight = total.signum() == 0 ? BigDecimal.ZERO
					: holding.marketValue().multiply(BigDecimal.valueOf(100)).divide(total, 6, RoundingMode.HALF_UP);
			assets.add(new AssetAllocationDto(holding.symbol(), holding.quantity(), holding.closingPrice(),
					holding.marketValue(), weight, position.cost, holding.marketValue().subtract(position.cost), holding.asOf()));
		}
		long openPositions = positions.values().stream().filter(position -> position.quantity.signum() != 0).count();
		if (openPositions != holdings.size()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Portfolio changed while calculating analytics. Refresh to retry.");
		}
		BigDecimal unrealized = total.subtract(cost);
		return new PortfolioSummaryDto(portfolioId, "USD", "PREVIOUS_CLOSE", total, cost,
				unrealized, realized, unrealized.add(realized), assets);
	}

	private static class Position {
		BigDecimal quantity = BigDecimal.ZERO;
		BigDecimal cost = BigDecimal.ZERO;
	}
}
