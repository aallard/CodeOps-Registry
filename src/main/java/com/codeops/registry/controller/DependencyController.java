package com.codeops.registry.controller;

import com.codeops.registry.dto.request.CreateDependencyRequest;
import com.codeops.registry.dto.response.DependencyGraphResponse;
import com.codeops.registry.dto.response.DependencyNodeResponse;
import com.codeops.registry.dto.response.ImpactAnalysisResponse;
import com.codeops.registry.dto.response.ServiceDependencyResponse;
import com.codeops.registry.service.DependencyGraphService;
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
 * REST controller for dependency graph management, impact analysis, and cycle detection.
 *
 * <p>All endpoints require JWT authentication. Write operations require the {@code ADMIN}
 * role or the {@code registry:write} authority; read operations require the {@code ADMIN}
 * role or the {@code registry:read} authority; delete operations require the {@code ADMIN}
 * role or the {@code registry:delete} authority.</p>
 *
 * @see DependencyGraphService
 */
@RestController
@RequestMapping("/api/v1/registry")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Dependencies")
public class DependencyController {

    private final DependencyGraphService dependencyGraphService;

    /**
     * Creates a directed dependency edge between two services.
     *
     * @param request the dependency creation request (sourceServiceId, targetServiceId, dependencyType)
     * @return a 201 response with the created dependency
     */
    @PostMapping("/dependencies")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:write')")
    public ResponseEntity<ServiceDependencyResponse> createDependency(
            @Valid @RequestBody CreateDependencyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dependencyGraphService.createDependency(request));
    }

    /**
     * Removes a dependency edge by ID.
     *
     * @param dependencyId the dependency ID
     * @return a 204 response with no content
     */
    @DeleteMapping("/dependencies/{dependencyId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:delete')")
    public ResponseEntity<Void> removeDependency(@PathVariable UUID dependencyId) {
        dependencyGraphService.removeDependency(dependencyId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Builds the complete dependency graph for a team for visualization.
     *
     * @param teamId the team ID
     * @return a 200 response with the dependency graph (nodes and edges)
     */
    @GetMapping("/teams/{teamId}/dependencies/graph")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:read')")
    public ResponseEntity<DependencyGraphResponse> getDependencyGraph(@PathVariable UUID teamId) {
        return ResponseEntity.ok(dependencyGraphService.getDependencyGraph(teamId));
    }

    /**
     * Performs BFS impact analysis from a source service.
     *
     * @param serviceId the source service ID
     * @return a 200 response with the impact analysis
     */
    @GetMapping("/services/{serviceId}/dependencies/impact")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:read')")
    public ResponseEntity<ImpactAnalysisResponse> getImpactAnalysis(@PathVariable UUID serviceId) {
        return ResponseEntity.ok(dependencyGraphService.getImpactAnalysis(serviceId));
    }

    /**
     * Computes topological startup order using Kahn's algorithm.
     *
     * @param teamId the team ID
     * @return a 200 response with the startup order (list of dependency nodes)
     */
    @GetMapping("/teams/{teamId}/dependencies/startup-order")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:read')")
    public ResponseEntity<List<DependencyNodeResponse>> getStartupOrder(@PathVariable UUID teamId) {
        return ResponseEntity.ok(dependencyGraphService.getStartupOrder(teamId));
    }

    /**
     * Detects cycles in the team's dependency graph.
     *
     * @param teamId the team ID
     * @return a 200 response with the list of service IDs participating in cycles
     */
    @GetMapping("/teams/{teamId}/dependencies/cycles")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:read')")
    public ResponseEntity<List<UUID>> detectCycles(@PathVariable UUID teamId) {
        return ResponseEntity.ok(dependencyGraphService.detectCycles(teamId));
    }
}
