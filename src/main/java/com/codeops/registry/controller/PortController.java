package com.codeops.registry.controller;

import com.codeops.registry.dto.request.AllocatePortRequest;
import com.codeops.registry.dto.request.AutoAllocatePortRequest;
import com.codeops.registry.dto.request.UpdatePortRangeRequest;
import com.codeops.registry.dto.response.PortAllocationResponse;
import com.codeops.registry.dto.response.PortCheckResponse;
import com.codeops.registry.dto.response.PortConflictResponse;
import com.codeops.registry.dto.response.PortMapResponse;
import com.codeops.registry.dto.response.PortRangeResponse;
import com.codeops.registry.security.SecurityUtils;
import com.codeops.registry.service.PortAllocationService;
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
 * REST controller for port allocation, range management, and conflict detection.
 *
 * <p>All endpoints require JWT authentication. Write operations require the {@code ADMIN}
 * role or the {@code registry:write} authority; read operations require the {@code ADMIN}
 * role or the {@code registry:read} authority; delete operations require the {@code ADMIN}
 * role or the {@code registry:delete} authority.</p>
 *
 * @see PortAllocationService
 */
@RestController
@RequestMapping("/api/v1/registry")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Ports")
public class PortController {

    private final PortAllocationService portAllocationService;

    /**
     * Auto-allocates the next available port from the team's configured range.
     *
     * @param request the auto-allocate request (serviceId, environment, portType)
     * @return a 201 response with the allocated port
     */
    @PostMapping("/ports/auto-allocate")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:write')")
    public ResponseEntity<PortAllocationResponse> autoAllocatePort(
            @Valid @RequestBody AutoAllocatePortRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(portAllocationService.autoAllocate(
                        request.serviceId(), request.environment(), request.portType(), userId));
    }

    /**
     * Manually allocates a specific port number to a service.
     *
     * @param request the manual allocation request (serviceId, environment, portType, portNumber)
     * @return a 201 response with the allocated port
     */
    @PostMapping("/ports/allocate")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:write')")
    public ResponseEntity<PortAllocationResponse> manualAllocatePort(
            @Valid @RequestBody AllocatePortRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(portAllocationService.manualAllocate(request, userId));
    }

    /**
     * Releases (deletes) a port allocation.
     *
     * @param allocationId the port allocation ID
     * @return a 204 response with no content
     */
    @DeleteMapping("/ports/{allocationId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:delete')")
    public ResponseEntity<Void> releasePort(@PathVariable UUID allocationId) {
        portAllocationService.releasePort(allocationId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lists port allocations for a service, optionally filtered by environment.
     *
     * @param serviceId   the service ID
     * @param environment optional environment filter
     * @return a 200 response with the list of port allocations
     */
    @GetMapping("/services/{serviceId}/ports")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:read')")
    public ResponseEntity<List<PortAllocationResponse>> getPortsForService(
            @PathVariable UUID serviceId,
            @RequestParam(required = false) String environment) {
        return ResponseEntity.ok(portAllocationService.getPortsForService(serviceId, environment));
    }

    /**
     * Lists all port allocations for a team in a specific environment.
     *
     * @param teamId      the team ID
     * @param environment the environment (required)
     * @return a 200 response with the list of port allocations
     */
    @GetMapping("/teams/{teamId}/ports")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:read')")
    public ResponseEntity<List<PortAllocationResponse>> getPortsForTeam(
            @PathVariable UUID teamId,
            @RequestParam String environment) {
        return ResponseEntity.ok(portAllocationService.getPortsForTeam(teamId, environment));
    }

    /**
     * Assembles the structured port map with ranges and their allocations.
     *
     * @param teamId      the team ID
     * @param environment the environment (required)
     * @return a 200 response with the port map
     */
    @GetMapping("/teams/{teamId}/ports/map")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:read')")
    public ResponseEntity<PortMapResponse> getPortMap(
            @PathVariable UUID teamId,
            @RequestParam String environment) {
        return ResponseEntity.ok(portAllocationService.getPortMap(teamId, environment));
    }

    /**
     * Checks whether a specific port number is available in a team and environment.
     *
     * @param teamId      the team ID
     * @param portNumber  the port number to check
     * @param environment the environment to check in
     * @return a 200 response with the port availability result
     */
    @GetMapping("/teams/{teamId}/ports/check")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:read')")
    public ResponseEntity<PortCheckResponse> checkPortAvailability(
            @PathVariable UUID teamId,
            @RequestParam int portNumber,
            @RequestParam String environment) {
        return ResponseEntity.ok(portAllocationService.checkAvailability(teamId, portNumber, environment));
    }

    /**
     * Detects port conflicts within a team (same port allocated to multiple services).
     *
     * @param teamId the team ID
     * @return a 200 response with the list of port conflicts
     */
    @GetMapping("/teams/{teamId}/ports/conflicts")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:read')")
    public ResponseEntity<List<PortConflictResponse>> detectPortConflicts(@PathVariable UUID teamId) {
        return ResponseEntity.ok(portAllocationService.detectConflicts(teamId));
    }

    /**
     * Lists all port ranges configured for a team.
     *
     * @param teamId the team ID
     * @return a 200 response with the list of port ranges
     */
    @GetMapping("/teams/{teamId}/ports/ranges")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:read')")
    public ResponseEntity<List<PortRangeResponse>> getPortRanges(@PathVariable UUID teamId) {
        return ResponseEntity.ok(portAllocationService.getPortRanges(teamId));
    }

    /**
     * Seeds default port ranges for a team using application constants.
     *
     * @param teamId      the team ID
     * @param environment the environment to seed ranges for (defaults to "local")
     * @return a 200 response with the created or existing port ranges
     */
    @PostMapping("/teams/{teamId}/ports/ranges/seed")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:write')")
    public ResponseEntity<List<PortRangeResponse>> seedDefaultRanges(
            @PathVariable UUID teamId,
            @RequestParam(defaultValue = "local") String environment) {
        return ResponseEntity.ok(portAllocationService.seedDefaultRanges(teamId, environment));
    }

    /**
     * Updates a port range's start, end, and optional description.
     *
     * @param rangeId the port range ID
     * @param request the update request (rangeStart, rangeEnd, description)
     * @return a 200 response with the updated port range
     */
    @PutMapping("/ports/ranges/{rangeId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:write')")
    public ResponseEntity<PortRangeResponse> updatePortRange(
            @PathVariable UUID rangeId,
            @Valid @RequestBody UpdatePortRangeRequest request) {
        return ResponseEntity.ok(portAllocationService.updatePortRange(rangeId, request));
    }
}
