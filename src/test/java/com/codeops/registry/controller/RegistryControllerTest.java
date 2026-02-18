package com.codeops.registry.controller;

import com.codeops.registry.config.JwtProperties;
import com.codeops.registry.dto.request.CloneServiceRequest;
import com.codeops.registry.dto.request.CreateServiceRequest;
import com.codeops.registry.dto.request.UpdateServiceRequest;
import com.codeops.registry.dto.request.UpdateServiceStatusRequest;
import com.codeops.registry.dto.response.PageResponse;
import com.codeops.registry.dto.response.ServiceHealthResponse;
import com.codeops.registry.dto.response.ServiceIdentityResponse;
import com.codeops.registry.dto.response.ServiceRegistrationResponse;
import com.codeops.registry.entity.enums.HealthStatus;
import com.codeops.registry.entity.enums.ServiceStatus;
import com.codeops.registry.entity.enums.ServiceType;
import com.codeops.registry.exception.NotFoundException;
import com.codeops.registry.exception.ValidationException;
import com.codeops.registry.service.ServiceRegistryService;
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
class RegistryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ServiceRegistryService serviceRegistryService;

    private static final UUID TEAM_ID = UUID.randomUUID();
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

    private ServiceRegistrationResponse sampleServiceResponse() {
        return new ServiceRegistrationResponse(
                SERVICE_ID, TEAM_ID, "Test Service", "test-service",
                ServiceType.SPRING_BOOT_API, "A test service", null, null,
                "main", null, ServiceStatus.ACTIVE, null, 30,
                null, null, null, null,
                USER_ID, 0, 0, 0,
                Instant.now(), Instant.now());
    }

    private ServiceHealthResponse sampleHealthResponse() {
        return new ServiceHealthResponse(
                SERVICE_ID, "Test Service", "test-service",
                HealthStatus.UP, Instant.now(), "http://localhost:8080/health",
                42, null);
    }

    // ──────────────────────────────────────────────
    // Authentication tests — no auth → 401
    // ──────────────────────────────────────────────

    @Test
    void createService_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/registry/teams/{teamId}/services", TEAM_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getService_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/registry/services/{serviceId}", SERVICE_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteService_noAuth_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/registry/services/{serviceId}", SERVICE_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getServiceBySlug_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/services/by-slug/{slug}", TEAM_ID, "my-svc"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cloneService_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/registry/services/{serviceId}/clone", SERVICE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ──────────────────────────────────────────────
    // Authorization tests — MEMBER role → 403
    // ──────────────────────────────────────────────

    @Test
    void createService_memberRole_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateServiceRequest(
                TEAM_ID, "Test Service", null, ServiceType.SPRING_BOOT_API,
                null, null, null, null, null, null, null, null, null, null, null));

        mockMvc.perform(post("/api/v1/registry/teams/{teamId}/services", TEAM_ID)
                        .header("Authorization", "Bearer " + memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void getService_memberRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/registry/services/{serviceId}", SERVICE_ID)
                        .header("Authorization", "Bearer " + memberToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteService_memberRole_returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/registry/services/{serviceId}", SERVICE_ID)
                        .header("Authorization", "Bearer " + memberToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateServiceStatus_memberRole_returns403() throws Exception {
        mockMvc.perform(patch("/api/v1/registry/services/{serviceId}/status", SERVICE_ID)
                        .header("Authorization", "Bearer " + memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isForbidden());
    }

    // ──────────────────────────────────────────────
    // Happy path tests — ADMIN role
    // ──────────────────────────────────────────────

    @Test
    void createService_architectRole_returns201() throws Exception {
        when(serviceRegistryService.createService(any(CreateServiceRequest.class), any(UUID.class)))
                .thenReturn(sampleServiceResponse());

        String body = objectMapper.writeValueAsString(new CreateServiceRequest(
                TEAM_ID, "Test Service", null, ServiceType.SPRING_BOOT_API,
                null, null, null, null, null, null, null, null, null, null, null));

        mockMvc.perform(post("/api/v1/registry/teams/{teamId}/services", TEAM_ID)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Test Service"))
                .andExpect(jsonPath("$.slug").value("test-service"));
    }

    @Test
    void getServicesForTeam_architectRole_returns200() throws Exception {
        when(serviceRegistryService.getServicesForTeam(
                any(UUID.class), isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(sampleServiceResponse()), 0, 20, 1, 1, true));

        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/services", TEAM_ID)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].name").value("Test Service"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getServicesForTeam_withStatusFilter_returns200() throws Exception {
        when(serviceRegistryService.getServicesForTeam(
                any(UUID.class), eq(ServiceStatus.ACTIVE), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(sampleServiceResponse()), 0, 20, 1, 1, true));

        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/services", TEAM_ID)
                        .header("Authorization", "Bearer " + adminToken())
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));
    }

    @Test
    void getServicesForTeam_withTypeFilter_returns200() throws Exception {
        when(serviceRegistryService.getServicesForTeam(
                any(UUID.class), isNull(), eq(ServiceType.SPRING_BOOT_API), isNull(), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(sampleServiceResponse()), 0, 20, 1, 1, true));

        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/services", TEAM_ID)
                        .header("Authorization", "Bearer " + adminToken())
                        .param("type", "SPRING_BOOT_API"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].serviceType").value("SPRING_BOOT_API"));
    }

    @Test
    void getServicesForTeam_withSearchFilter_returns200() throws Exception {
        when(serviceRegistryService.getServicesForTeam(
                any(UUID.class), isNull(), isNull(), eq("test"), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(sampleServiceResponse()), 0, 20, 1, 1, true));

        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/services", TEAM_ID)
                        .header("Authorization", "Bearer " + adminToken())
                        .param("search", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void getService_architectRole_returns200() throws Exception {
        when(serviceRegistryService.getService(SERVICE_ID))
                .thenReturn(sampleServiceResponse());

        mockMvc.perform(get("/api/v1/registry/services/{serviceId}", SERVICE_ID)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SERVICE_ID.toString()))
                .andExpect(jsonPath("$.name").value("Test Service"));
    }

    @Test
    void getService_notFound_returns404() throws Exception {
        UUID missingId = UUID.randomUUID();
        when(serviceRegistryService.getService(missingId))
                .thenThrow(new NotFoundException("ServiceRegistration", missingId));

        mockMvc.perform(get("/api/v1/registry/services/{serviceId}", missingId)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateService_architectRole_returns200() throws Exception {
        when(serviceRegistryService.updateService(any(UUID.class), any(UpdateServiceRequest.class)))
                .thenReturn(sampleServiceResponse());

        String body = objectMapper.writeValueAsString(new UpdateServiceRequest(
                "Updated Name", null, null, null, null, null, null, null, null, null));

        mockMvc.perform(put("/api/v1/registry/services/{serviceId}", SERVICE_ID)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void updateService_notFound_returns404() throws Exception {
        UUID missingId = UUID.randomUUID();
        when(serviceRegistryService.updateService(eq(missingId), any(UpdateServiceRequest.class)))
                .thenThrow(new NotFoundException("ServiceRegistration", missingId));

        String body = objectMapper.writeValueAsString(new UpdateServiceRequest(
                "Updated Name", null, null, null, null, null, null, null, null, null));

        mockMvc.perform(put("/api/v1/registry/services/{serviceId}", missingId)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteService_architectRole_returns204() throws Exception {
        doNothing().when(serviceRegistryService).deleteService(SERVICE_ID);

        mockMvc.perform(delete("/api/v1/registry/services/{serviceId}", SERVICE_ID)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteService_validationError_returns400() throws Exception {
        doThrow(new ValidationException("Cannot delete service that belongs to solutions"))
                .when(serviceRegistryService).deleteService(SERVICE_ID);

        mockMvc.perform(delete("/api/v1/registry/services/{serviceId}", SERVICE_ID)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateServiceStatus_architectRole_returns200() throws Exception {
        when(serviceRegistryService.updateServiceStatus(any(UUID.class), any(ServiceStatus.class)))
                .thenReturn(sampleServiceResponse());

        String body = objectMapper.writeValueAsString(new UpdateServiceStatusRequest(ServiceStatus.INACTIVE));

        mockMvc.perform(patch("/api/v1/registry/services/{serviceId}/status", SERVICE_ID)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void updateServiceStatus_invalidBody_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/registry/services/{serviceId}/status", SERVICE_ID)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cloneService_architectRole_returns201() throws Exception {
        when(serviceRegistryService.cloneService(any(UUID.class), any(CloneServiceRequest.class), any(UUID.class)))
                .thenReturn(sampleServiceResponse());

        String body = objectMapper.writeValueAsString(new CloneServiceRequest("Cloned Service", null));

        mockMvc.perform(post("/api/v1/registry/services/{serviceId}/clone", SERVICE_ID)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void cloneService_notFound_returns404() throws Exception {
        UUID missingId = UUID.randomUUID();
        when(serviceRegistryService.cloneService(eq(missingId), any(CloneServiceRequest.class), any(UUID.class)))
                .thenThrow(new NotFoundException("ServiceRegistration", missingId));

        String body = objectMapper.writeValueAsString(new CloneServiceRequest("Cloned Service", null));

        mockMvc.perform(post("/api/v1/registry/services/{serviceId}/clone", missingId)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void getServiceIdentity_architectRole_returns200() throws Exception {
        ServiceIdentityResponse identity = new ServiceIdentityResponse(
                sampleServiceResponse(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        when(serviceRegistryService.getServiceIdentity(SERVICE_ID, null))
                .thenReturn(identity);

        mockMvc.perform(get("/api/v1/registry/services/{serviceId}/identity", SERVICE_ID)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service.name").value("Test Service"))
                .andExpect(jsonPath("$.ports").isArray());
    }

    @Test
    void getServiceIdentity_withEnvironment_returns200() throws Exception {
        ServiceIdentityResponse identity = new ServiceIdentityResponse(
                sampleServiceResponse(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        when(serviceRegistryService.getServiceIdentity(SERVICE_ID, "local"))
                .thenReturn(identity);

        mockMvc.perform(get("/api/v1/registry/services/{serviceId}/identity", SERVICE_ID)
                        .header("Authorization", "Bearer " + adminToken())
                        .param("environment", "local"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").exists());
    }

    @Test
    void checkHealth_architectRole_returns200() throws Exception {
        when(serviceRegistryService.checkHealth(SERVICE_ID))
                .thenReturn(sampleHealthResponse());

        mockMvc.perform(post("/api/v1/registry/services/{serviceId}/health", SERVICE_ID)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.healthStatus").value("UP"))
                .andExpect(jsonPath("$.responseTimeMs").value(42));
    }

    @Test
    void checkAllHealth_architectRole_returns200() throws Exception {
        when(serviceRegistryService.checkAllHealth(TEAM_ID))
                .thenReturn(List.of(sampleHealthResponse()));

        mockMvc.perform(post("/api/v1/registry/teams/{teamId}/services/health", TEAM_ID)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].healthStatus").value("UP"));
    }

    @Test
    void getServiceBySlug_architectRole_returns200() throws Exception {
        when(serviceRegistryService.getServiceBySlug(TEAM_ID, "test-service"))
                .thenReturn(sampleServiceResponse());

        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/services/by-slug/{slug}", TEAM_ID, "test-service")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("test-service"));
    }

    // ──────────────────────────────────────────────
    // Validation tests
    // ──────────────────────────────────────────────

    @Test
    void createService_invalidBody_returns400() throws Exception {
        // Missing required fields: name, serviceType
        String body = objectMapper.writeValueAsString(new CreateServiceRequest(
                TEAM_ID, "", null, null,
                null, null, null, null, null, null, null, null, null, null, null));

        mockMvc.perform(post("/api/v1/registry/teams/{teamId}/services", TEAM_ID)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cloneService_invalidBody_returns400() throws Exception {
        // Missing required field: newName
        mockMvc.perform(post("/api/v1/registry/services/{serviceId}/clone", SERVICE_ID)
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newName\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
