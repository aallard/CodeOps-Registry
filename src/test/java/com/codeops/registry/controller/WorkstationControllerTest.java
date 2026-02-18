package com.codeops.registry.controller;

import com.codeops.registry.config.JwtProperties;
import com.codeops.registry.dto.request.CreateWorkstationProfileRequest;
import com.codeops.registry.dto.request.UpdateWorkstationProfileRequest;
import com.codeops.registry.dto.response.WorkstationProfileResponse;
import com.codeops.registry.dto.response.WorkstationServiceEntry;
import com.codeops.registry.entity.enums.HealthStatus;
import com.codeops.registry.entity.enums.ServiceStatus;
import com.codeops.registry.entity.enums.ServiceType;
import com.codeops.registry.exception.NotFoundException;
import com.codeops.registry.exception.ValidationException;
import com.codeops.registry.service.WorkstationProfileService;
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
class WorkstationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WorkstationProfileService workstationProfileService;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID PROFILE_ID = UUID.randomUUID();
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

    private WorkstationProfileResponse sampleProfileResponse() {
        WorkstationServiceEntry entry = new WorkstationServiceEntry(
                SERVICE_ID, "Test Service", "test-service",
                ServiceType.SPRING_BOOT_API, ServiceStatus.ACTIVE,
                HealthStatus.UP, 1);

        return new WorkstationProfileResponse(
                PROFILE_ID, TEAM_ID, "Dev Workstation",
                "Development profile", SOLUTION_ID,
                List.of(SERVICE_ID), List.of(entry),
                List.of(SERVICE_ID), true, USER_ID,
                Instant.now(), Instant.now());
    }

    // ──────────────────────────────────────────────
    // Authentication tests — no auth → 401
    // ──────────────────────────────────────────────

    @Test
    void createProfile_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/registry/teams/{teamId}/workstations", TEAM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getProfilesForTeam_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/workstations", TEAM_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteProfile_noAuth_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/registry/workstations/{profileId}", PROFILE_ID))
                .andExpect(status().isUnauthorized());
    }

    // ──────────────────────────────────────────────
    // Authorization tests — MEMBER role → 403
    // ──────────────────────────────────────────────

    @Test
    void createProfile_memberRole_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateWorkstationProfileRequest(
                TEAM_ID, "Test Profile", null, null, List.of(SERVICE_ID), false));

        mockMvc.perform(post("/api/v1/registry/teams/{teamId}/workstations", TEAM_ID)
                        .header("Authorization", "Bearer " + memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void getProfilesForTeam_memberRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/workstations", TEAM_ID)
                        .header("Authorization", "Bearer " + memberToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteProfile_memberRole_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/registry/workstations/{profileId}", PROFILE_ID)
                        .header("Authorization", "Bearer " + memberToken()))
                .andExpect(status().isForbidden());
    }

    // ──────────────────────────────────────────────
    // Create profile tests
    // ──────────────────────────────────────────────

    @Test
    void createProfile_architectRole_returns201() throws Exception {
        when(workstationProfileService.createProfile(
                any(CreateWorkstationProfileRequest.class), any(UUID.class)))
                .thenReturn(sampleProfileResponse());

        String body = objectMapper.writeValueAsString(new CreateWorkstationProfileRequest(
                TEAM_ID, "Dev Workstation", "Development profile",
                SOLUTION_ID, List.of(SERVICE_ID), true));

        mockMvc.perform(post("/api/v1/registry/teams/{teamId}/workstations", TEAM_ID)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Dev Workstation"))
                .andExpect(jsonPath("$.teamId").value(TEAM_ID.toString()))
                .andExpect(jsonPath("$.services").isArray());
    }

    @Test
    void createProfile_invalidBody_returns400() throws Exception {
        // Missing required fields: teamId, name
        mockMvc.perform(post("/api/v1/registry/teams/{teamId}/workstations", TEAM_ID)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":null,\"name\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createProfile_duplicateName_returns400() throws Exception {
        when(workstationProfileService.createProfile(
                any(CreateWorkstationProfileRequest.class), any(UUID.class)))
                .thenThrow(new ValidationException(
                        "A workstation profile named 'Dev Workstation' already exists for this team"));

        String body = objectMapper.writeValueAsString(new CreateWorkstationProfileRequest(
                TEAM_ID, "Dev Workstation", null, null, List.of(SERVICE_ID), false));

        mockMvc.perform(post("/api/v1/registry/teams/{teamId}/workstations", TEAM_ID)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ──────────────────────────────────────────────
    // Get profiles tests
    // ──────────────────────────────────────────────

    @Test
    void getProfilesForTeam_architectRole_returns200() throws Exception {
        when(workstationProfileService.getProfilesForTeam(TEAM_ID))
                .thenReturn(List.of(sampleProfileResponse()));

        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/workstations", TEAM_ID)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("Dev Workstation"));
    }

    @Test
    void getProfile_architectRole_returns200() throws Exception {
        when(workstationProfileService.getProfile(PROFILE_ID))
                .thenReturn(sampleProfileResponse());

        mockMvc.perform(get("/api/v1/registry/workstations/{profileId}", PROFILE_ID)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PROFILE_ID.toString()))
                .andExpect(jsonPath("$.name").value("Dev Workstation"))
                .andExpect(jsonPath("$.services[0].name").value("Test Service"));
    }

    @Test
    void getProfile_notFound_returns404() throws Exception {
        UUID missingId = UUID.randomUUID();
        when(workstationProfileService.getProfile(missingId))
                .thenThrow(new NotFoundException("WorkstationProfile", missingId));

        mockMvc.perform(get("/api/v1/registry/workstations/{profileId}", missingId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound());
    }

    // ──────────────────────────────────────────────
    // Update profile tests
    // ──────────────────────────────────────────────

    @Test
    void updateProfile_architectRole_returns200() throws Exception {
        WorkstationProfileResponse updated = new WorkstationProfileResponse(
                PROFILE_ID, TEAM_ID, "Updated Workstation",
                "Updated description", SOLUTION_ID,
                List.of(SERVICE_ID), List.of(), List.of(SERVICE_ID),
                true, USER_ID, Instant.now(), Instant.now());

        when(workstationProfileService.updateProfile(
                any(UUID.class), any(UpdateWorkstationProfileRequest.class)))
                .thenReturn(updated);

        String body = objectMapper.writeValueAsString(new UpdateWorkstationProfileRequest(
                "Updated Workstation", "Updated description", null, null));

        mockMvc.perform(put("/api/v1/registry/workstations/{profileId}", PROFILE_ID)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Workstation"))
                .andExpect(jsonPath("$.description").value("Updated description"));
    }

    @Test
    void updateProfile_notFound_returns404() throws Exception {
        UUID missingId = UUID.randomUUID();
        when(workstationProfileService.updateProfile(
                eq(missingId), any(UpdateWorkstationProfileRequest.class)))
                .thenThrow(new NotFoundException("WorkstationProfile", missingId));

        String body = objectMapper.writeValueAsString(new UpdateWorkstationProfileRequest(
                "Updated", null, null, null));

        mockMvc.perform(put("/api/v1/registry/workstations/{profileId}", missingId)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    // ──────────────────────────────────────────────
    // Delete profile tests
    // ──────────────────────────────────────────────

    @Test
    void deleteProfile_architectRole_returns204() throws Exception {
        doNothing().when(workstationProfileService).deleteProfile(PROFILE_ID);

        mockMvc.perform(delete("/api/v1/registry/workstations/{profileId}", PROFILE_ID)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteProfile_notFound_returns404() throws Exception {
        UUID missingId = UUID.randomUUID();
        doThrow(new NotFoundException("WorkstationProfile", missingId))
                .when(workstationProfileService).deleteProfile(missingId);

        mockMvc.perform(delete("/api/v1/registry/workstations/{profileId}", missingId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound());
    }

    // ──────────────────────────────────────────────
    // Default profile tests
    // ──────────────────────────────────────────────

    @Test
    void getDefaultProfile_architectRole_returns200() throws Exception {
        when(workstationProfileService.getDefaultProfile(TEAM_ID))
                .thenReturn(sampleProfileResponse());

        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/workstations/default", TEAM_ID)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(true))
                .andExpect(jsonPath("$.name").value("Dev Workstation"));
    }

    @Test
    void getDefaultProfile_notFound_returns404() throws Exception {
        UUID missingTeamId = UUID.randomUUID();
        when(workstationProfileService.getDefaultProfile(missingTeamId))
                .thenThrow(new NotFoundException("WorkstationProfile", missingTeamId));

        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/workstations/default", missingTeamId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void setDefault_architectRole_returns200() throws Exception {
        when(workstationProfileService.setDefault(PROFILE_ID))
                .thenReturn(sampleProfileResponse());

        mockMvc.perform(patch("/api/v1/registry/workstations/{profileId}/set-default", PROFILE_ID)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(true));
    }

    // ──────────────────────────────────────────────
    // Create from solution tests
    // ──────────────────────────────────────────────

    @Test
    void createFromSolution_architectRole_returns201() throws Exception {
        when(workstationProfileService.createFromSolution(
                eq(SOLUTION_ID), eq(TEAM_ID), any(UUID.class)))
                .thenReturn(sampleProfileResponse());

        mockMvc.perform(post("/api/v1/registry/solutions/{solutionId}/workstations/from-solution",
                        SOLUTION_ID)
                        .header("Authorization", "Bearer " + adminToken())
                        .param("teamId", TEAM_ID.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Dev Workstation"))
                .andExpect(jsonPath("$.solutionId").value(SOLUTION_ID.toString()));
    }

    @Test
    void createFromSolution_missingTeamId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/registry/solutions/{solutionId}/workstations/from-solution",
                        SOLUTION_ID)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createFromSolution_solutionNotFound_returns404() throws Exception {
        UUID missingId = UUID.randomUUID();
        when(workstationProfileService.createFromSolution(
                eq(missingId), eq(TEAM_ID), any(UUID.class)))
                .thenThrow(new NotFoundException("Solution", missingId));

        mockMvc.perform(post("/api/v1/registry/solutions/{solutionId}/workstations/from-solution",
                        missingId)
                        .header("Authorization", "Bearer " + adminToken())
                        .param("teamId", TEAM_ID.toString()))
                .andExpect(status().isNotFound());
    }

    // ──────────────────────────────────────────────
    // Refresh startup order tests
    // ──────────────────────────────────────────────

    @Test
    void refreshStartupOrder_architectRole_returns200() throws Exception {
        when(workstationProfileService.refreshStartupOrder(PROFILE_ID))
                .thenReturn(sampleProfileResponse());

        mockMvc.perform(post("/api/v1/registry/workstations/{profileId}/refresh-startup-order",
                        PROFILE_ID)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startupOrder").isArray());
    }

    @Test
    void refreshStartupOrder_notFound_returns404() throws Exception {
        UUID missingId = UUID.randomUUID();
        when(workstationProfileService.refreshStartupOrder(missingId))
                .thenThrow(new NotFoundException("WorkstationProfile", missingId));

        mockMvc.perform(post("/api/v1/registry/workstations/{profileId}/refresh-startup-order",
                        missingId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound());
    }
}
