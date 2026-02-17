package com.codeops.registry.service;

import com.codeops.registry.dto.request.CreateRouteRequest;
import com.codeops.registry.dto.response.ApiRouteResponse;
import com.codeops.registry.dto.response.RouteCheckResponse;
import com.codeops.registry.entity.ApiRouteRegistration;
import com.codeops.registry.entity.ServiceRegistration;
import com.codeops.registry.entity.enums.ServiceStatus;
import com.codeops.registry.entity.enums.ServiceType;
import com.codeops.registry.exception.NotFoundException;
import com.codeops.registry.exception.ValidationException;
import com.codeops.registry.repository.ApiRouteRegistrationRepository;
import com.codeops.registry.repository.ServiceRegistrationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ApiRouteService}.
 *
 * <p>Tests cover route creation (with and without gateways), prefix normalization,
 * overlap/collision detection, route deletion, listing, and availability checks.</p>
 */
@ExtendWith(MockitoExtension.class)
class ApiRouteServiceTest {

    @Mock
    private ApiRouteRegistrationRepository routeRepository;

    @Mock
    private ServiceRegistrationRepository serviceRepository;

    @InjectMocks
    private ApiRouteService apiRouteService;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String ENVIRONMENT = "local";

    // ──────────────────────────────────────────────────────────────
    // Helper methods
    // ──────────────────────────────────────────────────────────────

    private ServiceRegistration buildService(UUID serviceId, String name, String slug) {
        ServiceRegistration service = ServiceRegistration.builder()
                .teamId(TEAM_ID)
                .name(name)
                .slug(slug)
                .serviceType(ServiceType.SPRING_BOOT_API)
                .status(ServiceStatus.ACTIVE)
                .createdByUserId(USER_ID)
                .build();
        service.setId(serviceId);
        service.setCreatedAt(Instant.now());
        service.setUpdatedAt(Instant.now());
        return service;
    }

    private ServiceRegistration buildServiceWithTeam(UUID serviceId, String name, String slug, UUID teamId) {
        ServiceRegistration service = ServiceRegistration.builder()
                .teamId(teamId)
                .name(name)
                .slug(slug)
                .serviceType(ServiceType.SPRING_BOOT_API)
                .status(ServiceStatus.ACTIVE)
                .createdByUserId(USER_ID)
                .build();
        service.setId(serviceId);
        service.setCreatedAt(Instant.now());
        service.setUpdatedAt(Instant.now());
        return service;
    }

    private ApiRouteRegistration buildRoute(UUID routeId, ServiceRegistration service,
                                            ServiceRegistration gateway, String prefix,
                                            String environment) {
        ApiRouteRegistration route = ApiRouteRegistration.builder()
                .service(service)
                .gatewayService(gateway)
                .routePrefix(prefix)
                .httpMethods("GET,POST")
                .environment(environment)
                .description("Test route")
                .build();
        route.setId(routeId);
        route.setCreatedAt(Instant.now());
        route.setUpdatedAt(Instant.now());
        return route;
    }

    // ──────────────────────────────────────────────────────────────
    // createRoute tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void createRoute_success() {
        UUID serviceId = UUID.randomUUID();
        UUID routeId = UUID.randomUUID();
        ServiceRegistration service = buildService(serviceId, "User Service", "user-service");

        CreateRouteRequest request = new CreateRouteRequest(
                serviceId, null, "/api/v1/users", "GET,POST", ENVIRONMENT, "User routes");

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(routeRepository.findOverlappingDirectRoutes(TEAM_ID, ENVIRONMENT, "/api/v1/users"))
                .thenReturn(Collections.emptyList());

        ApiRouteRegistration savedEntity = buildRoute(routeId, service, null, "/api/v1/users", ENVIRONMENT);
        when(routeRepository.save(any(ApiRouteRegistration.class))).thenReturn(savedEntity);

        ApiRouteResponse response = apiRouteService.createRoute(request, USER_ID);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(routeId);
        assertThat(response.serviceId()).isEqualTo(serviceId);
        assertThat(response.serviceName()).isEqualTo("User Service");
        assertThat(response.routePrefix()).isEqualTo("/api/v1/users");
        assertThat(response.environment()).isEqualTo(ENVIRONMENT);
        assertThat(response.gatewayServiceId()).isNull();
        assertThat(response.gatewayServiceName()).isNull();

