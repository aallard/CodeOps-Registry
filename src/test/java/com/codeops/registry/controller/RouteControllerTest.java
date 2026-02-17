package com.codeops.registry.controller;

import com.codeops.registry.config.JwtProperties;
import com.codeops.registry.dto.request.CreateRouteRequest;
import com.codeops.registry.dto.response.ApiRouteResponse;
import com.codeops.registry.dto.response.RouteCheckResponse;
import com.codeops.registry.exception.NotFoundException;
import com.codeops.registry.exception.ValidationException;
import com.codeops.registry.service.ApiRouteService;
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
class RouteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ApiRouteService apiRouteService;

    private static final UUID SERVICE_ID = UUID.randomUUID();
    private static final UUID GATEWAY_SERVICE_ID = UUID.randomUUID();
    private static final UUID ROUTE_ID = UUID.randomUUID();
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

    private ApiRouteResponse sampleRouteResponse() {
        return new ApiRouteResponse(
                ROUTE_ID, SERVICE_ID, "Test Service", "test-service",
                GATEWAY_SERVICE_ID, "Gateway Service",
                "/api/v1/test", "GET,POST", "local", "Test route",
                Instant.now());
    }

    // ──────────────────────────────────────────────
    // Authentication tests — no auth → 401
    // ──────────────────────────────────────────────

    @Test
    void createRoute_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/registry/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getRoutesForService_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/registry/services/{serviceId}/routes", SERVICE_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteRoute_noAuth_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/registry/routes/{routeId}", ROUTE_ID))
                .andExpect(status().isUnauthorized());
    }

    // ──────────────────────────────────────────────
    // Authorization tests — MEMBER role → 403
    // ──────────────────────────────────────────────

    @Test
    void createRoute_memberRole_returns403() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateRouteRequest(
                SERVICE_ID, null, "/api/v1/test", "GET", "local", null));

        mockMvc.perform(post("/api/v1/registry/routes")
                        .header("Authorization", "Bearer " + memberToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void getRoutesForService_memberRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/registry/services/{serviceId}/routes", SERVICE_ID)
                        .header("Authorization", "Bearer " + memberToken()))
                .andExpect(status().isForbidden());
    }

    // ──────────────────────────────────────────────
    // Create route tests
    // ──────────────────────────────────────────────

    @Test
    void createRoute_architectRole_returns201() throws Exception {
        when(apiRouteService.createRoute(any(CreateRouteRequest.class), any(UUID.class)))
                .thenReturn(sampleRouteResponse());

        String body = objectMapper.writeValueAsString(new CreateRouteRequest(
                SERVICE_ID, null, "/api/v1/test", "GET,POST", "local", "Test route"));

        mockMvc.perform(post("/api/v1/registry/routes")
                        .header("Authorization", "Bearer " + architectToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.routePrefix").value("/api/v1/test"))
                .andExpect(jsonPath("$.httpMethods").value("GET,POST"));
    }

    @Test
    void createRoute_invalidBody_returns400() throws Exception {
        // Missing required fields: serviceId, routePrefix, environment
        mockMvc.perform(post("/api/v1/registry/routes")
                        .header("Authorization", "Bearer " + architectToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceId\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRoute_conflict_returns400() throws Exception {
        when(apiRouteService.createRoute(any(CreateRouteRequest.class), any(UUID.class)))
                .thenThrow(new ValidationException(
                        "Route prefix '/api/v1/test' conflicts with existing route"));

        String body = objectMapper.writeValueAsString(new CreateRouteRequest(
                SERVICE_ID, null, "/api/v1/test", "GET", "local", null));

        mockMvc.perform(post("/api/v1/registry/routes")
                        .header("Authorization", "Bearer " + architectToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // ──────────────────────────────────────────────
    // Delete route tests
    // ──────────────────────────────────────────────

    @Test
    void deleteRoute_architectRole_returns204() throws Exception {
        doNothing().when(apiRouteService).deleteRoute(ROUTE_ID);

        mockMvc.perform(delete("/api/v1/registry/routes/{routeId}", ROUTE_ID)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteRoute_notFound_returns404() throws Exception {
        UUID missingId = UUID.randomUUID();
        doThrow(new NotFoundException("ApiRouteRegistration", missingId))
                .when(apiRouteService).deleteRoute(missingId);

        mockMvc.perform(delete("/api/v1/registry/routes/{routeId}", missingId)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isNotFound());
    }

    // ──────────────────────────────────────────────
    // Get routes tests
    // ──────────────────────────────────────────────

    @Test
    void getRoutesForService_architectRole_returns200() throws Exception {
        when(apiRouteService.getRoutesForService(SERVICE_ID))
                .thenReturn(List.of(sampleRouteResponse()));

        mockMvc.perform(get("/api/v1/registry/services/{serviceId}/routes", SERVICE_ID)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].routePrefix").value("/api/v1/test"));
    }

    @Test
    void getRoutesForGateway_architectRole_returns200() throws Exception {
        when(apiRouteService.getRoutesForGateway(GATEWAY_SERVICE_ID, "local"))
                .thenReturn(List.of(sampleRouteResponse()));

        mockMvc.perform(get("/api/v1/registry/services/{gatewayServiceId}/routes/gateway",
                        GATEWAY_SERVICE_ID)
                        .header("Authorization", "Bearer " + architectToken())
                        .param("environment", "local"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].routePrefix").value("/api/v1/test"));
    }

    @Test
    void getRoutesForGateway_missingEnvironment_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/registry/services/{gatewayServiceId}/routes/gateway",
                        GATEWAY_SERVICE_ID)
                        .header("Authorization", "Bearer " + architectToken()))
                .andExpect(status().isBadRequest());
    }

    // ──────────────────────────────────────────────
    // Route check tests
    // ──────────────────────────────────────────────

    @Test
    void checkRouteAvailability_available_returns200() throws Exception {
        RouteCheckResponse check = new RouteCheckResponse("/api/v1/test", "local", true, List.of());
        when(apiRouteService.checkRouteAvailability(GATEWAY_SERVICE_ID, "local", "/api/v1/test"))
                .thenReturn(check);

        mockMvc.perform(get("/api/v1/registry/routes/check")
                        .header("Authorization", "Bearer " + architectToken())
                        .param("gatewayServiceId", GATEWAY_SERVICE_ID.toString())
                        .param("environment", "local")
                        .param("routePrefix", "/api/v1/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.routePrefix").value("/api/v1/test"));
    }

    @Test
    void checkRouteAvailability_unavailable_returns200() throws Exception {
        RouteCheckResponse check = new RouteCheckResponse(
                "/api/v1/test", "local", false, List.of(sampleRouteResponse()));
        when(apiRouteService.checkRouteAvailability(GATEWAY_SERVICE_ID, "local", "/api/v1/test"))
                .thenReturn(check);

        mockMvc.perform(get("/api/v1/registry/routes/check")
                        .header("Authorization", "Bearer " + architectToken())
                        .param("gatewayServiceId", GATEWAY_SERVICE_ID.toString())
                        .param("environment", "local")
                        .param("routePrefix", "/api/v1/test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.conflictingRoutes").isArray());
    }
}
