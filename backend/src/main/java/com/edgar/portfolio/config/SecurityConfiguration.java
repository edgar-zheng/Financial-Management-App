package com.edgar.portfolio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import com.edgar.portfolio.repository.UserRepository;

@Configuration
public class SecurityConfiguration {
	@Bean
	PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

	@Bean
	UserDetailsService userDetailsService(UserRepository users) {
		return email -> {
			var user = users.findByEmailIgnoreCase(email.strip())
					.orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));
			return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
					.password(user.getPasswordHash()).roles("USER").build();
		};
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		// CSRF remains enabled, including registration, login, and logout.
		http.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
				.anyRequest().authenticated())
			.exceptionHandling(errors -> errors
				.authenticationEntryPoint((request, response, exception) -> {
					response.setStatus(401); response.setContentType("application/json");
					response.getWriter().write("{\"status\":401,\"message\":\"Authentication required\",\"fieldErrors\":{}}");
				})
				.accessDeniedHandler((request, response, exception) -> {
					response.setStatus(403); response.setContentType("application/json");
					response.getWriter().write("{\"status\":403,\"message\":\"Access denied or invalid CSRF token\",\"fieldErrors\":{}}");
				}))
			.formLogin(login -> login.loginProcessingUrl("/api/auth/login").usernameParameter("email")
				.successHandler((request, response, authentication) -> response.setStatus(204))
				.failureHandler((request, response, exception) -> {
					response.setStatus(401); response.setContentType("application/json");
					response.getWriter().write("{\"status\":401,\"message\":\"Invalid email or password\",\"fieldErrors\":{}}");
				}))
			.logout(logout -> logout.logoutUrl("/api/auth/logout").deleteCookies("JSESSIONID")
				.logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));
		return http.build();
	}
}
