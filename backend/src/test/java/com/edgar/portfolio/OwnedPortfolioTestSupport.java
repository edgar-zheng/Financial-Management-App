package com.edgar.portfolio;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import com.edgar.portfolio.entity.User;
import com.edgar.portfolio.repository.UserRepository;

abstract class OwnedPortfolioTestSupport {
	@Autowired private UserRepository testUsers;
	protected User owner;
	@BeforeEach
	void authenticateOwner() {
		owner = testUsers.saveAndFlush(new User(UUID.randomUUID() + "@example.com", "test-only-hash"));
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				owner.getEmail(), null, AuthorityUtils.createAuthorityList("ROLE_USER")));
	}
	@AfterEach
	void clearAuthentication() { SecurityContextHolder.clearContext(); }
}
