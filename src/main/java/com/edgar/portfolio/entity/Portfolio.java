package com.edgar.portfolio.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "portfolios")
public class Portfolio {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank
	@Column(nullable = false)
	private String name;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected Portfolio() {
	}

	public Portfolio(String name) {
		this.name = name;
	}

	@PrePersist
	private void setCreationTimestamp() {
		createdAt = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
