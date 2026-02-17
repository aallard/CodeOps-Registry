package com.codeops.registry.integration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Abstract base class for all integration tests.
 *
 * <p>Starts a shared PostgreSQL Testcontainer and configures the Spring datasource
 * to point at it. Provides helper methods for generating test JWT tokens and
 * building authenticated HTTP headers.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
public abstract class BaseIntegrationTest {

    private static final String TEST_SECRET = "integration-test-secret-key-minimum-32-characters-long";

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("codeops_registry_test")
                .withUsername("test")
                .withPassword("test");
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected TestRestTemplate restTemplate;

    /**
     * Generates a valid JWT token for testing purposes.
     *
     * @param userId  the user UUID to set as the token subject
     * @param email   the email claim
     * @param teamIds the team ID list claim
     * @return a signed JWT string
     */
    protected String generateTestJwt(UUID userId, String email, List<UUID> teamIds) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes());
        Instant now = Instant.now();
        Instant expiry = now.plus(1, ChronoUnit.HOURS);

        var builder = Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("roles", List.of("MEMBER"))
                .claim("teamIds", teamIds.stream().map(UUID::toString).toList())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key, Jwts.SIG.HS256);

        return builder.compact();
    }

    /**
     * Creates HTTP headers with a Bearer token and JSON content type for authenticated requests.
     *
     * @param userId  the user UUID
     * @param email   the user email
     * @param teamIds the team IDs to embed in the token
     * @return configured {@link HttpHeaders}
     */
    protected HttpHeaders authHeaders(UUID userId, String email, List<UUID> teamIds) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(generateTestJwt(userId, email, teamIds));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
