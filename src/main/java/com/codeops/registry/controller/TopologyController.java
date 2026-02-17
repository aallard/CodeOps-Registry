package com.codeops.registry.controller;

import com.codeops.registry.dto.response.TopologyResponse;
import com.codeops.registry.dto.response.TopologyStatsResponse;
import com.codeops.registry.service.TopologyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for topology visualization of the service ecosystem.
 *
 * <p>Provides team-wide, solution-scoped, and neighborhood-scoped topology views
 * with nodes, edges, solution groupings, layer classification, and aggregate statistics.
 * All endpoints require JWT authentication and the {@code ARCHITECT} role or the
 * {@code registry:read} authority.</p>
 *
 * @see TopologyService
 */
@RestController
@RequestMapping("/api/v1/registry")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Topology")
public class TopologyController {

    private final TopologyService topologyService;

    /**
     * Builds a complete ecosystem topology map for a team.
     *
     * @param teamId the team ID
     * @return a 200 response with the full topology (nodes, edges, layers, stats)
     */
    @GetMapping("/teams/{teamId}/topology")
    @PreAuthorize("hasRole('ARCHITECT') or hasAuthority('registry:read')")
    public ResponseEntity<TopologyResponse> getTopology(@PathVariable UUID teamId) {
        return ResponseEntity.ok(topologyService.getTopology(teamId));
    }

    /**
     * Builds a topology view filtered to a specific solution's member services.
     *
     * @param solutionId the solution ID
     * @return a 200 response with the solution-scoped topology
     */
    @GetMapping("/solutions/{solutionId}/topology")
    @PreAuthorize("hasRole('ARCHITECT') or hasAuthority('registry:read')")
    public ResponseEntity<TopologyResponse> getSolutionTopology(@PathVariable UUID solutionId) {
        return ResponseEntity.ok(topologyService.getTopologyForSolution(solutionId));
    }

    /**
     * Builds a topology view for a service and its dependency neighborhood.
     *
     * @param serviceId the center service ID
     * @param depth     the maximum number of hops (defaults to 1, capped at 3)
     * @return a 200 response with the neighborhood topology
     */
    @GetMapping("/services/{serviceId}/topology/neighborhood")
    @PreAuthorize("hasRole('ARCHITECT') or hasAuthority('registry:read')")
    public ResponseEntity<TopologyResponse> getServiceNeighborhood(
            @PathVariable UUID serviceId,
            @RequestParam(defaultValue = "1") int depth) {
        return ResponseEntity.ok(topologyService.getServiceNeighborhood(serviceId, depth));
    }

    /**
     * Computes quick aggregate statistics for a team's ecosystem.
     *
     * @param teamId the team ID
     * @return a 200 response with the topology stats
     */
    @GetMapping("/teams/{teamId}/topology/stats")
    @PreAuthorize("hasRole('ARCHITECT') or hasAuthority('registry:read')")
    public ResponseEntity<TopologyStatsResponse> getEcosystemStats(@PathVariable UUID teamId) {
        return ResponseEntity.ok(topologyService.getEcosystemStats(teamId));
    }
}
