package com.codeops.registry.service;

import com.codeops.registry.dto.request.CreateRouteRequest;
import com.codeops.registry.dto.response.ApiRouteResponse;
import com.codeops.registry.dto.response.RouteCheckResponse;
import com.codeops.registry.entity.ApiRouteRegistration;
import com.codeops.registry.entity.ServiceRegistration;
import com.codeops.registry.exception.NotFoundException;
import com.codeops.registry.exception.ValidationException;
import com.codeops.registry.repository.ApiRouteRegistrationRepository;
import com.codeops.registry.repository.ServiceRegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Service for managing API route registrations and route collision detection.
 *
 * <p>Routes map URL prefixes to services, optionally behind a gateway. The key feature
 * is prefix overlap detection — not just exact matches, but hierarchical prefix conflicts
 * (e.g., {@code /api/v1/users} conflicts with {@code /api/v1/users/admin}).</p>
 *
 * @see ApiRouteRegistration
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ApiRouteService {

    private final ApiRouteRegistrationRepository routeRepository;
    private final ServiceRegistrationRepository serviceRepository;

    /** Pattern for valid route prefix characters: alphanumeric, /, -, _, . */
    private static final Pattern VALID_PREFIX_PATTERN = Pattern.compile("^[a-z0-9/\\-_.]+$");

    /**
     * Registers an API route prefix for a service.
     *
     * <p>Normalizes the prefix, validates the optional gateway (must be in the same team),
     * and checks for prefix overlap conflicts before saving.</p>
     *
     * @param request       the route creation request
     * @param currentUserId the user creating the route
     * @return the created route response
     * @throws NotFoundException   if the service or gateway does not exist
     * @throws ValidationException if the route prefix conflicts with an existing route
     */
    @Transactional
    public ApiRouteResponse createRoute(CreateRouteRequest request, UUID currentUserId) {
        ServiceRegistration service = serviceRepository.findById(request.serviceId())
                .orElseThrow(() -> new NotFoundException("ServiceRegistration", request.serviceId()));

        ServiceRegistration gateway = null;
        if (request.gatewayServiceId() != null) {
            gateway = serviceRepository.findById(request.gatewayServiceId())
                    .orElseThrow(() -> new NotFoundException("ServiceRegistration", request.gatewayServiceId()));

            if (!gateway.getTeamId().equals(service.getTeamId())) {
                throw new ValidationException(
                        "Gateway service must belong to the same team as the owning service");
            }
        }

        String normalizedPrefix = normalizePrefix(request.routePrefix());

        // Check for route collisions
        List<ApiRouteRegistration> overlapping;
        if (gateway != null) {
            overlapping = routeRepository.findOverlappingRoutes(
                    gateway.getId(), request.environment(), normalizedPrefix);
        } else {
            overlapping = routeRepository.findOverlappingDirectRoutes(
                    service.getTeamId(), request.environment(), normalizedPrefix);
        }

        for (ApiRouteRegistration existing : overlapping) {
            if (existing.getService().getId().equals(request.serviceId())) {
                throw new ValidationException(
                        "Service already has a route with overlapping prefix '"
                                + existing.getRoutePrefix() + "'");
            } else {
                throw new ValidationException(
                        "Route prefix '" + normalizedPrefix + "' conflicts with existing route '"
                                + existing.getRoutePrefix() + "' owned by service '"
                                + existing.getService().getName() + "'");
            }
        }

        ApiRouteRegistration entity = ApiRouteRegistration.builder()
                .service(service)
                .gatewayService(gateway)
                .routePrefix(normalizedPrefix)
                .httpMethods(request.httpMethods())
                .environment(request.environment())
                .description(request.description())
                .build();

        entity = routeRepository.save(entity);
        log.info("Route registered: {} → {} (gateway: {})",
                normalizedPrefix, service.getName(),
                gateway != null ? gateway.getName() : "none");

        return mapToResponse(entity);
    }

    /**
     * Deletes an API route registration.
     *
     * @param routeId the route ID
     * @throws NotFoundException if the route does not exist
     */
    @Transactional
    public void deleteRoute(UUID routeId) {
        ApiRouteRegistration entity = routeRepository.findById(routeId)
                .orElseThrow(() -> new NotFoundException("ApiRouteRegistration", routeId));

        routeRepository.delete(entity);
        log.info("Route deleted: {} (service: {})",
                entity.getRoutePrefix(), entity.getService().getName());
    }

    /**
     * Lists all routes for a service.
     *
     * @param serviceId the service ID
     * @return list of route responses
     */
    public List<ApiRouteResponse> getRoutesForService(UUID serviceId) {
        return routeRepository.findByServiceId(serviceId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Lists all routes behind a specific gateway in an environment.
     *
     * @param gatewayServiceId the gateway service ID
     * @param environment      the environment
     * @return list of route responses
     */
    public List<ApiRouteResponse> getRoutesForGateway(UUID gatewayServiceId, String environment) {
        return routeRepository.findByGatewayServiceIdAndEnvironment(gatewayServiceId, environment).stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Checks whether a route prefix is available behind a gateway in an environment.
     *
     * @param gatewayServiceId the gateway service ID
     * @param environment      the environment
     * @param routePrefix      the route prefix to check
     * @return the availability check response with any conflicting routes
     */
    public RouteCheckResponse checkRouteAvailability(UUID gatewayServiceId, String environment, String routePrefix) {
        String normalizedPrefix = normalizePrefix(routePrefix);

        List<ApiRouteRegistration> overlapping = routeRepository.findOverlappingRoutes(
                gatewayServiceId, environment, normalizedPrefix);

        if (overlapping.isEmpty()) {
            return new RouteCheckResponse(normalizedPrefix, environment, true, List.of());
        }

        List<ApiRouteResponse> conflicting = overlapping.stream()
                .map(this::mapToResponse)
                .toList();

        return new RouteCheckResponse(normalizedPrefix, environment, false, conflicting);
    }

    /**
     * Normalizes a route prefix: trims, ensures leading slash, removes trailing slash,
     * lowercases, and validates characters.
     *
     * @param prefix the raw route prefix
     * @return the normalized prefix
     * @throws ValidationException if the prefix contains invalid characters
     */
    String normalizePrefix(String prefix) {
        String normalized = prefix.trim().toLowerCase();

        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }

        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        if (!"/".equals(normalized) && !VALID_PREFIX_PATTERN.matcher(normalized).matches()) {
            throw new ValidationException("Route prefix contains invalid characters");
        }

        return normalized;
    }

    // ──────────────────────────────────────────────
    // Mapping helper
    // ──────────────────────────────────────────────

    private ApiRouteResponse mapToResponse(ApiRouteRegistration entity) {
        ServiceRegistration svc = entity.getService();
        ServiceRegistration gw = entity.getGatewayService();
        return new ApiRouteResponse(
                entity.getId(),
                svc.getId(),
                svc.getName(),
                svc.getSlug(),
                gw != null ? gw.getId() : null,
                gw != null ? gw.getName() : null,
                entity.getRoutePrefix(),
                entity.getHttpMethods(),
                entity.getEnvironment(),
                entity.getDescription(),
                entity.getCreatedAt());
    }
}
