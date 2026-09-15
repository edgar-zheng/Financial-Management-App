package com.edgar.portfolio.service;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.edgar.portfolio.dto.CreateTransactionRequest;
import com.edgar.portfolio.entity.Portfolio;
import com.edgar.portfolio.entity.Transaction;
import com.edgar.portfolio.entity.TransactionType;
import com.edgar.portfolio.exception.InsufficientSharesException;
import com.edgar.portfolio.repository.TransactionRepository;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class TransactionServiceTests {
	private final PortfolioAccessService access = mock(PortfolioAccessService.class);
	private final TransactionRepository repository = mock(TransactionRepository.class);
	private final TransactionService service = new TransactionService(access, repository);
	private final Portfolio portfolio = new Portfolio("Test");
	private Transaction trade(String symbol, TransactionType type, String quantity) {
		return new Transaction(portfolio, symbol, type, new BigDecimal(quantity), BigDecimal.TEN);
	}
	@Test void rejectsOversellWithoutSavingAndIgnoresOtherSymbols() {
		when(access.requireOwnedForUpdate(1L)).thenReturn(portfolio);
		when(repository.findByPortfolioIdOrderByTimestampAscIdAsc(1L)).thenReturn(List.of(
				trade(" aapl ", TransactionType.BUY, "1.5"), trade("AAPL", TransactionType.SELL, "0.5"),
				trade("MSFT", TransactionType.BUY, "100")));
		assertThrows(InsufficientSharesException.class, () -> service.createTransaction(1L,
				new CreateTransactionRequest("AAPL", TransactionType.SELL, new BigDecimal("1.00000001"), BigDecimal.TEN)));
		verify(repository, never()).save(any());
	}
	@Test void allowsExactBalanceAndNormalizesSymbol() {
		when(access.requireOwnedForUpdate(1L)).thenReturn(portfolio);
		when(repository.findByPortfolioIdOrderByTimestampAscIdAsc(1L)).thenReturn(List.of(trade("AAPL", TransactionType.BUY, "0.5")));
		when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
		var response = service.createTransaction(1L, new CreateTransactionRequest(" aapl ", TransactionType.SELL, new BigDecimal("0.5"), BigDecimal.TEN));
		assertEquals("AAPL", response.symbol());
		assertEquals(new BigDecimal("0.5"), response.quantity());
		verify(repository).save(any());
	}
	@Test void checksOwnershipBeforeReadingOrWritingTransactions() {
		when(access.requireOwned(2L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
		when(access.requireOwnedForUpdate(2L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
		assertThrows(ResponseStatusException.class, () -> service.getTransactions(2L));
		assertThrows(ResponseStatusException.class, () -> service.createTransaction(2L,
				new CreateTransactionRequest("AAPL", TransactionType.BUY, BigDecimal.ONE, BigDecimal.TEN)));
		verifyNoInteractions(repository);
	}
}