        verify(routeRepository).save(any(ApiRouteRegistration.class));
    }

    @Test
    void createRoute_normalizesPrefix() {
        UUID serviceId = UUID.randomUUID();
        UUID routeId = UUID.randomUUID();
        ServiceRegistration service = buildService(serviceId, "User Service", "user-service");

        CreateRouteRequest request = new CreateRouteRequest(
                serviceId, null, "/API/V1/Users/", "GET", ENVIRONMENT, "Normalized route");

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(routeRepository.findOverlappingDirectRoutes(TEAM_ID, ENVIRONMENT, "/api/v1/users"))
                .thenReturn(Collections.emptyList());

        ApiRouteRegistration savedEntity = buildRoute(routeId, service, null, "/api/v1/users", ENVIRONMENT);
        when(routeRepository.save(any(ApiRouteRegistration.class))).thenReturn(savedEntity);

        ApiRouteResponse response = apiRouteService.createRoute(request, USER_ID);

        assertThat(response.routePrefix()).isEqualTo("/api/v1/users");
        verify(routeRepository).save(any(ApiRouteRegistration.class));
    }

    @Test
    void createRoute_withGateway_success() {
        UUID serviceId = UUID.randomUUID();
        UUID gatewayId = UUID.randomUUID();
        UUID routeId = UUID.randomUUID();
        ServiceRegistration service = buildService(serviceId, "User Service", "user-service");
        ServiceRegistration gateway = buildService(gatewayId, "API Gateway", "api-gateway");

        CreateRouteRequest request = new CreateRouteRequest(
                serviceId, gatewayId, "/api/v1/users", "GET,POST", ENVIRONMENT, "Gateway route");

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(serviceRepository.findById(gatewayId)).thenReturn(Optional.of(gateway));
        when(routeRepository.findOverlappingRoutes(gatewayId, ENVIRONMENT, "/api/v1/users"))
                .thenReturn(Collections.emptyList());

        ApiRouteRegistration savedEntity = buildRoute(routeId, service, gateway, "/api/v1/users", ENVIRONMENT);
        when(routeRepository.save(any(ApiRouteRegistration.class))).thenReturn(savedEntity);

        ApiRouteResponse response = apiRouteService.createRoute(request, USER_ID);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(routeId);
        assertThat(response.serviceId()).isEqualTo(serviceId);
        assertThat(response.gatewayServiceId()).isEqualTo(gatewayId);
        assertThat(response.gatewayServiceName()).isEqualTo("API Gateway");

        verify(routeRepository).findOverlappingRoutes(gatewayId, ENVIRONMENT, "/api/v1/users");
        verify(routeRepository).save(any(ApiRouteRegistration.class));
    }

    @Test
    void createRoute_gatewayDifferentTeam_throwsValidation() {
        UUID serviceId = UUID.randomUUID();
        UUID gatewayId = UUID.randomUUID();
        UUID differentTeamId = UUID.randomUUID();
        ServiceRegistration service = buildService(serviceId, "User Service", "user-service");
        ServiceRegistration gateway = buildServiceWithTeam(gatewayId, "Other Gateway", "other-gateway", differentTeamId);

        CreateRouteRequest request = new CreateRouteRequest(
                serviceId, gatewayId, "/api/v1/users", "GET", ENVIRONMENT, "Cross-team gateway");

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(serviceRepository.findById(gatewayId)).thenReturn(Optional.of(gateway));

        assertThatThrownBy(() -> apiRouteService.createRoute(request, USER_ID))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("same team");

        verify(routeRepository, never()).save(any(ApiRouteRegistration.class));
    }

    @Test
    void createRoute_exactDuplicate_throwsValidation() {
        UUID serviceId = UUID.randomUUID();
        UUID otherServiceId = UUID.randomUUID();
        ServiceRegistration service = buildService(serviceId, "User Service", "user-service");
        ServiceRegistration otherService = buildService(otherServiceId, "Other Service", "other-service");

        ApiRouteRegistration existingRoute = buildRoute(
                UUID.randomUUID(), otherService, null, "/api/v1/users", ENVIRONMENT);

        CreateRouteRequest request = new CreateRouteRequest(
                serviceId, null, "/api/v1/users", "GET", ENVIRONMENT, "Duplicate route");

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(routeRepository.findOverlappingDirectRoutes(TEAM_ID, ENVIRONMENT, "/api/v1/users"))
                .thenReturn(List.of(existingRoute));

        assertThatThrownBy(() -> apiRouteService.createRoute(request, USER_ID))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("conflicts with existing route");

        verify(routeRepository, never()).save(any(ApiRouteRegistration.class));
    }

    @Test
    void createRoute_prefixOverlap_childConflict() {
        UUID serviceId = UUID.randomUUID();
        UUID otherServiceId = UUID.randomUUID();
        ServiceRegistration service = buildService(serviceId, "User Service", "user-service");
        ServiceRegistration otherService = buildService(otherServiceId, "Admin Service", "admin-service");

        ApiRouteRegistration existingRoute = buildRoute(
                UUID.randomUUID(), otherService, null, "/api/v1/users", ENVIRONMENT);

        CreateRouteRequest request = new CreateRouteRequest(
                serviceId, null, "/api/v1/users/admin", "GET", ENVIRONMENT, "Child prefix");

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(routeRepository.findOverlappingDirectRoutes(TEAM_ID, ENVIRONMENT, "/api/v1/users/admin"))
                .thenReturn(List.of(existingRoute));

        assertThatThrownBy(() -> apiRouteService.createRoute(request, USER_ID))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("conflicts with existing route");

        verify(routeRepository, never()).save(any(ApiRouteRegistration.class));
    }

    @Test
    void createRoute_prefixOverlap_parentConflict() {
        UUID serviceId = UUID.randomUUID();
        UUID otherServiceId = UUID.randomUUID();
        ServiceRegistration service = buildService(serviceId, "User Service", "user-service");
        ServiceRegistration otherService = buildService(otherServiceId, "Admin Service", "admin-service");

        ApiRouteRegistration existingRoute = buildRoute(
                UUID.randomUUID(), otherService, null, "/api/v1/users/admin", ENVIRONMENT);

        CreateRouteRequest request = new CreateRouteRequest(
                serviceId, null, "/api/v1/users", "GET", ENVIRONMENT, "Parent prefix");

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(routeRepository.findOverlappingDirectRoutes(TEAM_ID, ENVIRONMENT, "/api/v1/users"))
                .thenReturn(List.of(existingRoute));

        assertThatThrownBy(() -> apiRouteService.createRoute(request, USER_ID))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("conflicts with existing route");

        verify(routeRepository, never()).save(any(ApiRouteRegistration.class));
    }

    @Test
    void createRoute_noOverlap_differentPrefixes() {
        UUID serviceId = UUID.randomUUID();
        UUID routeId = UUID.randomUUID();
        ServiceRegistration service = buildService(serviceId, "Team Service", "team-service");

        CreateRouteRequest request = new CreateRouteRequest(
                serviceId, null, "/api/v1/teams", "GET,POST", ENVIRONMENT, "Teams route");

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(routeRepository.findOverlappingDirectRoutes(TEAM_ID, ENVIRONMENT, "/api/v1/teams"))
                .thenReturn(Collections.emptyList());

        ApiRouteRegistration savedEntity = buildRoute(routeId, service, null, "/api/v1/teams", ENVIRONMENT);
        when(routeRepository.save(any(ApiRouteRegistration.class))).thenReturn(savedEntity);

        ApiRouteResponse response = apiRouteService.createRoute(request, USER_ID);

        assertThat(response).isNotNull();
        assertThat(response.routePrefix()).isEqualTo("/api/v1/teams");

        verify(routeRepository).save(any(ApiRouteRegistration.class));
    }

    @Test
    void createRoute_sameServiceOverlap_throwsValidation() {
        UUID serviceId = UUID.randomUUID();
        ServiceRegistration service = buildService(serviceId, "User Service", "user-service");

        ApiRouteRegistration existingRoute = buildRoute(
                UUID.randomUUID(), service, null, "/api/v1/users", ENVIRONMENT);

        CreateRouteRequest request = new CreateRouteRequest(
                serviceId, null, "/api/v1/users/profile", "GET", ENVIRONMENT, "Profile route");

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(routeRepository.findOverlappingDirectRoutes(TEAM_ID, ENVIRONMENT, "/api/v1/users/profile"))
                .thenReturn(List.of(existingRoute));

        assertThatThrownBy(() -> apiRouteService.createRoute(request, USER_ID))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Service already has a route with overlapping prefix");

        verify(routeRepository, never()).save(any(ApiRouteRegistration.class));
    }

    @Test
    void createRoute_differentGateway_noConflict() {
        UUID serviceId = UUID.randomUUID();
        UUID gatewayId = UUID.randomUUID();
        UUID routeId = UUID.randomUUID();
        ServiceRegistration service = buildService(serviceId, "User Service", "user-service");
        ServiceRegistration gateway = buildService(gatewayId, "Gateway B", "gateway-b");

        CreateRouteRequest request = new CreateRouteRequest(
                serviceId, gatewayId, "/api/v1/users", "GET", ENVIRONMENT, "Different gateway route");

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(serviceRepository.findById(gatewayId)).thenReturn(Optional.of(gateway));
        when(routeRepository.findOverlappingRoutes(gatewayId, ENVIRONMENT, "/api/v1/users"))
                .thenReturn(Collections.emptyList());

        ApiRouteRegistration savedEntity = buildRoute(routeId, service, gateway, "/api/v1/users", ENVIRONMENT);
        when(routeRepository.save(any(ApiRouteRegistration.class))).thenReturn(savedEntity);

        ApiRouteResponse response = apiRouteService.createRoute(request, USER_ID);

        assertThat(response).isNotNull();
        assertThat(response.gatewayServiceId()).isEqualTo(gatewayId);

        verify(routeRepository).findOverlappingRoutes(gatewayId, ENVIRONMENT, "/api/v1/users");
        verify(routeRepository).save(any(ApiRouteRegistration.class));
    }

    @Test
    void createRoute_differentEnvironment_noConflict() {
        UUID serviceId = UUID.randomUUID();
        UUID routeId = UUID.randomUUID();
        ServiceRegistration service = buildService(serviceId, "User Service", "user-service");
        String prodEnvironment = "production";

        CreateRouteRequest request = new CreateRouteRequest(
                serviceId, null, "/api/v1/users", "GET", prodEnvironment, "Prod route");

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(routeRepository.findOverlappingDirectRoutes(TEAM_ID, prodEnvironment, "/api/v1/users"))
                .thenReturn(Collections.emptyList());

        ApiRouteRegistration savedEntity = buildRoute(routeId, service, null, "/api/v1/users", prodEnvironment);
        when(routeRepository.save(any(ApiRouteRegistration.class))).thenReturn(savedEntity);

        ApiRouteResponse response = apiRouteService.createRoute(request, USER_ID);

        assertThat(response).isNotNull();
        assertThat(response.environment()).isEqualTo(prodEnvironment);

        verify(routeRepository).findOverlappingDirectRoutes(TEAM_ID, prodEnvironment, "/api/v1/users");
        verify(routeRepository).save(any(ApiRouteRegistration.class));
    }

    @Test
    void createRoute_invalidCharacters_throwsValidation() {
        UUID serviceId = UUID.randomUUID();
        ServiceRegistration service = buildService(serviceId, "User Service", "user-service");

        CreateRouteRequest request = new CreateRouteRequest(
                serviceId, null, "/api/v1/users with spaces", "GET", ENVIRONMENT, "Bad prefix");

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));

        assertThatThrownBy(() -> apiRouteService.createRoute(request, USER_ID))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("invalid characters");

        verify(routeRepository, never()).save(any(ApiRouteRegistration.class));
    }

    @Test
    void createRoute_serviceNotFound_throwsNotFound() {
        UUID serviceId = UUID.randomUUID();

        CreateRouteRequest request = new CreateRouteRequest(
                serviceId, null, "/api/v1/users", "GET", ENVIRONMENT, "Missing service");

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiRouteService.createRoute(request, USER_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(serviceId.toString());

        verify(routeRepository, never()).save(any(ApiRouteRegistration.class));
    }

    // ──────────────────────────────────────────────────────────────
    // deleteRoute tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void deleteRoute_success() {
        UUID routeId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        ServiceRegistration service = buildService(serviceId, "User Service", "user-service");
        ApiRouteRegistration route = buildRoute(routeId, service, null, "/api/v1/users", ENVIRONMENT);

        when(routeRepository.findById(routeId)).thenReturn(Optional.of(route));

        apiRouteService.deleteRoute(routeId);

        verify(routeRepository).delete(route);
    }

    @Test
    void deleteRoute_notFound_throwsNotFound() {
        UUID routeId = UUID.randomUUID();

        when(routeRepository.findById(routeId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiRouteService.deleteRoute(routeId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(routeId.toString());

        verify(routeRepository, never()).delete(any(ApiRouteRegistration.class));
    }

    // ──────────────────────────────────────────────────────────────
    // getRoutesForService tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void getRoutesForService_returnsList() {
        UUID serviceId = UUID.randomUUID();
        ServiceRegistration service = buildService(serviceId, "User Service", "user-service");
        ApiRouteRegistration route1 = buildRoute(UUID.randomUUID(), service, null, "/api/v1/users", ENVIRONMENT);
        ApiRouteRegistration route2 = buildRoute(UUID.randomUUID(), service, null, "/api/v1/profiles", ENVIRONMENT);

        when(routeRepository.findByServiceId(serviceId)).thenReturn(List.of(route1, route2));

        List<ApiRouteResponse> responses = apiRouteService.getRoutesForService(serviceId);

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(ApiRouteResponse::routePrefix)
                .containsExactly("/api/v1/users", "/api/v1/profiles");
        assertThat(responses).extracting(ApiRouteResponse::serviceId)
                .containsOnly(serviceId);

        verify(routeRepository).findByServiceId(serviceId);
    }

    // ──────────────────────────────────────────────────────────────
    // getRoutesForGateway tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void getRoutesForGateway_returnsList() {
        UUID gatewayId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        ServiceRegistration service = buildService(serviceId, "User Service", "user-service");
        ServiceRegistration gateway = buildService(gatewayId, "API Gateway", "api-gateway");
        ApiRouteRegistration route1 = buildRoute(UUID.randomUUID(), service, gateway, "/api/v1/users", ENVIRONMENT);
        ApiRouteRegistration route2 = buildRoute(UUID.randomUUID(), service, gateway, "/api/v1/teams", ENVIRONMENT);

        when(routeRepository.findByGatewayServiceIdAndEnvironment(gatewayId, ENVIRONMENT))
                .thenReturn(List.of(route1, route2));

        List<ApiRouteResponse> responses = apiRouteService.getRoutesForGateway(gatewayId, ENVIRONMENT);

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(ApiRouteResponse::routePrefix)
                .containsExactly("/api/v1/users", "/api/v1/teams");
        assertThat(responses).extracting(ApiRouteResponse::gatewayServiceId)
                .containsOnly(gatewayId);

        verify(routeRepository).findByGatewayServiceIdAndEnvironment(gatewayId, ENVIRONMENT);
    }

    // ──────────────────────────────────────────────────────────────
    // checkRouteAvailability tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void checkRouteAvailability_available_returnsTrue() {
        UUID gatewayId = UUID.randomUUID();

        when(routeRepository.findOverlappingRoutes(gatewayId, ENVIRONMENT, "/api/v1/users"))
                .thenReturn(Collections.emptyList());

        RouteCheckResponse response = apiRouteService.checkRouteAvailability(
                gatewayId, ENVIRONMENT, "/api/v1/users");

        assertThat(response.available()).isTrue();
        assertThat(response.routePrefix()).isEqualTo("/api/v1/users");
        assertThat(response.environment()).isEqualTo(ENVIRONMENT);
        assertThat(response.conflictingRoutes()).isEmpty();

        verify(routeRepository).findOverlappingRoutes(gatewayId, ENVIRONMENT, "/api/v1/users");
    }

    @Test
    void checkRouteAvailability_conflict_returnsFalseWithDetails() {
        UUID gatewayId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        ServiceRegistration service = buildService(serviceId, "User Service", "user-service");
        ServiceRegistration gateway = buildService(gatewayId, "API Gateway", "api-gateway");
        ApiRouteRegistration conflictingRoute = buildRoute(
                UUID.randomUUID(), service, gateway, "/api/v1/users", ENVIRONMENT);

        when(routeRepository.findOverlappingRoutes(gatewayId, ENVIRONMENT, "/api/v1/users"))
                .thenReturn(List.of(conflictingRoute));

        RouteCheckResponse response = apiRouteService.checkRouteAvailability(
                gatewayId, ENVIRONMENT, "/api/v1/users");

        assertThat(response.available()).isFalse();
        assertThat(response.routePrefix()).isEqualTo("/api/v1/users");
        assertThat(response.environment()).isEqualTo(ENVIRONMENT);
        assertThat(response.conflictingRoutes()).hasSize(1);
        assertThat(response.conflictingRoutes().get(0).serviceId()).isEqualTo(serviceId);
        assertThat(response.conflictingRoutes().get(0).routePrefix()).isEqualTo("/api/v1/users");

        verify(routeRepository).findOverlappingRoutes(gatewayId, ENVIRONMENT, "/api/v1/users");
    }

    // ──────────────────────────────────────────────────────────────
    // normalizePrefix tests (package-private method)
    // ──────────────────────────────────────────────────────────────

    @Test
    void normalizePrefix_addsLeadingSlash() {
        String result = apiRouteService.normalizePrefix("api/v1");

        assertThat(result).isEqualTo("/api/v1");
    }

    @Test
    void normalizePrefix_removesTrailingSlash() {
        String result = apiRouteService.normalizePrefix("/api/v1/");

        assertThat(result).isEqualTo("/api/v1");
    }

    @Test
    void normalizePrefix_lowercases() {
        String result = apiRouteService.normalizePrefix("/API/V1");

        assertThat(result).isEqualTo("/api/v1");
    }
}
