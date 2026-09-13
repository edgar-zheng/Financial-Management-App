package com.edgar.portfolio.repository;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.edgar.portfolio.entity.Portfolio;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Portfolio p where p.id = :id")
	Optional<Portfolio> findByIdForUpdate(@Param("id") Long id);
}
