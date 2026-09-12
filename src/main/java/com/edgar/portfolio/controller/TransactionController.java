package com.edgar.portfolio.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.edgar.portfolio.dto.CreateTransactionRequest;
import com.edgar.portfolio.dto.TransactionResponse;
import com.edgar.portfolio.service.TransactionService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/portfolios/{id}/transactions")
public class TransactionController {

	private final TransactionService transactionService;

	public TransactionController(TransactionService transactionService) {
		this.transactionService = transactionService;
	}

	@PostMapping
	public ResponseEntity<TransactionResponse> createTransaction(@PathVariable("id") Long id,
			@Valid @RequestBody CreateTransactionRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(transactionService.createTransaction(id, request));
	}

	@GetMapping
	public List<TransactionResponse> getTransactions(@PathVariable("id") Long id) {
		return transactionService.getTransactions(id);
	}
}
