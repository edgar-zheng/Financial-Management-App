package com.edgar.portfolio.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.edgar.portfolio.entity.Portfolio;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
}
