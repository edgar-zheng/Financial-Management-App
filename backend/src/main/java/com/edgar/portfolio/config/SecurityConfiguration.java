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
import com.edgar.portfolio.exception.ApiError;
import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

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
	SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper json) throws Exception {
		// CSRF remains enabled, including registration, login, and logout.
		http.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
				.anyRequest().authenticated())
			.exceptionHandling(errors -> errors
				.authenticationEntryPoint((request, response, exception) -> {
					writeError(response, json, 401, "Authentication required");
				})
				.accessDeniedHandler((request, response, exception) -> {
					writeError(response, json, 403, "Access denied or invalid CSRF token");
				}))
			.formLogin(login -> login.loginProcessingUrl("/api/auth/login").usernameParameter("email")
				.successHandler((request, response, authentication) -> response.setStatus(204))
				.failureHandler((request, response, exception) -> {
					writeError(response, json, 401, "Invalid email or password");
				}))
			.logout(logout -> logout.logoutUrl("/api/auth/logout").deleteCookies("JSESSIONID")
				.logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)));
		return http.build();
	}

    private static void writeError(HttpServletResponse response, JsonMapper json,
            int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(json.writeValueAsString(new ApiError(status, message, Map.of())));
    }
}
