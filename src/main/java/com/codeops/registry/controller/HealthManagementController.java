package com.codeops.registry.controller;

import com.codeops.registry.dto.response.ServiceHealthResponse;
import com.codeops.registry.dto.response.SolutionHealthResponse;
import com.codeops.registry.dto.response.TeamHealthSummaryResponse;
import com.codeops.registry.service.HealthCheckService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for health aggregation, live health checks, and health queries.
 *
 * <p>Provides team-wide summaries, unhealthy service filtering, solution health checks,
 * and cached health data retrieval. Separate from the public {@code /api/v1/health}
 * endpoint (handled by {@code config.HealthController}).</p>
 *
 * <p>All endpoints require JWT authentication. Read operations require the {@code ADMIN}
 * role or the {@code registry:read} authority; write operations (live checks) require the
 * {@code ADMIN} role or the {@code registry:write} authority.</p>
 *
 * @see HealthCheckService
 */
@RestController
@RequestMapping("/api/v1/registry")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Health Management")
public class HealthManagementController {

    private final HealthCheckService healthCheckService;

    /**
     * Aggregates a health summary for all services in a team using cached data.
     *
     * @param teamId the team ID
     * @return a 200 response with the team health summary
     */
    @GetMapping("/teams/{teamId}/health/summary")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:read')")
    public ResponseEntity<TeamHealthSummaryResponse> getTeamHealthSummary(
            @PathVariable UUID teamId) {
        return ResponseEntity.ok(healthCheckService.getTeamHealthSummary(teamId));
    }

    /**
     * Performs live health checks on all active services for a team, then returns
     * the aggregated summary.
     *
     * @param teamId the team ID
     * @return a 200 response with the fresh team health summary
     */
    @PostMapping("/teams/{teamId}/health/check")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:write')")
    public ResponseEntity<TeamHealthSummaryResponse> checkTeamHealth(
            @PathVariable UUID teamId) {
        return ResponseEntity.ok(healthCheckService.checkTeamHealth(teamId));
    }

    /**
     * Returns only DOWN or DEGRADED active services from cached health data.
     *
     * @param teamId the team ID
     * @return a 200 response with the list of unhealthy services
     */
    @GetMapping("/teams/{teamId}/health/unhealthy")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:read')")
    public ResponseEntity<List<ServiceHealthResponse>> getUnhealthyServices(
            @PathVariable UUID teamId) {
        return ResponseEntity.ok(healthCheckService.getUnhealthyServices(teamId));
    }

    /**
     * Returns services that have a health check URL configured but have never been checked.
     *
     * @param teamId the team ID
     * @return a 200 response with the list of never-checked services
     */
    @GetMapping("/teams/{teamId}/health/never-checked")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:read')")
    public ResponseEntity<List<ServiceHealthResponse>> getServicesNeverChecked(
            @PathVariable UUID teamId) {
        return ResponseEntity.ok(healthCheckService.getServicesNeverChecked(teamId));
    }

    /**
     * Performs live health checks on all services in a solution and returns
     * the aggregated solution health.
     *
     * @param solutionId the solution ID
     * @return a 200 response with the solution health
     */
    @PostMapping("/solutions/{solutionId}/health/check")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:write')")
    public ResponseEntity<SolutionHealthResponse> checkSolutionHealth(
            @PathVariable UUID solutionId) {
        return ResponseEntity.ok(healthCheckService.checkSolutionHealth(solutionId));
    }

    /**
     * Returns the current cached health status for a single service without
     * performing a live check.
     *
     * @param serviceId the service ID
     * @return a 200 response with the cached service health
     */
    @GetMapping("/services/{serviceId}/health/cached")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:read')")
    public ResponseEntity<ServiceHealthResponse> getServiceHealthCached(
            @PathVariable UUID serviceId) {
        return ResponseEntity.ok(healthCheckService.getServiceHealthHistory(serviceId));
    }
}
