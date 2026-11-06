package com.edgar.portfolio.config;

import com.edgar.portfolio.controller.AuthController;
import com.edgar.portfolio.repository.UserRepository;
import com.edgar.portfolio.service.RegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Exercises the actual security filters and Actuator without connecting to a database.
@SpringBootTest(classes = DeploymentSecurityTests.TestApplication.class, properties = {
        "spring.config.import=", "app.frontend-url=https://frontend.example.com"
})
class DeploymentSecurityTests {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
    @Import({SecurityConfiguration.class, AuthController.class})
    static class TestApplication {}

    @MockitoBean UserRepository users;
    @MockitoBean RegistrationService registration;
    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test void anonymousHealthReturnsOnlyStatusWithoutCreatingASession() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(jsonPath("$.groups").doesNotExist())
                .andExpect(cookie().doesNotExist("JSESSIONID"));
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test void configuredOriginCanPreflightWithCredentialsAndCsrfHeader() throws Exception {
        for (String method : new String[] {"GET", "HEAD", "POST", "PUT", "PATCH", "DELETE"}) {
            mvc.perform(options("/api/portfolios/1/allocations")
                .header("Origin", "https://frontend.example.com")
                .header("Access-Control-Request-Method", method)
                .header("Access-Control-Request-Headers", "content-type,x-csrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://frontend.example.com"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        }
    }

    @Test void actualBootstrapPathIsPublicWhileRootCsrfHasNoController() throws Exception {
        mvc.perform(get("/api/auth/csrf").header("Origin", "https://frontend.example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://frontend.example.com"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        // The catch-all protects unknown paths before MVC can return its 404.
        mvc.perform(get("/csrf")).andExpect(status().isUnauthorized());
        mvc.perform(get("/csrf").with(user("test@example.com"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/portfolios/1")).andExpect(status().isUnauthorized());
    }

    @Test void otherOriginsAreRejected() throws Exception {
        mvc.perform(options("/api/portfolios")
                .header("Origin", "https://untrusted.example.com")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test void corsDoesNotBypassAuthenticationOrCsrf() throws Exception {
        mvc.perform(get("/api/auth/me").header("Origin", "https://frontend.example.com"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://frontend.example.com"));
        mvc.perform(post("/api/auth/register").header("Origin", "https://frontend.example.com")
                .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }
}
