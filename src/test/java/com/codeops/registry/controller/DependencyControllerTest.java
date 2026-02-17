package com.codeops.registry.controller;

import com.codeops.registry.config.JwtProperties;
import com.codeops.registry.dto.request.CreateDependencyRequest;
import com.codeops.registry.dto.response.*;
import com.codeops.registry.entity.enums.DependencyType;
import com.codeops.registry.entity.enums.HealthStatus;
import com.codeops.registry.entity.enums.ServiceStatus;
import com.codeops.registry.entity.enums.ServiceType;
import com.codeops.registry.exception.NotFoundException;
import com.codeops.registry.exception.ValidationException;
import com.codeops.registry.service.DependencyGraphService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DependencyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DependencyGraphService dependencyGraphService;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID SERVICE_ID = UUID.randomUUID();
    private static final UUID TARGET_SERVICE_ID = UUID.randomUUID();
    private static final UUID DEPENDENCY_ID = UUID.randomUUID();
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

    private ServiceDependencyResponse sampleDependencyResponse() {
        return new ServiceDependencyResponse(
                DEPENDENCY_ID, SERVICE_ID, "Source Service", "source-service",
                TARGET_SERVICE_ID, "Target Service", "target-service",
                DependencyType.HTTP_REST, "REST call", true, "/api/data",
                Instant.now());
    }

    private DependencyNodeResponse sampleNodeResponse(UUID id, String name) {
        return new DependencyNodeResponse(
                id, name, name.toLowerCase().replace(' ', '-'),
                ServiceType.SPRING_BOOT_API, ServiceStatus.ACTIVE, HealthStatus.UP);
    }

    // ──────────────────────────────────────────────
    // Authentication tests — no auth → 401
    // ──────────────────────────────────────────────

    @Test
    void createDependency_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/registry/dependencies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getDependencyGraph_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/dependencies/graph", TEAM_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void removeDependency_noAuth_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/registry/dependencies/{dependencyId}", DEPENDENCY_ID))
                .andExpect(status().isUnauthorized());
    }

    // ──────────────────────────────────────────────
    // Authorization tests — MEMBER role → 403
    // ──────────────────────────────────────────────

    @Test
    void createDependency_memberRole_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateDependencyRequest(
                SERVICE_ID, TARGET_SERVICE_ID, DependencyType.HTTP_REST, null, null, null));

        mockMvc.perform(post("/api/v1/registry/dependencies")
                        .header("Authorization", "Bearer " + memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void getDependencyGraph_memberRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/dependencies/graph", TEAM_ID)
                        .header("Authorization", "Bearer " + memberToken()))
                .andExpect(status().isForbidden());
    }

    // ──────────────────────────────────────────────
    // CRUD tests
    // ──────────────────────────────────────────────

    @Test
    void createDependency_architectRole_returns201() throws Exception {
        when(dependencyGraphService.createDependency(any(CreateDependencyRequest.class)))
                .thenReturn(sampleDependencyResponse());

        String body = objectMapper.writeValueAsString(new CreateDependencyRequest(
                SERVICE_ID, TARGET_SERVICE_ID, DependencyType.HTTP_REST, "REST call", true, "/api/data"));

        mockMvc.perform(post("/api/v1/registry/dependencies")
                        .header("Authorization", "Bearer " + architectToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dependencyType").value("HTTP_REST"))
                .andExpect(jsonPath("$.isRequired").value(true));
    }

    @Test
    void createDependency_invalidBody_returns400() throws Exception {
        // Missing required fields: sourceServiceId, targetServiceId, dependencyType
        mockMvc.perform(post("/api/v1/registry/dependencies")
                        .header("Authorization", "Bearer " + architectToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceServiceId\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createDependency_selfDependency_returns400() throws Exception {
        when(dependencyGraphService.createDependency(any(CreateDependencyRequest.class)))
                .thenThrow(new ValidationException("A service cannot depend on itself"));

        String body = objectMapper.writeValueAsString(new CreateDependencyRequest(
                SERVICE_ID, SERVICE_ID, DependencyType.HTTP_REST, null, null, null));

        mockMvc.perform(post("/api/v1/registry/dependencies")
                        .header("Authorization", "Bearer " + architectToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createDependency_wouldCreateCycle_returns400() throws Exception {
        when(dependencyGraphService.createDependency(any(CreateDependencyRequest.class)))
                .thenThrow(new ValidationException("Adding this dependency would create a cycle"));

        String body = objectMapper.writeValueAsString(new CreateDependencyRequest(
                SERVICE_ID, TARGET_SERVICE_ID, DependencyType.HTTP_REST, null, null, null));

        mockMvc.perform(post("/api/v1/registry/dependencies")
                        .header("Authorization", "Bearer " + architectToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createDependency_duplicate_returns400() throws Exception {
        when(dependencyGraphService.createDependency(any(CreateDependencyRequest.class)))
                .thenThrow(new ValidationException("Dependency already exists"));

        String body = objectMapper.writeValueAsString(new CreateDependencyRequest(
                SERVICE_ID, TARGET_SERVICE_ID, DependencyType.HTTP_REST, null, null, null));

        mockMvc.perform(post("/api/v1/registry/dependencies")
                        .header("Authorization", "Bearer " + architectToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void removeDependency_architectRole_returns204() throws Exception {
        doNothing().when(dependencyGraphService).removeDependency(DEPENDENCY_ID);

        mockMvc.perform(delete("/api/v1/registry/dependencies/{dependencyId}", DEPENDENCY_ID)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isNoContent());
    }

    @Test
    void removeDependency_notFound_returns404() throws Exception {
        UUID missingId = UUID.randomUUID();
        doThrow(new NotFoundException("ServiceDependency", missingId))
                .when(dependencyGraphService).removeDependency(missingId);

        mockMvc.perform(delete("/api/v1/registry/dependencies/{dependencyId}", missingId)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isNotFound());
    }

    // ──────────────────────────────────────────────
    // Graph query tests
    // ──────────────────────────────────────────────

    @Test
    void getDependencyGraph_architectRole_returns200() throws Exception {
        DependencyGraphResponse graph = new DependencyGraphResponse(TEAM_ID, List.of(), List.of());
        when(dependencyGraphService.getDependencyGraph(TEAM_ID))
                .thenReturn(graph);

        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/dependencies/graph", TEAM_ID)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").value(TEAM_ID.toString()))
                .andExpect(jsonPath("$.nodes").isArray())
                .andExpect(jsonPath("$.edges").isArray());
    }

    @Test
    void getImpactAnalysis_architectRole_returns200() throws Exception {
        ImpactAnalysisResponse impact = new ImpactAnalysisResponse(
                SERVICE_ID, "Source Service", List.of(), 0);
        when(dependencyGraphService.getImpactAnalysis(SERVICE_ID))
                .thenReturn(impact);

        mockMvc.perform(get("/api/v1/registry/services/{serviceId}/dependencies/impact", SERVICE_ID)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceServiceId").value(SERVICE_ID.toString()))
                .andExpect(jsonPath("$.totalAffected").value(0));
    }

    @Test
    void getImpactAnalysis_notFound_returns404() throws Exception {
        UUID missingId = UUID.randomUUID();
        when(dependencyGraphService.getImpactAnalysis(missingId))
                .thenThrow(new NotFoundException("ServiceRegistration", missingId));

        mockMvc.perform(get("/api/v1/registry/services/{serviceId}/dependencies/impact", missingId)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getStartupOrder_architectRole_returns200() throws Exception {
        when(dependencyGraphService.getStartupOrder(TEAM_ID))
                .thenReturn(List.of(sampleNodeResponse(SERVICE_ID, "Database"),
                        sampleNodeResponse(TARGET_SERVICE_ID, "API")));

        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/dependencies/startup-order", TEAM_ID)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("Database"))
                .andExpect(jsonPath("$[1].name").value("API"));
    }

    @Test
    void detectCycles_architectRole_returns200() throws Exception {
        when(dependencyGraphService.detectCycles(TEAM_ID))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/dependencies/cycles", TEAM_ID)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void detectCycles_withCycles_returnsUuidList() throws Exception {
        when(dependencyGraphService.detectCycles(TEAM_ID))
                .thenReturn(List.of(SERVICE_ID, TARGET_SERVICE_ID));

        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/dependencies/cycles", TEAM_ID)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(SERVICE_ID.toString()))
                .andExpect(jsonPath("$[1]").value(TARGET_SERVICE_ID.toString()));
    }
}
