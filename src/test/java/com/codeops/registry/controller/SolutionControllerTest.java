package com.codeops.registry.controller;

import com.codeops.registry.config.JwtProperties;
import com.codeops.registry.dto.request.AddSolutionMemberRequest;
import com.codeops.registry.dto.request.CreateSolutionRequest;
import com.codeops.registry.dto.request.UpdateSolutionMemberRequest;
import com.codeops.registry.dto.request.UpdateSolutionRequest;
import com.codeops.registry.dto.response.*;
import com.codeops.registry.entity.enums.*;
import com.codeops.registry.exception.NotFoundException;
import com.codeops.registry.exception.ValidationException;
import com.codeops.registry.service.SolutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
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
class SolutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SolutionService solutionService;

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

    private SolutionResponse sampleSolutionResponse() {
        return new SolutionResponse(
                SOLUTION_ID, TEAM_ID, "Test Solution", "test-solution",
                "A test solution", SolutionCategory.APPLICATION, SolutionStatus.ACTIVE,
                null, null, null, null, null, null,
                USER_ID, 0,
                Instant.now(), Instant.now());
    }

    private SolutionDetailResponse sampleDetailResponse() {
        return new SolutionDetailResponse(
                SOLUTION_ID, TEAM_ID, "Test Solution", "test-solution",
                "A test solution", SolutionCategory.APPLICATION, SolutionStatus.ACTIVE,
                null, null, null, null, null, null,
                USER_ID, List.of(),
                Instant.now(), Instant.now());
    }

    private SolutionMemberResponse sampleMemberResponse() {
        return new SolutionMemberResponse(
                UUID.randomUUID(), SOLUTION_ID, SERVICE_ID,
                "Test Service", "test-service",
                ServiceType.SPRING_BOOT_API, ServiceStatus.ACTIVE,
                HealthStatus.UP, SolutionMemberRole.CORE,
                0, null);
    }

    private SolutionHealthResponse sampleHealthResponse() {
        return new SolutionHealthResponse(
                SOLUTION_ID, "Test Solution",
                1, 1, 0, 0, 0,
                HealthStatus.UP, List.of());
    }

    // ──────────────────────────────────────────────
    // Authentication tests — no auth → 401
    // ──────────────────────────────────────────────

    @Test
    void createSolution_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/registry/teams/{teamId}/solutions", TEAM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getSolution_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/registry/solutions/{solutionId}", SOLUTION_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteSolution_noAuth_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/registry/solutions/{solutionId}", SOLUTION_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addSolutionMember_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/registry/solutions/{solutionId}/members", SOLUTION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ──────────────────────────────────────────────
    // Authorization tests — MEMBER role → 403
    // ──────────────────────────────────────────────

    @Test
    void createSolution_memberRole_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateSolutionRequest(
                TEAM_ID, "Test Solution", null, null, SolutionCategory.APPLICATION,
                null, null, null, null, null, null));

        mockMvc.perform(post("/api/v1/registry/teams/{teamId}/solutions", TEAM_ID)
                        .header("Authorization", "Bearer " + memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void getSolution_memberRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/registry/solutions/{solutionId}", SOLUTION_ID)
                        .header("Authorization", "Bearer " + memberToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteSolution_memberRole_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/registry/solutions/{solutionId}", SOLUTION_ID)
                        .header("Authorization", "Bearer " + memberToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateSolution_memberRole_returns403() throws Exception {
        mockMvc.perform(put("/api/v1/registry/solutions/{solutionId}", SOLUTION_ID)
                        .header("Authorization", "Bearer " + memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // ──────────────────────────────────────────────
    // Happy path tests — ADMIN role
    // ──────────────────────────────────────────────

    @Test
    void createSolution_architectRole_returns201() throws Exception {
        when(solutionService.createSolution(any(CreateSolutionRequest.class), any(UUID.class)))
                .thenReturn(sampleSolutionResponse());

        String body = objectMapper.writeValueAsString(new CreateSolutionRequest(
                TEAM_ID, "Test Solution", null, null, SolutionCategory.APPLICATION,
                null, null, null, null, null, null));

        mockMvc.perform(post("/api/v1/registry/teams/{teamId}/solutions", TEAM_ID)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Solution"))
                .andExpect(jsonPath("$.slug").value("test-solution"));
    }

    @Test
    void getSolutionsForTeam_architectRole_returns200() throws Exception {
        when(solutionService.getSolutionsForTeam(
                any(UUID.class), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(sampleSolutionResponse()), 0, 20, 1, 1, true));

        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/solutions", TEAM_ID)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].name").value("Test Solution"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getSolutionsForTeam_withStatusFilter_returns200() throws Exception {
        when(solutionService.getSolutionsForTeam(
                any(UUID.class), eq(SolutionStatus.ACTIVE), isNull(), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(sampleSolutionResponse()), 0, 20, 1, 1, true));

        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/solutions", TEAM_ID)
                        .header("Authorization", "Bearer " + adminToken())
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));
    }

    @Test
    void getSolutionsForTeam_withCategoryFilter_returns200() throws Exception {
        when(solutionService.getSolutionsForTeam(
                any(UUID.class), isNull(), eq(SolutionCategory.APPLICATION), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(sampleSolutionResponse()), 0, 20, 1, 1, true));

        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/solutions", TEAM_ID)
                        .header("Authorization", "Bearer " + adminToken())
                        .param("category", "APPLICATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].category").value("APPLICATION"));
    }

    @Test
    void getSolution_architectRole_returns200() throws Exception {
        when(solutionService.getSolution(SOLUTION_ID))
                .thenReturn(sampleSolutionResponse());

        mockMvc.perform(get("/api/v1/registry/solutions/{solutionId}", SOLUTION_ID)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SOLUTION_ID.toString()))
                .andExpect(jsonPath("$.name").value("Test Solution"));
    }

    @Test
    void getSolution_notFound_returns404() throws Exception {
        UUID missingId = UUID.randomUUID();
        when(solutionService.getSolution(missingId))
                .thenThrow(new NotFoundException("Solution", missingId));

        mockMvc.perform(get("/api/v1/registry/solutions/{solutionId}", missingId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateSolution_architectRole_returns200() throws Exception {
        when(solutionService.updateSolution(any(UUID.class), any(UpdateSolutionRequest.class)))
                .thenReturn(sampleSolutionResponse());

        String body = objectMapper.writeValueAsString(new UpdateSolutionRequest(
                "Updated Solution", null, null, null, null, null, null, null, null, null));

        mockMvc.perform(put("/api/v1/registry/solutions/{solutionId}", SOLUTION_ID)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void updateSolution_notFound_returns404() throws Exception {
        UUID missingId = UUID.randomUUID();
        when(solutionService.updateSolution(eq(missingId), any(UpdateSolutionRequest.class)))
                .thenThrow(new NotFoundException("Solution", missingId));

        String body = objectMapper.writeValueAsString(new UpdateSolutionRequest(
                "Updated", null, null, null, null, null, null, null, null, null));

        mockMvc.perform(put("/api/v1/registry/solutions/{solutionId}", missingId)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteSolution_architectRole_returns204() throws Exception {
        doNothing().when(solutionService).deleteSolution(SOLUTION_ID);

        mockMvc.perform(delete("/api/v1/registry/solutions/{solutionId}", SOLUTION_ID)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteSolution_notFound_returns404() throws Exception {
        UUID missingId = UUID.randomUUID();
        doThrow(new NotFoundException("Solution", missingId))
                .when(solutionService).deleteSolution(missingId);

        mockMvc.perform(delete("/api/v1/registry/solutions/{solutionId}", missingId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getSolutionDetail_architectRole_returns200() throws Exception {
        when(solutionService.getSolutionDetail(SOLUTION_ID))
                .thenReturn(sampleDetailResponse());

        mockMvc.perform(get("/api/v1/registry/solutions/{solutionId}/detail", SOLUTION_ID)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test Solution"))
                .andExpect(jsonPath("$.members").isArray());
    }

    @Test
    void addSolutionMember_architectRole_returns201() throws Exception {
        when(solutionService.addMember(any(UUID.class), any(AddSolutionMemberRequest.class)))
                .thenReturn(sampleMemberResponse());

        String body = objectMapper.writeValueAsString(new AddSolutionMemberRequest(
                SERVICE_ID, SolutionMemberRole.CORE, null, null));

        mockMvc.perform(post("/api/v1/registry/solutions/{solutionId}/members", SOLUTION_ID)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serviceId").value(SERVICE_ID.toString()))
                .andExpect(jsonPath("$.role").value("CORE"));
    }

    @Test
    void addSolutionMember_duplicateMember_returns400() throws Exception {
        when(solutionService.addMember(any(UUID.class), any(AddSolutionMemberRequest.class)))
                .thenThrow(new ValidationException("Service is already a member of solution"));

        String body = objectMapper.writeValueAsString(new AddSolutionMemberRequest(
                SERVICE_ID, SolutionMemberRole.CORE, null, null));

        mockMvc.perform(post("/api/v1/registry/solutions/{solutionId}/members", SOLUTION_ID)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateSolutionMember_architectRole_returns200() throws Exception {
        when(solutionService.updateMember(any(UUID.class), any(UUID.class), any(UpdateSolutionMemberRequest.class)))
                .thenReturn(sampleMemberResponse());

        String body = objectMapper.writeValueAsString(new UpdateSolutionMemberRequest(
                SolutionMemberRole.SUPPORTING, 1, null));

        mockMvc.perform(put("/api/v1/registry/solutions/{solutionId}/members/{serviceId}",
                        SOLUTION_ID, SERVICE_ID)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void removeSolutionMember_architectRole_returns204() throws Exception {
        doNothing().when(solutionService).removeMember(SOLUTION_ID, SERVICE_ID);

        mockMvc.perform(delete("/api/v1/registry/solutions/{solutionId}/members/{serviceId}",
                        SOLUTION_ID, SERVICE_ID)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNoContent());
    }

    @Test
    void reorderSolutionMembers_architectRole_returns200() throws Exception {
        when(solutionService.reorderMembers(any(UUID.class), anyList()))
                .thenReturn(List.of(sampleMemberResponse()));

        UUID svc1 = UUID.randomUUID();
        UUID svc2 = UUID.randomUUID();
        String body = objectMapper.writeValueAsString(List.of(svc1, svc2));

        mockMvc.perform(put("/api/v1/registry/solutions/{solutionId}/members/reorder", SOLUTION_ID)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void reorderSolutionMembers_invalidServiceId_returns400() throws Exception {
        UUID invalidId = UUID.randomUUID();
        when(solutionService.reorderMembers(any(UUID.class), anyList()))
                .thenThrow(new ValidationException("Service " + invalidId + " is not a member of solution"));

        String body = objectMapper.writeValueAsString(List.of(invalidId));

        mockMvc.perform(put("/api/v1/registry/solutions/{solutionId}/members/reorder", SOLUTION_ID)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getSolutionHealth_architectRole_returns200() throws Exception {
        when(solutionService.getSolutionHealth(SOLUTION_ID))
                .thenReturn(sampleHealthResponse());

        mockMvc.perform(get("/api/v1/registry/solutions/{solutionId}/health", SOLUTION_ID)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aggregatedHealth").value("UP"))
                .andExpect(jsonPath("$.totalServices").value(1));
    }

    // ──────────────────────────────────────────────
    // Validation tests
    // ──────────────────────────────────────────────

    @Test
    void createSolution_invalidBody_returns400() throws Exception {
        // Missing required fields: name, category
        String body = objectMapper.writeValueAsString(new CreateSolutionRequest(
                TEAM_ID, "", null, null, null,
                null, null, null, null, null, null));

        mockMvc.perform(post("/api/v1/registry/teams/{teamId}/solutions", TEAM_ID)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void addSolutionMember_invalidBody_returns400() throws Exception {
        // Missing required fields: serviceId, role
        mockMvc.perform(post("/api/v1/registry/solutions/{solutionId}/members", SOLUTION_ID)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":null,\"role\":null}"))
                .andExpect(status().isBadRequest());
    }
}
