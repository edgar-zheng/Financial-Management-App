package com.edgar.portfolio.service;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.edgar.portfolio.dto.TransactionResponse;
import com.edgar.portfolio.dto.HoldingValuationDto;
import com.edgar.portfolio.entity.TransactionType;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class PortfolioAnalyticsServiceTests {
	private TransactionResponse trade(String symbol, TransactionType type, String quantity, String price) {
		return new TransactionResponse(1L, 1L, symbol, type, new BigDecimal(quantity), new BigDecimal(price), null);
	}
	private HoldingValuationDto holding(String symbol, String quantity, String price) {
		var q = new BigDecimal(quantity); var p = new BigDecimal(price);
		return new HoldingValuationDto(symbol, q, p, q.multiply(p), null, "USD", "PREVIOUS_CLOSE");
	}
	private void equal(String expected, BigDecimal actual) {
		assertEquals(0, new BigDecimal(expected).compareTo(actual));
	}

	@Test
	void computesFiveThousandPortfolioWithEqualWeights() {
		var transactions = mock(TransactionService.class);
		var valuations = mock(HoldingValuationService.class);
		when(transactions.getTransactions(1L)).thenReturn(List.of(
				trade("AAPL", TransactionType.BUY, "10", "200"), trade("MSFT", TransactionType.BUY, "5", "400")));
		when(valuations.getValuations(1L)).thenReturn(List.of(holding("AAPL", "10", "250"), holding("MSFT", "5", "500")));
		var summary = new PortfolioAnalyticsService(transactions, valuations).getAnalytics(1L);
		equal("5000", summary.totalMarketValue()); equal("1000", summary.unrealizedGainLoss());
		summary.assets().forEach(asset -> equal("50", asset.weightPercent()));
	}

	@Test
	void usesAverageCostForSalesAndResetsCostOnLiquidation() {
		var transactions = mock(TransactionService.class);
		var valuations = mock(HoldingValuationService.class);
		when(transactions.getTransactions(1L)).thenReturn(List.of(
				trade("AAPL", TransactionType.BUY, "10", "100"), trade("AAPL", TransactionType.BUY, "10", "200"),
				trade("AAPL", TransactionType.SELL, "5", "180")));
		when(valuations.getValuations(1L)).thenReturn(List.of(holding("AAPL", "15", "160")));
		var service = new PortfolioAnalyticsService(transactions, valuations);
		var summary = service.getAnalytics(1L);
		equal("2250", summary.totalCostBasis()); equal("150", summary.realizedGainLoss());
		equal("150", summary.unrealizedGainLoss()); equal("300", summary.totalGainLoss());
		when(transactions.getTransactions(1L)).thenReturn(List.of(trade("AAPL", TransactionType.BUY, "0.5", "100"),
				trade("AAPL", TransactionType.SELL, "0.5", "80")));
		when(valuations.getValuations(1L)).thenReturn(List.of());
		summary = service.getAnalytics(1L);
		equal("0", summary.totalCostBasis()); equal("-10", summary.realizedGainLoss());
		equal("0", summary.totalMarketValue());
	}

	@Test
	void emptyPortfolioHasZeroTotals() {
		var transactions = mock(TransactionService.class);
		var valuations = mock(HoldingValuationService.class);
		when(transactions.getTransactions(1L)).thenReturn(List.of());
		when(valuations.getValuations(1L)).thenReturn(List.of());
		var summary = new PortfolioAnalyticsService(transactions, valuations).getAnalytics(1L);
		equal("0", summary.totalGainLoss()); assertTrue(summary.assets().isEmpty());
	}
}
