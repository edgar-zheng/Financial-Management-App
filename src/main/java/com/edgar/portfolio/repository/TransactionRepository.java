package com.edgar.portfolio.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.edgar.portfolio.entity.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

	List<Transaction> findByPortfolioIdOrderByTimestampAscIdAsc(Long portfolioId);
}
