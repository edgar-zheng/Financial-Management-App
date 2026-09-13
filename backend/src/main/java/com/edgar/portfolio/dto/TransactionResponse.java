package com.edgar.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.edgar.portfolio.entity.Transaction;
import com.edgar.portfolio.entity.TransactionType;

public record TransactionResponse(Long id, Long portfolioId, String symbol,
		TransactionType type, BigDecimal quantity, BigDecimal price, LocalDateTime timestamp) {

	public static TransactionResponse from(Transaction transaction) {
		return new TransactionResponse(transaction.getId(), transaction.getPortfolio().getId(),
				transaction.getSymbol(), transaction.getType(), transaction.getQuantity(),
				transaction.getPrice(), transaction.getTimestamp());
	}
}
