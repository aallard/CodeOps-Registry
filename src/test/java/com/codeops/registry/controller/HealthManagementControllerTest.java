package com.codeops.registry.controller;

import com.codeops.registry.config.JwtProperties;
import com.codeops.registry.dto.response.ServiceHealthResponse;
import com.codeops.registry.dto.response.SolutionHealthResponse;
import com.codeops.registry.dto.response.TeamHealthSummaryResponse;
import com.codeops.registry.entity.enums.HealthStatus;
import com.codeops.registry.exception.NotFoundException;
import com.codeops.registry.service.HealthCheckService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProperties jwtProperties;

    @MockBean
    private HealthCheckService healthCheckService;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID SOLUTION_ID = UUID.randomUUID();
    private static final UUID SERVICE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    // ──────────────────────────────────────────────
    // Token builders
    // ──────────────────────────────────────────────

    private String buildToken(String... roles) {
        SecretKey key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes());
        return Jwts.builder()
                .subject(USER_ID.toString())
                .claim("email", "test@test.com")
                .claim("roles", List.of(roles))
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    private String architectToken() {
        return buildToken("ARCHITECT");
    }

    private String memberToken() {
        return buildToken("MEMBER");
    }

    // ──────────────────────────────────────────────
    // Sample response builders
    // ──────────────────────────────────────────────

    private ServiceHealthResponse sampleServiceHealth() {
        return new ServiceHealthResponse(
                SERVICE_ID, "Test Service", "test-service",
                HealthStatus.UP, Instant.now(),
                "http://localhost:8080/actuator/health", 45, null);
    }

    private TeamHealthSummaryResponse sampleTeamHealthSummary() {
        return new TeamHealthSummaryResponse(
                TEAM_ID, 10, 8, 6, 1, 1, 0, 2,
                HealthStatus.DEGRADED, List.of(sampleServiceHealth()),
                Instant.now());
    }

    private SolutionHealthResponse sampleSolutionHealth() {
        return new SolutionHealthResponse(
                SOLUTION_ID, "Test Solution", 5, 3, 1, 1, 0,
                HealthStatus.DEGRADED, List.of(sampleServiceHealth()));
    }

    // ──────────────────────────────────────────────
    // Authentication tests — no auth → 401
    // ──────────────────────────────────────────────

    @Test
    void getTeamHealthSummary_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/health/summary", TEAM_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void checkTeamHealth_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/registry/teams/{teamId}/health/check", TEAM_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getServiceHealthCached_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/registry/services/{serviceId}/health/cached", SERVICE_ID))
                .andExpect(status().isUnauthorized());
    }

    // ──────────────────────────────────────────────
    // Authorization tests — MEMBER role → 403
    // ──────────────────────────────────────────────

    @Test
    void getTeamHealthSummary_memberRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/health/summary", TEAM_ID)
                        .header("Authorization", "Bearer " + memberToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void checkTeamHealth_memberRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/registry/teams/{teamId}/health/check", TEAM_ID)
                        .header("Authorization", "Bearer " + memberToken()))
                .andExpect(status().isForbidden());
    }

    // ──────────────────────────────────────────────
    // Team health summary tests
    // ──────────────────────────────────────────────

    @Test
    void getTeamHealthSummary_architectRole_returns200() throws Exception {
        when(healthCheckService.getTeamHealthSummary(TEAM_ID))
                .thenReturn(sampleTeamHealthSummary());

        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/health/summary", TEAM_ID)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").value(TEAM_ID.toString()))
                .andExpect(jsonPath("$.totalServices").value(10))
                .andExpect(jsonPath("$.activeServices").value(8))
                .andExpect(jsonPath("$.overallHealth").value("DEGRADED"))
                .andExpect(jsonPath("$.unhealthyServices").isArray());
    }

    // ──────────────────────────────────────────────
    // Check team health tests (live)
    // ──────────────────────────────────────────────

    @Test
    void checkTeamHealth_architectRole_returns200() throws Exception {
        when(healthCheckService.checkTeamHealth(TEAM_ID))
                .thenReturn(sampleTeamHealthSummary());

        mockMvc.perform(post("/api/v1/registry/teams/{teamId}/health/check", TEAM_ID)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").value(TEAM_ID.toString()))
                .andExpect(jsonPath("$.overallHealth").value("DEGRADED"));
    }

    // ──────────────────────────────────────────────
    // Unhealthy services tests
    // ──────────────────────────────────────────────

    @Test
    void getUnhealthyServices_architectRole_returns200() throws Exception {
        ServiceHealthResponse unhealthy = new ServiceHealthResponse(
                SERVICE_ID, "Failing Service", "failing-service",
                HealthStatus.DOWN, Instant.now(),
                "http://localhost:8080/actuator/health", null, "Connection refused");

        when(healthCheckService.getUnhealthyServices(TEAM_ID))
                .thenReturn(List.of(unhealthy));

        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/health/unhealthy", TEAM_ID)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].healthStatus").value("DOWN"))
                .andExpect(jsonPath("$[0].errorMessage").value("Connection refused"));
    }

    // ──────────────────────────────────────────────
    // Never-checked services tests
    // ──────────────────────────────────────────────

    @Test
    void getServicesNeverChecked_architectRole_returns200() throws Exception {
        ServiceHealthResponse neverChecked = new ServiceHealthResponse(
                SERVICE_ID, "New Service", "new-service",
                HealthStatus.UNKNOWN, null,
                "http://localhost:9090/health", null, null);

        when(healthCheckService.getServicesNeverChecked(TEAM_ID))
                .thenReturn(List.of(neverChecked));

        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/health/never-checked", TEAM_ID)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].healthStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$[0].lastCheckAt").doesNotExist());
    }

    // ──────────────────────────────────────────────
    // Solution health check tests
    // ──────────────────────────────────────────────

    @Test
    void checkSolutionHealth_architectRole_returns200() throws Exception {
        when(healthCheckService.checkSolutionHealth(SOLUTION_ID))
                .thenReturn(sampleSolutionHealth());

        mockMvc.perform(post("/api/v1/registry/solutions/{solutionId}/health/check", SOLUTION_ID)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.solutionId").value(SOLUTION_ID.toString()))
                .andExpect(jsonPath("$.solutionName").value("Test Solution"))
                .andExpect(jsonPath("$.aggregatedHealth").value("DEGRADED"))
                .andExpect(jsonPath("$.serviceHealths").isArray());
    }

    @Test
    void checkSolutionHealth_notFound_returns404() throws Exception {
        UUID missingId = UUID.randomUUID();
        when(healthCheckService.checkSolutionHealth(missingId))
                .thenThrow(new NotFoundException("Solution", missingId));

        mockMvc.perform(post("/api/v1/registry/solutions/{solutionId}/health/check", missingId)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isNotFound());
    }

    // ──────────────────────────────────────────────
    // Cached service health tests
    // ──────────────────────────────────────────────

    @Test
    void getServiceHealthCached_architectRole_returns200() throws Exception {
        when(healthCheckService.getServiceHealthHistory(SERVICE_ID))
                .thenReturn(sampleServiceHealth());

        mockMvc.perform(get("/api/v1/registry/services/{serviceId}/health/cached", SERVICE_ID)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceId").value(SERVICE_ID.toString()))
                .andExpect(jsonPath("$.healthStatus").value("UP"))
                .andExpect(jsonPath("$.responseTimeMs").value(45));
    }

    @Test
    void getServiceHealthCached_notFound_returns404() throws Exception {
        UUID missingId = UUID.randomUUID();
        when(healthCheckService.getServiceHealthHistory(missingId))
                .thenThrow(new NotFoundException("ServiceRegistration", missingId));

        mockMvc.perform(get("/api/v1/registry/services/{serviceId}/health/cached", missingId)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isNotFound());
    }
}
