package com.codeops.registry.controller;

import com.codeops.registry.dto.request.CloneServiceRequest;
import com.codeops.registry.dto.request.CreateServiceRequest;
import com.codeops.registry.dto.request.UpdateServiceRequest;
import com.codeops.registry.dto.request.UpdateServiceStatusRequest;
import com.codeops.registry.dto.response.PageResponse;
import com.codeops.registry.dto.response.ServiceHealthResponse;
import com.codeops.registry.dto.response.ServiceIdentityResponse;
import com.codeops.registry.dto.response.ServiceRegistrationResponse;
import com.codeops.registry.entity.enums.ServiceStatus;
import com.codeops.registry.entity.enums.ServiceType;
import com.codeops.registry.security.SecurityUtils;
import com.codeops.registry.service.ServiceRegistryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for service registration CRUD, identity assembly, and health checking.
 *
 * <p>All endpoints require JWT authentication. Write operations require the {@code ARCHITECT}
 * role or the {@code registry:write} authority; read operations require the {@code ARCHITECT}
 * role or the {@code registry:read} authority.</p>
 *
 * @see ServiceRegistryService
 */
@RestController
@RequestMapping("/api/v1/registry")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Services")
public class RegistryController {

    private final ServiceRegistryService serviceRegistryService;

    /**
     * Registers a new service for a team.
     *
     * @param teamId  the team ID (path segment for REST structure)
     * @param request the service creation request (contains teamId, name, serviceType, etc.)
     * @return a 201 response with the created service registration
     */
    @PostMapping("/teams/{teamId}/services")
    @PreAuthorize("hasRole('ARCHITECT') or hasAuthority('registry:write')")
    public ResponseEntity<ServiceRegistrationResponse> createService(
            @PathVariable UUID teamId,
            @Valid @RequestBody CreateServiceRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        ServiceRegistrationResponse response = serviceRegistryService.createService(request, currentUserId);
        return ResponseEntity.status(201).body(response);
    }

    /**
     * Lists services for a team with optional filtering by status, type, and name search.
     *
     * @param teamId   the team ID
     * @param status   optional service status filter
     * @param type     optional service type filter
     * @param search   optional name search (case-insensitive contains)
     * @param pageable pagination parameters (default size 20)
     * @return a 200 response with the paginated service list
     */
    @GetMapping("/teams/{teamId}/services")
    @PreAuthorize("hasRole('ARCHITECT') or hasAuthority('registry:read')")
    public ResponseEntity<PageResponse<ServiceRegistrationResponse>> getServicesForTeam(
            @PathVariable UUID teamId,
            @RequestParam(required = false) ServiceStatus status,
            @RequestParam(required = false) ServiceType type,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(serviceRegistryService.getServicesForTeam(teamId, status, type, search, pageable));
    }

    /**
     * Retrieves a single service by ID with derived counts.
     *
     * @param serviceId the service ID
     * @return a 200 response with the service registration
     */
    @GetMapping("/services/{serviceId}")
    @PreAuthorize("hasRole('ARCHITECT') or hasAuthority('registry:read')")
    public ResponseEntity<ServiceRegistrationResponse> getService(@PathVariable UUID serviceId) {
        return ResponseEntity.ok(serviceRegistryService.getService(serviceId));
    }

    /**
     * Updates a service registration with non-null fields from the request.
     *
     * @param serviceId the service ID
     * @param request   the update request with optional fields
     * @return a 200 response with the updated service registration
     */
    @PutMapping("/services/{serviceId}")
    @PreAuthorize("hasRole('ARCHITECT') or hasAuthority('registry:write')")
    public ResponseEntity<ServiceRegistrationResponse> updateService(
            @PathVariable UUID serviceId,
            @Valid @RequestBody UpdateServiceRequest request) {
        return ResponseEntity.ok(serviceRegistryService.updateService(serviceId, request));
    }

