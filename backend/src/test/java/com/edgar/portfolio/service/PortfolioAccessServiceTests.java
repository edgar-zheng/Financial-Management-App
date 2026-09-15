package com.edgar.portfolio.service;

import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import com.edgar.portfolio.entity.User;
import com.edgar.portfolio.entity.Portfolio;
import com.edgar.portfolio.repository.UserRepository;
import com.edgar.portfolio.repository.PortfolioRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortfolioAccessServiceTests {
	private final UserRepository users = mock(UserRepository.class);
	private final PortfolioRepository portfolios = mock(PortfolioRepository.class);
	private final PortfolioAccessService service = new PortfolioAccessService(users, portfolios);
	@AfterEach void clear() { SecurityContextHolder.clearContext(); }
	private User authenticate() {
		var user = new User("alice@example.com", "test-hash");
		ReflectionTestUtils.setField(user, "id", 7L);
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				user.getEmail(), null, AuthorityUtils.createAuthorityList("ROLE_USER")));
		when(users.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
		return user;
	}
	@Test void rejectsMissingAndAnonymousAuthentication() {
		SecurityContextHolder.clearContext();
		assertEquals(401, assertThrows(ResponseStatusException.class, service::currentUser).getStatusCode().value());
		SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("test", "anonymousUser",
				AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
		assertEquals(401, assertThrows(ResponseStatusException.class, service::currentUser).getStatusCode().value());
		verifyNoInteractions(users, portfolios);
	}
	@Test void scopesReadsAndLockedWritesToAuthenticatedUser() {
		var user = authenticate();
		var portfolio = new Portfolio("Owned", user);
		when(portfolios.findByIdAndOwnerId(1L, 7L)).thenReturn(Optional.of(portfolio));
		when(portfolios.findOwnedByIdForUpdate(1L, 7L)).thenReturn(Optional.of(portfolio));
		assertSame(portfolio, service.requireOwned(1L));
		assertSame(portfolio, service.requireOwnedForUpdate(1L));
		verify(portfolios, never()).findById(anyLong());
	}
	@Test void hidesOtherUsersAndMissingPortfoliosWith404() {
		authenticate();
		assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.requireOwned(99L)).getStatusCode().value());
		assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.requireOwnedForUpdate(99L)).getStatusCode().value());
	}
	@Test void rejectsSessionForUserNoLongerInDatabase() {
		authenticate();
		when(users.findByEmailIgnoreCase("alice@example.com")).thenReturn(Optional.empty());
		assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.requireOwned(1L)).getStatusCode().value());
		verifyNoInteractions(portfolios);
	}
}
