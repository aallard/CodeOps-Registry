package com.codeops.registry.controller;

import com.codeops.registry.dto.request.CreateInfraResourceRequest;
import com.codeops.registry.dto.request.UpdateInfraResourceRequest;
import com.codeops.registry.dto.response.InfraResourceResponse;
import com.codeops.registry.dto.response.PageResponse;
import com.codeops.registry.entity.enums.InfraResourceType;
import com.codeops.registry.security.SecurityUtils;
import com.codeops.registry.service.InfraResourceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for infrastructure resource tracking, orphan detection, and reassignment.
 *
 * <p>All endpoints require JWT authentication. Write operations require the {@code ARCHITECT}
 * role or the {@code registry:write} authority; read operations require the {@code ARCHITECT}
 * role or the {@code registry:read} authority; delete operations require the {@code ARCHITECT}
 * role or the {@code registry:delete} authority.</p>
 *
 * @see InfraResourceService
 */
@RestController
@RequestMapping("/api/v1/registry")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "InfraResources")
public class InfraController {

    private final InfraResourceService infraResourceService;

    /**
     * Registers an infrastructure resource.
     *
     * @param teamId  the team ID (path variable for URL structure)
     * @param request the creation request (teamId, resourceType, resourceName, environment)
     * @return a 201 response with the created resource
     */
    @PostMapping("/teams/{teamId}/infra-resources")
    @PreAuthorize("hasRole('ARCHITECT') or hasAuthority('registry:write')")
    public ResponseEntity<InfraResourceResponse> createResource(
            @PathVariable UUID teamId,
            @Valid @RequestBody CreateInfraResourceRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(infraResourceService.createResource(request, userId));
    }

    /**
     * Lists infrastructure resources for a team with optional type and environment filters.
     *
     * @param teamId      the team ID
     * @param type        optional resource type filter
     * @param environment optional environment filter
     * @param page        page number (default 0)
     * @param size        page size (default 20)
     * @return a 200 response with a paginated list of infrastructure resources
     */
    @GetMapping("/teams/{teamId}/infra-resources")
    @PreAuthorize("hasRole('ARCHITECT') or hasAuthority('registry:read')")
    public ResponseEntity<PageResponse<InfraResourceResponse>> getResourcesForTeam(
            @PathVariable UUID teamId,
            @RequestParam(required = false) InfraResourceType type,
            @RequestParam(required = false) String environment,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(infraResourceService.getResourcesForTeam(
                teamId, type, environment, PageRequest.of(page, size)));
    }

    /**
     * Partially updates an infrastructure resource.
     *
     * @param resourceId the resource ID
     * @param request    the update request with optional fields
     * @return a 200 response with the updated resource
     */
    @PutMapping("/infra-resources/{resourceId}")
    @PreAuthorize("hasRole('ARCHITECT') or hasAuthority('registry:write')")
    public ResponseEntity<InfraResourceResponse> updateResource(
            @PathVariable UUID resourceId,
            @Valid @RequestBody UpdateInfraResourceRequest request) {
        return ResponseEntity.ok(infraResourceService.updateResource(resourceId, request));
    }

    /**
     * Deletes an infrastructure resource.
     *
     * @param resourceId the resource ID
     * @return a 204 response with no content
     */
    @DeleteMapping("/infra-resources/{resourceId}")
    @PreAuthorize("hasRole('ARCHITECT') or hasAuthority('registry:delete')")
    public ResponseEntity<Void> deleteResource(@PathVariable UUID resourceId) {
        infraResourceService.deleteResource(resourceId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lists all infrastructure resources owned by a specific service.
     *
     * @param serviceId the service ID
     * @return a 200 response with the list of resources
     */
    @GetMapping("/services/{serviceId}/infra-resources")
    @PreAuthorize("hasRole('ARCHITECT') or hasAuthority('registry:read')")
    public ResponseEntity<List<InfraResourceResponse>> getResourcesForService(
            @PathVariable UUID serviceId) {
        return ResponseEntity.ok(infraResourceService.getResourcesForService(serviceId));
    }

    /**
     * Finds orphaned resources (resources with no owning service) for a team.
     *
     * @param teamId the team ID
     * @return a 200 response with the list of orphaned resources
     */
    @GetMapping("/teams/{teamId}/infra-resources/orphans")
    @PreAuthorize("hasRole('ARCHITECT') or hasAuthority('registry:read')")
    public ResponseEntity<List<InfraResourceResponse>> findOrphanedResources(
            @PathVariable UUID teamId) {
        return ResponseEntity.ok(infraResourceService.findOrphanedResources(teamId));
    }

    /**
     * Reassigns an infrastructure resource to a different service.
     *
     * @param resourceId   the resource ID
     * @param newServiceId the new owning service ID (required)
     * @return a 200 response with the updated resource
     */
    @PatchMapping("/infra-resources/{resourceId}/reassign")
    @PreAuthorize("hasRole('ARCHITECT') or hasAuthority('registry:write')")
    public ResponseEntity<InfraResourceResponse> reassignResource(
            @PathVariable UUID resourceId,
            @RequestParam UUID newServiceId) {
        return ResponseEntity.ok(infraResourceService.reassignResource(resourceId, newServiceId));
    }

    /**
     * Removes service ownership from a resource, making it orphaned/shared.
     *
     * @param resourceId the resource ID
     * @return a 200 response with the updated resource
     */
    @PatchMapping("/infra-resources/{resourceId}/orphan")
    @PreAuthorize("hasRole('ARCHITECT') or hasAuthority('registry:write')")
    public ResponseEntity<InfraResourceResponse> orphanResource(
            @PathVariable UUID resourceId) {
        return ResponseEntity.ok(infraResourceService.orphanResource(resourceId));
    }
}
