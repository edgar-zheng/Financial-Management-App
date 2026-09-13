package com.edgar.portfolio.entity;

import java.math.BigDecimal;
import jakarta.persistence.*;

@Entity
@Table(name = "target_allocations", uniqueConstraints = @UniqueConstraint(columnNames = {"portfolio_id", "symbol"}))
public class TargetAllocation {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "portfolio_id", nullable = false)
	private Portfolio portfolio;
	@Column(nullable = false, length = 32)
	private String symbol;
	@Column(nullable = false, precision = 7, scale = 4)
	private BigDecimal targetPercent;
	protected TargetAllocation() {}
	public TargetAllocation(Portfolio portfolio, String symbol, BigDecimal targetPercent) {
		this.portfolio = portfolio; this.symbol = symbol; this.targetPercent = targetPercent;
	}
	public String getSymbol() { return symbol; }
	public BigDecimal getTargetPercent() { return targetPercent; }
}