    /**
     * Deletes a service registration.
     *
     * @param serviceId the service ID
     * @return a 204 response with no content
     */
    @DeleteMapping("/services/{serviceId}")
    @PreAuthorize("hasRole('ARCHITECT') or hasAuthority('registry:write')")
    public ResponseEntity<Void> deleteService(@PathVariable UUID serviceId) {
        serviceRegistryService.deleteService(serviceId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Updates a service's lifecycle status.
     *
     * @param serviceId the service ID
     * @param request   the status update request
     * @return a 200 response with the updated service registration
     */
    @PatchMapping("/services/{serviceId}/status")
    @PreAuthorize("hasRole('ARCHITECT') or hasAuthority('registry:write')")
    public ResponseEntity<ServiceRegistrationResponse> updateServiceStatus(
            @PathVariable UUID serviceId,
            @Valid @RequestBody UpdateServiceStatusRequest request) {
        return ResponseEntity.ok(serviceRegistryService.updateServiceStatus(serviceId, request.status()));
    }

    /**
     * Clones a service under a new name, copying all fields except identity and health state.
     *
     * @param serviceId the original service ID
     * @param request   the clone request with new name and optional slug
     * @return a 201 response with the cloned service registration
     */
    @PostMapping("/services/{serviceId}/clone")
    @PreAuthorize("hasRole('ARCHITECT') or hasAuthority('registry:write')")
    public ResponseEntity<ServiceRegistrationResponse> cloneService(
            @PathVariable UUID serviceId,
            @Valid @RequestBody CloneServiceRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        ServiceRegistrationResponse response = serviceRegistryService.cloneService(serviceId, request, currentUserId);
        return ResponseEntity.status(201).body(response);
    }

    /**
     * Assembles the complete service identity — ports, dependencies, routes, infra, and configs.
     *
     * @param serviceId   the service ID
     * @param environment optional environment filter
     * @return a 200 response with the service identity
     */
    @GetMapping("/services/{serviceId}/identity")
    @PreAuthorize("hasRole('ARCHITECT') or hasAuthority('registry:read')")
    public ResponseEntity<ServiceIdentityResponse> getServiceIdentity(
            @PathVariable UUID serviceId,
            @RequestParam(required = false) String environment) {
        return ResponseEntity.ok(serviceRegistryService.getServiceIdentity(serviceId, environment));
    }

    /**
     * Checks the health of a single service by calling its health check URL.
     *
     * @param serviceId the service ID
     * @return a 200 response with the health check result
     */
    @PostMapping("/services/{serviceId}/health")
    @PreAuthorize("hasRole('ARCHITECT') or hasAuthority('registry:write')")
    public ResponseEntity<ServiceHealthResponse> checkHealth(@PathVariable UUID serviceId) {
        return ResponseEntity.ok(serviceRegistryService.checkHealth(serviceId));
    }

    /**
     * Checks health of all active services for a team in parallel.
     *
     * @param teamId the team ID
     * @return a 200 response with health check results for all active services
     */
    @PostMapping("/teams/{teamId}/services/health")
    @PreAuthorize("hasRole('ARCHITECT') or hasAuthority('registry:write')")
    public ResponseEntity<List<ServiceHealthResponse>> checkAllHealth(@PathVariable UUID teamId) {
        return ResponseEntity.ok(serviceRegistryService.checkAllHealth(teamId));
    }

    /**
     * Retrieves a service by team ID and slug.
     *
     * @param teamId the team ID
     * @param slug   the service slug
     * @return a 200 response with the service registration
     */
    @GetMapping("/teams/{teamId}/services/by-slug/{slug}")
    @PreAuthorize("hasRole('ARCHITECT') or hasAuthority('registry:read')")
    public ResponseEntity<ServiceRegistrationResponse> getServiceBySlug(
            @PathVariable UUID teamId,
            @PathVariable String slug) {
        return ResponseEntity.ok(serviceRegistryService.getServiceBySlug(teamId, slug));
    }
}
