package com.edgar.portfolio.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.edgar.portfolio.entity.TargetAllocation;

public interface TargetAllocationRepository extends JpaRepository<TargetAllocation, Long> {
	List<TargetAllocation> findByPortfolioIdOrderBySymbolAsc(Long portfolioId);
}
