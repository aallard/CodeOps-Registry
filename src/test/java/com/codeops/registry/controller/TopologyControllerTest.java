package com.codeops.registry.controller;

import com.codeops.registry.config.JwtProperties;
import com.codeops.registry.dto.response.*;
import com.codeops.registry.entity.enums.*;
import com.codeops.registry.exception.NotFoundException;
import com.codeops.registry.service.TopologyService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TopologyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProperties jwtProperties;

    @MockBean
    private TopologyService topologyService;

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

    private String adminToken() {
        return buildToken("ADMIN");
    }

    private String memberToken() {
        return buildToken("MEMBER");
    }

    // ──────────────────────────────────────────────
    // Sample response builders
    // ──────────────────────────────────────────────

    private TopologyResponse sampleTopologyResponse() {
        TopologyNodeResponse node = new TopologyNodeResponse(
                SERVICE_ID, "Test Service", "test-service",
                ServiceType.SPRING_BOOT_API, ServiceStatus.ACTIVE, HealthStatus.UP,
                2, 1, 3, List.of(SOLUTION_ID), "backend");

        DependencyEdgeResponse edge = new DependencyEdgeResponse(
                SERVICE_ID, UUID.randomUUID(),
                DependencyType.HTTP_REST, true, "/api/v1/data");

        TopologySolutionGroup group = new TopologySolutionGroup(
                SOLUTION_ID, "Test Solution", "test-solution",
                SolutionStatus.ACTIVE, 3, List.of(SERVICE_ID));

        TopologyLayerResponse layer = new TopologyLayerResponse(
                "backend", 1, List.of(SERVICE_ID));

        TopologyStatsResponse stats = new TopologyStatsResponse(
                5, 8, 2, 1, 1, 0, 3);

        return new TopologyResponse(
                TEAM_ID, List.of(node), List.of(edge),
                List.of(group), List.of(layer), stats);
    }

    private TopologyStatsResponse sampleStatsResponse() {
        return new TopologyStatsResponse(5, 8, 2, 1, 1, 0, 3);
    }

    // ──────────────────────────────────────────────
    // Authentication tests — no auth → 401
    // ──────────────────────────────────────────────

    @Test
    void getTopology_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/topology", TEAM_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getEcosystemStats_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/topology/stats", TEAM_ID))
                .andExpect(status().isUnauthorized());
    }

    // ──────────────────────────────────────────────
    // Authorization tests — MEMBER role → 403
    // ──────────────────────────────────────────────

    @Test
    void getTopology_memberRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/topology", TEAM_ID)
                        .header("Authorization", "Bearer " + memberToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void getSolutionTopology_memberRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/registry/solutions/{solutionId}/topology", SOLUTION_ID)
                        .header("Authorization", "Bearer " + memberToken()))
                .andExpect(status().isForbidden());
    }

    // ──────────────────────────────────────────────
    // Get topology tests
    // ──────────────────────────────────────────────

    @Test
    void getTopology_architectRole_returns200() throws Exception {
        when(topologyService.getTopology(TEAM_ID))
                .thenReturn(sampleTopologyResponse());

        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/topology", TEAM_ID)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").value(TEAM_ID.toString()))
                .andExpect(jsonPath("$.nodes").isArray())
                .andExpect(jsonPath("$.nodes[0].name").value("Test Service"))
                .andExpect(jsonPath("$.edges").isArray())
                .andExpect(jsonPath("$.solutionGroups").isArray())
                .andExpect(jsonPath("$.layers").isArray())
                .andExpect(jsonPath("$.stats.totalServices").value(5));
    }

    @Test
    void getSolutionTopology_architectRole_returns200() throws Exception {
        when(topologyService.getTopologyForSolution(SOLUTION_ID))
                .thenReturn(sampleTopologyResponse());

        mockMvc.perform(get("/api/v1/registry/solutions/{solutionId}/topology", SOLUTION_ID)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes").isArray())
                .andExpect(jsonPath("$.edges").isArray());
    }

    @Test
    void getSolutionTopology_notFound_returns404() throws Exception {
        UUID missingId = UUID.randomUUID();
        when(topologyService.getTopologyForSolution(missingId))
                .thenThrow(new NotFoundException("Solution", missingId));

        mockMvc.perform(get("/api/v1/registry/solutions/{solutionId}/topology", missingId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getServiceNeighborhood_architectRole_returns200() throws Exception {
        when(topologyService.getServiceNeighborhood(SERVICE_ID, 2))
                .thenReturn(sampleTopologyResponse());

        mockMvc.perform(get("/api/v1/registry/services/{serviceId}/topology/neighborhood", SERVICE_ID)
                        .header("Authorization", "Bearer " + adminToken())
                        .param("depth", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes").isArray())
                .andExpect(jsonPath("$.edges").isArray());
    }

    @Test
    void getServiceNeighborhood_defaultDepth_returns200() throws Exception {
        when(topologyService.getServiceNeighborhood(SERVICE_ID, 1))
                .thenReturn(sampleTopologyResponse());

        mockMvc.perform(get("/api/v1/registry/services/{serviceId}/topology/neighborhood", SERVICE_ID)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes").isArray());
    }

    @Test
    void getServiceNeighborhood_notFound_returns404() throws Exception {
        UUID missingId = UUID.randomUUID();
        when(topologyService.getServiceNeighborhood(missingId, 1))
                .thenThrow(new NotFoundException("ServiceRegistration", missingId));

        mockMvc.perform(get("/api/v1/registry/services/{serviceId}/topology/neighborhood", missingId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getEcosystemStats_architectRole_returns200() throws Exception {
        when(topologyService.getEcosystemStats(TEAM_ID))
                .thenReturn(sampleStatsResponse());

        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/topology/stats", TEAM_ID)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalServices").value(5))
                .andExpect(jsonPath("$.totalDependencies").value(8))
                .andExpect(jsonPath("$.totalSolutions").value(2))
                .andExpect(jsonPath("$.maxDependencyDepth").value(3));
    }
}
