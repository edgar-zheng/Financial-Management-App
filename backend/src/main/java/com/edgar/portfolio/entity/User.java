package com.edgar.portfolio.entity;

import java.time.LocalDateTime;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "users")
public class User {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank
	@Email
	@Size(max = 254)
	@Column(nullable = false, unique = true, length = 254)
	private String email;

	@NotBlank
	@Size(max = 255)
	@JsonIgnore
	@Column(nullable = false, length = 255)
	private String passwordHash;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	protected User() {}

	/** passwordHash must already be encoded by the authentication service, never a raw password. */
	public User(String email, String passwordHash) {
		this.email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
		this.passwordHash = passwordHash;
	}

	@PrePersist
	private void setCreationTimestamp() {
		createdAt = LocalDateTime.now();
	}

	public Long getId() { return id; }
	public String getEmail() { return email; }
	@JsonIgnore
	public String getPasswordHash() { return passwordHash; }
	public LocalDateTime getCreatedAt() { return createdAt; }
}
