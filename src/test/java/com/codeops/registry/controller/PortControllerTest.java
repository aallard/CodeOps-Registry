package com.codeops.registry.controller;

import com.codeops.registry.config.JwtProperties;
import com.codeops.registry.dto.request.AllocatePortRequest;
import com.codeops.registry.dto.request.AutoAllocatePortRequest;
import com.codeops.registry.dto.request.UpdatePortRangeRequest;
import com.codeops.registry.dto.response.*;
import com.codeops.registry.entity.enums.PortType;
import com.codeops.registry.exception.NotFoundException;
import com.codeops.registry.exception.ValidationException;
import com.codeops.registry.service.PortAllocationService;
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
class PortControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PortAllocationService portAllocationService;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID SERVICE_ID = UUID.randomUUID();
    private static final UUID ALLOCATION_ID = UUID.randomUUID();
    private static final UUID RANGE_ID = UUID.randomUUID();
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

    private PortAllocationResponse samplePortResponse() {
        return new PortAllocationResponse(
                ALLOCATION_ID, SERVICE_ID, "Test Service", "test-service",
                "local", PortType.HTTP_API, 8080, "TCP",
                null, true, USER_ID, Instant.now());
    }

    private PortRangeResponse sampleRangeResponse() {
        return new PortRangeResponse(
                RANGE_ID, TEAM_ID, PortType.HTTP_API,
                8080, 8199, "local", null);
    }

    // ──────────────────────────────────────────────
    // Authentication tests — no auth → 401
    // ──────────────────────────────────────────────

    @Test
    void autoAllocatePort_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/registry/ports/auto-allocate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPortsForService_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/registry/services/{serviceId}/ports", SERVICE_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void releasePort_noAuth_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/registry/ports/{allocationId}", ALLOCATION_ID))
                .andExpect(status().isUnauthorized());
    }

    // ──────────────────────────────────────────────
    // Authorization tests — MEMBER role → 403
    // ──────────────────────────────────────────────

    @Test
    void autoAllocatePort_memberRole_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(new AutoAllocatePortRequest(
                SERVICE_ID, "local", PortType.HTTP_API, null));

        mockMvc.perform(post("/api/v1/registry/ports/auto-allocate")
                        .header("Authorization", "Bearer " + memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPortsForService_memberRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/registry/services/{serviceId}/ports", SERVICE_ID)
                        .header("Authorization", "Bearer " + memberToken()))
                .andExpect(status().isForbidden());
    }

    // ──────────────────────────────────────────────
    // Auto-allocate tests
    // ──────────────────────────────────────────────

    @Test
    void autoAllocatePort_architectRole_returns201() throws Exception {
        when(portAllocationService.autoAllocate(any(UUID.class), anyString(), any(PortType.class), any(UUID.class)))
                .thenReturn(samplePortResponse());

        String body = objectMapper.writeValueAsString(new AutoAllocatePortRequest(
                SERVICE_ID, "local", PortType.HTTP_API, null));

        mockMvc.perform(post("/api/v1/registry/ports/auto-allocate")
                        .header("Authorization", "Bearer " + architectToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.portNumber").value(8080))
                .andExpect(jsonPath("$.portType").value("HTTP_API"));
    }

    @Test
    void autoAllocatePort_invalidBody_returns400() throws Exception {
        // Missing required fields: serviceId, environment, portType
        mockMvc.perform(post("/api/v1/registry/ports/auto-allocate")
                        .header("Authorization", "Bearer " + architectToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void autoAllocatePort_rangeFull_returns400() throws Exception {
        when(portAllocationService.autoAllocate(any(UUID.class), anyString(), any(PortType.class), any(UUID.class)))
                .thenThrow(new ValidationException("No available ports in range 8080-8199 for type HTTP_API"));

        String body = objectMapper.writeValueAsString(new AutoAllocatePortRequest(
                SERVICE_ID, "local", PortType.HTTP_API, null));

        mockMvc.perform(post("/api/v1/registry/ports/auto-allocate")
                        .header("Authorization", "Bearer " + architectToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ──────────────────────────────────────────────
    // Manual allocate tests
    // ──────────────────────────────────────────────

    @Test
    void manualAllocatePort_architectRole_returns201() throws Exception {
        when(portAllocationService.manualAllocate(any(AllocatePortRequest.class), any(UUID.class)))
                .thenReturn(samplePortResponse());

        String body = objectMapper.writeValueAsString(new AllocatePortRequest(
                SERVICE_ID, "local", PortType.HTTP_API, 8080, null, null));

        mockMvc.perform(post("/api/v1/registry/ports/allocate")
                        .header("Authorization", "Bearer " + architectToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.portNumber").value(8080));
    }

    @Test
    void manualAllocatePort_portTaken_returns400() throws Exception {
        when(portAllocationService.manualAllocate(any(AllocatePortRequest.class), any(UUID.class)))
                .thenThrow(new ValidationException("Port 8080 is already allocated to service Other Service"));

        String body = objectMapper.writeValueAsString(new AllocatePortRequest(
                SERVICE_ID, "local", PortType.HTTP_API, 8080, null, null));

        mockMvc.perform(post("/api/v1/registry/ports/allocate")
                        .header("Authorization", "Bearer " + architectToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ──────────────────────────────────────────────
    // Release tests
    // ──────────────────────────────────────────────

    @Test
    void releasePort_architectRole_returns204() throws Exception {
        doNothing().when(portAllocationService).releasePort(ALLOCATION_ID);

        mockMvc.perform(delete("/api/v1/registry/ports/{allocationId}", ALLOCATION_ID)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isNoContent());
    }

    @Test
    void releasePort_notFound_returns404() throws Exception {
        UUID missingId = UUID.randomUUID();
        doThrow(new NotFoundException("PortAllocation", missingId))
                .when(portAllocationService).releasePort(missingId);

        mockMvc.perform(delete("/api/v1/registry/ports/{allocationId}", missingId)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isNotFound());
    }

    // ──────────────────────────────────────────────
    // Get ports tests
    // ──────────────────────────────────────────────

    @Test
    void getPortsForService_architectRole_returns200() throws Exception {
        when(portAllocationService.getPortsForService(SERVICE_ID, null))
                .thenReturn(List.of(samplePortResponse()));

        mockMvc.perform(get("/api/v1/registry/services/{serviceId}/ports", SERVICE_ID)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].portNumber").value(8080));
    }

    @Test
    void getPortsForService_withEnvironment_returns200() throws Exception {
        when(portAllocationService.getPortsForService(SERVICE_ID, "local"))
                .thenReturn(List.of(samplePortResponse()));

        mockMvc.perform(get("/api/v1/registry/services/{serviceId}/ports", SERVICE_ID)
                        .header("Authorization", "Bearer " + architectToken())
                        .param("environment", "local"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].environment").value("local"));
    }

    @Test
    void getPortsForTeam_architectRole_returns200() throws Exception {
        when(portAllocationService.getPortsForTeam(TEAM_ID, "local"))
                .thenReturn(List.of(samplePortResponse()));

        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/ports", TEAM_ID)
                        .header("Authorization", "Bearer " + architectToken())
                        .param("environment", "local"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getPortsForTeam_missingEnvironment_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/ports", TEAM_ID)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isBadRequest());
    }

    // ──────────────────────────────────────────────
    // Port map, check, conflicts tests
    // ──────────────────────────────────────────────

    @Test
    void getPortMap_architectRole_returns200() throws Exception {
        PortMapResponse portMap = new PortMapResponse(TEAM_ID, "local", List.of(), 0, 0);
        when(portAllocationService.getPortMap(TEAM_ID, "local"))
                .thenReturn(portMap);

        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/ports/map", TEAM_ID)
                        .header("Authorization", "Bearer " + architectToken())
                        .param("environment", "local"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamId").value(TEAM_ID.toString()))
                .andExpect(jsonPath("$.environment").value("local"));
    }

    @Test
    void checkPortAvailability_architectRole_returns200() throws Exception {
        PortCheckResponse check = new PortCheckResponse(8080, "local", true, null, null, null);
        when(portAllocationService.checkAvailability(TEAM_ID, 8080, "local"))
                .thenReturn(check);

        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/ports/check", TEAM_ID)
                        .header("Authorization", "Bearer " + architectToken())
                        .param("portNumber", "8080")
                        .param("environment", "local"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.portNumber").value(8080));
    }

    @Test
    void checkPortAvailability_missingParams_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/ports/check", TEAM_ID)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void detectPortConflicts_architectRole_returns200() throws Exception {
        when(portAllocationService.detectConflicts(TEAM_ID))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/ports/conflicts", TEAM_ID)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ──────────────────────────────────────────────
    // Ranges tests
    // ──────────────────────────────────────────────

    @Test
    void getPortRanges_architectRole_returns200() throws Exception {
        when(portAllocationService.getPortRanges(TEAM_ID))
                .thenReturn(List.of(sampleRangeResponse()));

        mockMvc.perform(get("/api/v1/registry/teams/{teamId}/ports/ranges", TEAM_ID)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].portType").value("HTTP_API"));
    }

    @Test
    void seedDefaultRanges_architectRole_returns200() throws Exception {
        when(portAllocationService.seedDefaultRanges(TEAM_ID, "local"))
                .thenReturn(List.of(sampleRangeResponse()));

        mockMvc.perform(post("/api/v1/registry/teams/{teamId}/ports/ranges/seed", TEAM_ID)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void seedDefaultRanges_withEnvironment_returns200() throws Exception {
        when(portAllocationService.seedDefaultRanges(TEAM_ID, "dev"))
                .thenReturn(List.of(sampleRangeResponse()));

        mockMvc.perform(post("/api/v1/registry/teams/{teamId}/ports/ranges/seed", TEAM_ID)
                        .header("Authorization", "Bearer " + architectToken())
                        .param("environment", "dev"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void updatePortRange_architectRole_returns200() throws Exception {
        when(portAllocationService.updatePortRange(any(UUID.class), any(UpdatePortRangeRequest.class)))
                .thenReturn(sampleRangeResponse());

        String body = objectMapper.writeValueAsString(new UpdatePortRangeRequest(8080, 8199, null));

        mockMvc.perform(put("/api/v1/registry/ports/ranges/{rangeId}", RANGE_ID)
                        .header("Authorization", "Bearer " + architectToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portType").value("HTTP_API"));
    }
}
