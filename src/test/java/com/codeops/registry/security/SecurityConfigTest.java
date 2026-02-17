package com.codeops.registry.security;

import com.codeops.registry.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProperties jwtProperties;

    private String buildValidToken() {
        SecretKey key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes());
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("email", "test@test.com")
                .claim("roles", List.of("MEMBER"))
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    @Test
    void healthEndpoint_noAuth_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("codeops-registry"));
    }

    @Test
    void swaggerUi_noAuth_returns200() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void apiDocs_noAuth_returns200() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpoint_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/registry/services"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_invalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/registry/services")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_validToken_passesFilter() throws Exception {
        String token = buildValidToken();
        // Endpoint doesn't exist yet (404), but it passes the security filter (not 401)
        mockMvc.perform(get("/api/v1/registry/services")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void healthEndpoint_returnsCorrelationIdHeader() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(result ->
                        org.assertj.core.api.Assertions.assertThat(
                                result.getResponse().getHeader("X-Correlation-ID")
                        ).isNotBlank());
    }

    @Test
    void healthEndpoint_respectsClientCorrelationId() throws Exception {
        String correlationId = "test-correlation-123";
        mockMvc.perform(get("/api/v1/health")
                        .header("X-Correlation-ID", correlationId))
                .andExpect(status().isOk())
                .andExpect(result ->
                        org.assertj.core.api.Assertions.assertThat(
                                result.getResponse().getHeader("X-Correlation-ID")
                        ).isEqualTo(correlationId));
    }
}
