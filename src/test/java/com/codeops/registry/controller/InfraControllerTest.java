package com.codeops.registry.controller;

import com.codeops.registry.config.JwtProperties;
import com.codeops.registry.dto.request.CreateInfraResourceRequest;
import com.codeops.registry.dto.request.UpdateInfraResourceRequest;
import com.codeops.registry.dto.response.InfraResourceResponse;
import com.codeops.registry.dto.response.PageResponse;
import com.codeops.registry.entity.enums.InfraResourceType;
import com.codeops.registry.exception.NotFoundException;
import com.codeops.registry.exception.ValidationException;
import com.codeops.registry.service.InfraResourceService;
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
class InfraControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InfraResourceService infraResourceService;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID SERVICE_ID = UUID.randomUUID();
    private static final UUID RESOURCE_ID = UUID.randomUUID();
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

    private InfraResourceResponse sampleInfraResponse() {
        return new InfraResourceResponse(
                RESOURCE_ID, TEAM_ID, SERVICE_ID, "Test Service", "test-service",
                InfraResourceType.S3_BUCKET, "my-bucket", "local",
                "us-east-1", "arn:aws:s3:::my-bucket", null, "Test bucket",
                USER_ID, Instant.now(), Instant.now());
    }

    // ──────────────────────────────────────────────
    // Authentication tests — no auth → 401
    // ──────────────────────────────────────────────

    @Test
    void createResource_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/registry/teams/{teamId}/infra-resources", TEAM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getResourcesForTeam_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/infra-resources", TEAM_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteResource_noAuth_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/registry/infra-resources/{resourceId}", RESOURCE_ID))
                .andExpect(status().isUnauthorized());
    }

    // ──────────────────────────────────────────────
    // Authorization tests — MEMBER role → 403
    // ──────────────────────────────────────────────

    @Test
    void createResource_memberRole_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateInfraResourceRequest(
                TEAM_ID, SERVICE_ID, InfraResourceType.S3_BUCKET,
                "my-bucket", "local", null, null, null, null));

        mockMvc.perform(post("/api/v1/registry/teams/{teamId}/infra-resources", TEAM_ID)
                        .header("Authorization", "Bearer " + memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void getResourcesForTeam_memberRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/infra-resources", TEAM_ID)
                        .header("Authorization", "Bearer " + memberToken()))
                .andExpect(status().isForbidden());
    }

    // ──────────────────────────────────────────────
    // Create resource tests
    // ──────────────────────────────────────────────

    @Test
    void createResource_architectRole_returns201() throws Exception {
        when(infraResourceService.createResource(any(CreateInfraResourceRequest.class), any(UUID.class)))
                .thenReturn(sampleInfraResponse());

        String body = objectMapper.writeValueAsString(new CreateInfraResourceRequest(
                TEAM_ID, SERVICE_ID, InfraResourceType.S3_BUCKET,
                "my-bucket", "local", "us-east-1", "arn:aws:s3:::my-bucket", null, "Test bucket"));

        mockMvc.perform(post("/api/v1/registry/teams/{teamId}/infra-resources", TEAM_ID)
                        .header("Authorization", "Bearer " + architectToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resourceType").value("S3_BUCKET"))
                .andExpect(jsonPath("$.resourceName").value("my-bucket"));
    }

    @Test
    void createResource_invalidBody_returns400() throws Exception {
        // Missing required fields: teamId, resourceType, resourceName, environment
        mockMvc.perform(post("/api/v1/registry/teams/{teamId}/infra-resources", TEAM_ID)
                        .header("Authorization", "Bearer " + architectToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamId\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createResource_duplicate_returns400() throws Exception {
        when(infraResourceService.createResource(any(CreateInfraResourceRequest.class), any(UUID.class)))
                .thenThrow(new ValidationException(
                        "Infrastructure resource 'my-bucket' of type S3_BUCKET already exists"));

        String body = objectMapper.writeValueAsString(new CreateInfraResourceRequest(
                TEAM_ID, SERVICE_ID, InfraResourceType.S3_BUCKET,
                "my-bucket", "local", null, null, null, null));

        mockMvc.perform(post("/api/v1/registry/teams/{teamId}/infra-resources", TEAM_ID)
                        .header("Authorization", "Bearer " + architectToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ──────────────────────────────────────────────
    // Get resources tests
    // ──────────────────────────────────────────────

    @Test
    void getResourcesForTeam_architectRole_returns200() throws Exception {
        when(infraResourceService.getResourcesForTeam(
                any(UUID.class), any(), any(), any(Pageable.class)))
                .thenReturn(new PageResponse<>(
                        List.of(sampleInfraResponse()), 0, 20, 1, 1, true));

        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/infra-resources", TEAM_ID)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].resourceName").value("my-bucket"));
    }

    @Test
    void getResourcesForTeam_withFilters_returns200() throws Exception {
        when(infraResourceService.getResourcesForTeam(
                any(UUID.class), any(), any(), any(Pageable.class)))
                .thenReturn(new PageResponse<>(
                        List.of(sampleInfraResponse()), 0, 20, 1, 1, true));

        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/infra-resources", TEAM_ID)
                        .header("Authorization", "Bearer " + architectToken())
                        .param("type", "S3_BUCKET")
                        .param("environment", "local"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].resourceType").value("S3_BUCKET"));
    }

    @Test
    void getResourcesForService_architectRole_returns200() throws Exception {
        when(infraResourceService.getResourcesForService(SERVICE_ID))
                .thenReturn(List.of(sampleInfraResponse()));

        mockMvc.perform(get("/api/v1/registry/services/{serviceId}/infra-resources", SERVICE_ID)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].resourceName").value("my-bucket"));
    }

    @Test
    void findOrphanedResources_architectRole_returns200() throws Exception {
        when(infraResourceService.findOrphanedResources(TEAM_ID))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/infra-resources/orphans", TEAM_ID)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ──────────────────────────────────────────────
    // Update resource tests
    // ──────────────────────────────────────────────

    @Test
    void updateResource_architectRole_returns200() throws Exception {
        when(infraResourceService.updateResource(any(UUID.class), any(UpdateInfraResourceRequest.class)))
                .thenReturn(sampleInfraResponse());

        String body = objectMapper.writeValueAsString(new UpdateInfraResourceRequest(
                null, "updated-bucket", "us-west-2", null, null, "Updated description"));

        mockMvc.perform(put("/api/v1/registry/infra-resources/{resourceId}", RESOURCE_ID)
                        .header("Authorization", "Bearer " + architectToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceType").value("S3_BUCKET"));
    }

    @Test
    void updateResource_notFound_returns404() throws Exception {
        UUID missingId = UUID.randomUUID();
        when(infraResourceService.updateResource(eq(missingId), any(UpdateInfraResourceRequest.class)))
                .thenThrow(new NotFoundException("InfraResource", missingId));

        String body = objectMapper.writeValueAsString(new UpdateInfraResourceRequest(
                null, "updated-bucket", null, null, null, null));

        mockMvc.perform(put("/api/v1/registry/infra-resources/{resourceId}", missingId)
                        .header("Authorization", "Bearer " + architectToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    // ──────────────────────────────────────────────
    // Delete resource tests
    // ──────────────────────────────────────────────

    @Test
    void deleteResource_architectRole_returns204() throws Exception {
        doNothing().when(infraResourceService).deleteResource(RESOURCE_ID);

        mockMvc.perform(delete("/api/v1/registry/infra-resources/{resourceId}", RESOURCE_ID)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteResource_notFound_returns404() throws Exception {
        UUID missingId = UUID.randomUUID();
        doThrow(new NotFoundException("InfraResource", missingId))
                .when(infraResourceService).deleteResource(missingId);

        mockMvc.perform(delete("/api/v1/registry/infra-resources/{resourceId}", missingId)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isNotFound());
    }

    // ──────────────────────────────────────────────
    // Reassign and orphan tests
    // ──────────────────────────────────────────────

    @Test
    void reassignResource_architectRole_returns200() throws Exception {
        UUID newServiceId = UUID.randomUUID();
        when(infraResourceService.reassignResource(RESOURCE_ID, newServiceId))
                .thenReturn(sampleInfraResponse());

        mockMvc.perform(patch("/api/v1/registry/infra-resources/{resourceId}/reassign", RESOURCE_ID)
                        .header("Authorization", "Bearer " + architectToken())
                        .param("newServiceId", newServiceId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceName").value("my-bucket"));
    }

    @Test
    void reassignResource_notFound_returns404() throws Exception {
        UUID missingId = UUID.randomUUID();
        UUID newServiceId = UUID.randomUUID();
        when(infraResourceService.reassignResource(missingId, newServiceId))
                .thenThrow(new NotFoundException("InfraResource", missingId));

        mockMvc.perform(patch("/api/v1/registry/infra-resources/{resourceId}/reassign", missingId)
                        .header("Authorization", "Bearer " + architectToken())
                        .param("newServiceId", newServiceId.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void reassignResource_crossTeam_returns400() throws Exception {
        UUID newServiceId = UUID.randomUUID();
        when(infraResourceService.reassignResource(RESOURCE_ID, newServiceId))
                .thenThrow(new ValidationException(
                        "Cannot reassign resource to a service in a different team"));

        mockMvc.perform(patch("/api/v1/registry/infra-resources/{resourceId}/reassign", RESOURCE_ID)
                        .header("Authorization", "Bearer " + architectToken())
                        .param("newServiceId", newServiceId.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void orphanResource_architectRole_returns200() throws Exception {
        when(infraResourceService.orphanResource(RESOURCE_ID))
                .thenReturn(sampleInfraResponse());

        mockMvc.perform(patch("/api/v1/registry/infra-resources/{resourceId}/orphan", RESOURCE_ID)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceName").value("my-bucket"));
    }
}
