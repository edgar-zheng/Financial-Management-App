package com.edgar.portfolio.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.edgar.portfolio.entity.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
}
