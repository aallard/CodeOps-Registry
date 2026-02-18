package com.codeops.registry.controller;

import com.codeops.registry.dto.request.CreateRouteRequest;
import com.codeops.registry.dto.response.ApiRouteResponse;
import com.codeops.registry.dto.response.RouteCheckResponse;
import com.codeops.registry.security.SecurityUtils;
import com.codeops.registry.service.ApiRouteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for API route registration and collision detection.
 *
 * <p>All endpoints require JWT authentication. Write operations require the {@code ADMIN}
 * role or the {@code registry:write} authority; read operations require the {@code ADMIN}
 * role or the {@code registry:read} authority; delete operations require the {@code ADMIN}
 * role or the {@code registry:delete} authority.</p>
 *
 * @see ApiRouteService
 */
@RestController
@RequestMapping("/api/v1/registry")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Routes")
public class RouteController {

    private final ApiRouteService apiRouteService;

    /**
     * Registers an API route prefix for a service.
     *
     * @param request the route creation request (serviceId, routePrefix, environment)
     * @return a 201 response with the created route
     */
    @PostMapping("/routes")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:write')")
    public ResponseEntity<ApiRouteResponse> createRoute(
            @Valid @RequestBody CreateRouteRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(apiRouteService.createRoute(request, userId));
    }

    /**
     * Deletes an API route registration.
     *
     * @param routeId the route ID
     * @return a 204 response with no content
     */
    @DeleteMapping("/routes/{routeId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:delete')")
    public ResponseEntity<Void> deleteRoute(@PathVariable UUID routeId) {
        apiRouteService.deleteRoute(routeId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lists all routes for a service.
     *
     * @param serviceId the service ID
     * @return a 200 response with the list of routes
     */
    @GetMapping("/services/{serviceId}/routes")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:read')")
    public ResponseEntity<List<ApiRouteResponse>> getRoutesForService(
            @PathVariable UUID serviceId) {
        return ResponseEntity.ok(apiRouteService.getRoutesForService(serviceId));
    }

    /**
     * Lists all routes behind a specific gateway in an environment.
     *
     * @param gatewayServiceId the gateway service ID
     * @param environment      the environment (required)
     * @return a 200 response with the list of gateway routes
     */
    @GetMapping("/services/{gatewayServiceId}/routes/gateway")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:read')")
    public ResponseEntity<List<ApiRouteResponse>> getRoutesForGateway(
            @PathVariable UUID gatewayServiceId,
            @RequestParam String environment) {
        return ResponseEntity.ok(apiRouteService.getRoutesForGateway(gatewayServiceId, environment));
    }

    /**
     * Checks whether a route prefix is available behind a gateway in an environment.
     *
     * @param gatewayServiceId the gateway service ID
     * @param environment      the environment
     * @param routePrefix      the route prefix to check
     * @return a 200 response with the availability check result
     */
    @GetMapping("/routes/check")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:read')")
    public ResponseEntity<RouteCheckResponse> checkRouteAvailability(
            @RequestParam UUID gatewayServiceId,
            @RequestParam String environment,
            @RequestParam String routePrefix) {
        return ResponseEntity.ok(apiRouteService.checkRouteAvailability(
                gatewayServiceId, environment, routePrefix));
    }
}
