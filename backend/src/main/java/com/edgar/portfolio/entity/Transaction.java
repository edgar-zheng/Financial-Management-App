package com.edgar.portfolio.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "transactions")
public class Transaction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "portfolio_id", nullable = false)
	private Portfolio portfolio;

	@NotBlank
	@Size(max = 32)
	@Column(nullable = false, length = 32)
	private String symbol;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private TransactionType type;

	@NotNull
	@Positive
	@Digits(integer = 11, fraction = 8)
	@Column(nullable = false, precision = 19, scale = 8)
	private BigDecimal quantity;

	@NotNull
	@Positive
	@Digits(integer = 11, fraction = 8)
	@Column(nullable = false, precision = 19, scale = 8)
	private BigDecimal price;

	@Column(nullable = false, updatable = false)
	private LocalDateTime timestamp;

	protected Transaction() {
	}

	public Transaction(Portfolio portfolio, String symbol, TransactionType type,
			BigDecimal quantity, BigDecimal price) {
		this.portfolio = portfolio;
		this.symbol = symbol;
		this.type = type;
		this.quantity = quantity;
		this.price = price;
	}

	@PrePersist
	private void setTimestamp() {
		timestamp = LocalDateTime.now();
	}

	public Long getId() { return id; }
	public Portfolio getPortfolio() { return portfolio; }
	public String getSymbol() { return symbol; }
	public TransactionType getType() { return type; }
	public BigDecimal getQuantity() { return quantity; }
	public BigDecimal getPrice() { return price; }
	public LocalDateTime getTimestamp() { return timestamp; }
}
