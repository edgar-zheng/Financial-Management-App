package com.edgar.portfolio.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.edgar.portfolio.entity.Portfolio;
import com.edgar.portfolio.entity.User;
import com.edgar.portfolio.repository.PortfolioRepository;
import com.edgar.portfolio.repository.UserRepository;

@Service
public class PortfolioAccessService {
	private final UserRepository users;
	private final PortfolioRepository portfolios;
	public PortfolioAccessService(UserRepository users, PortfolioRepository portfolios) {
		this.users = users; this.portfolios = portfolios;
	}
	public User currentUser() {
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
		}
		return users.findByEmailIgnoreCase(authentication.getName())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
	}
	public Portfolio requireOwned(Long id) {
		return portfolios.findByIdAndOwnerId(id, currentUser().getId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found"));
	}
	// Caller must have an active transaction so the write lock lasts through the mutation.
	public Portfolio requireOwnedForUpdate(Long id) {
		return portfolios.findOwnedByIdForUpdate(id, currentUser().getId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Portfolio not found"));
	}
}
