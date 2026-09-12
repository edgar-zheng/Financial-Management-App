package com.edgar.portfolio.service;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.edgar.portfolio.dto.CreateTransactionRequest;
import com.edgar.portfolio.dto.TransactionResponse;
import com.edgar.portfolio.entity.Portfolio;
import com.edgar.portfolio.entity.Transaction;
import com.edgar.portfolio.repository.PortfolioRepository;
import com.edgar.portfolio.repository.TransactionRepository;

@Service
public class TransactionService {

	private final PortfolioRepository portfolios;
	private final TransactionRepository transactions;

	public TransactionService(PortfolioRepository portfolios, TransactionRepository transactions) {
		this.portfolios = portfolios;
		this.transactions = transactions;
	}

	@Transactional
	public TransactionResponse createTransaction(Long portfolioId, CreateTransactionRequest request) {
		Portfolio portfolio = requirePortfolio(portfolioId);
		Transaction transaction = new Transaction(portfolio, request.symbol(), request.type(),
				request.quantity(), request.price());
		return TransactionResponse.from(transactions.save(transaction));
	}

	@Transactional(readOnly = true)
	public List<TransactionResponse> getTransactions(Long portfolioId) {
		requirePortfolio(portfolioId);
		return transactions.findByPortfolioIdOrderByTimestampAscIdAsc(portfolioId).stream()
				.map(TransactionResponse::from).toList();
	}

	private Portfolio requirePortfolio(Long id) {
		return portfolios.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found"));
	}
}
