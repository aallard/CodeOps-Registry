package com.codeops.registry.service;

import com.codeops.registry.dto.response.ServiceHealthResponse;
import com.codeops.registry.dto.response.SolutionHealthResponse;
import com.codeops.registry.dto.response.TeamHealthSummaryResponse;
import com.codeops.registry.entity.ServiceRegistration;
import com.codeops.registry.entity.Solution;
import com.codeops.registry.entity.SolutionMember;
import com.codeops.registry.entity.enums.HealthStatus;
import com.codeops.registry.entity.enums.ServiceStatus;
import com.codeops.registry.exception.NotFoundException;
import com.codeops.registry.repository.ServiceRegistrationRepository;
import com.codeops.registry.repository.SolutionMemberRepository;
import com.codeops.registry.repository.SolutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Higher-level health aggregation and querying service.
 *
 * <p>Does NOT duplicate the HTTP health check logic from {@link ServiceRegistryService}.
 * Instead, delegates to {@link ServiceRegistryService#checkHealth(UUID)} and
 * {@link ServiceRegistryService#checkAllHealth(UUID)} for live checks, then provides
 * team-wide summaries, filtering, and cached-data queries on top.</p>
 *
 * @see ServiceRegistryService#checkHealth(UUID)
 * @see TeamHealthSummaryResponse
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class HealthCheckService {

    private final ServiceRegistrationRepository serviceRepository;
    private final SolutionMemberRepository solutionMemberRepository;
    private final SolutionRepository solutionRepository;
    private final ServiceRegistryService serviceRegistryService;

    /**
     * Aggregates a health summary for all services in a team using cached data.
     *
     * <p>No live HTTP checks are performed. Counts services by their
     * {@code lastHealthStatus} and computes a worst-case overall health status.</p>
     *
     * @param teamId the team ID
     * @return the aggregated team health summary
     */
    public TeamHealthSummaryResponse getTeamHealthSummary(UUID teamId) {
        List<ServiceRegistration> services = serviceRepository.findByTeamId(teamId);
        List<ServiceRegistration> active = services.stream()
                .filter(s -> s.getStatus() == ServiceStatus.ACTIVE)
                .toList();

        int up = 0, down = 0, degraded = 0, unknown = 0, neverChecked = 0;
        List<ServiceHealthResponse> unhealthy = new ArrayList<>();

        for (ServiceRegistration svc : services) {
            HealthStatus status = svc.getLastHealthStatus();
            if (status == null) {
                neverChecked++;
                unknown++;
            } else {
                switch (status) {
                    case UP -> up++;
                    case DOWN -> down++;
                    case DEGRADED -> degraded++;
                    case UNKNOWN -> unknown++;
                }
            }

            if (status == HealthStatus.DOWN || status == HealthStatus.DEGRADED) {
                unhealthy.add(mapToHealthResponse(svc));
            }
        }

        HealthStatus overall = computeOverallHealth(services.isEmpty(), down, degraded, unknown);

        return new TeamHealthSummaryResponse(
                teamId,
                services.size(),
                active.size(),
                up, down, degraded, unknown,
                neverChecked,
                overall,
                unhealthy,
                Instant.now());
    }

    /**
     * Performs live health checks on all active services for a team, then returns
     * the aggregated team health summary.
     *
     * <p>Delegates to {@link ServiceRegistryService#checkAllHealth(UUID)} for parallel
     * HTTP health checks, which update entity state. Then builds the summary from
     * the now-fresh cached data.</p>
     *
     * @param teamId the team ID
     * @return the team health summary with fresh health data
     */
    @Transactional
    public TeamHealthSummaryResponse checkTeamHealth(UUID teamId) {
        serviceRegistryService.checkAllHealth(teamId);
        return getTeamHealthSummary(teamId);
    }

    /**
     * Returns only DOWN or DEGRADED active services from cached health data.
     *
     * @param teamId the team ID
     * @return list of unhealthy service health responses
     */
    public List<ServiceHealthResponse> getUnhealthyServices(UUID teamId) {
        List<ServiceRegistration> active = serviceRepository.findByTeamIdAndStatus(teamId, ServiceStatus.ACTIVE);

        return active.stream()
                .filter(svc -> svc.getLastHealthStatus() == HealthStatus.DOWN
                        || svc.getLastHealthStatus() == HealthStatus.DEGRADED)
                .map(this::mapToHealthResponse)
                .toList();
    }

    /**
     * Returns services that have a health check URL configured but have never been checked.
     *
     * @param teamId the team ID
     * @return list of never-checked service health responses
     */
    public List<ServiceHealthResponse> getServicesNeverChecked(UUID teamId) {
        List<ServiceRegistration> services = serviceRepository.findByTeamId(teamId);

        return services.stream()
                .filter(svc -> svc.getLastHealthStatus() == null
                        && svc.getHealthCheckUrl() != null
                        && !svc.getHealthCheckUrl().isBlank())
                .map(this::mapToHealthResponse)
                .toList();
    }

    /**
     * Performs live health checks on all services in a solution and returns
     * the aggregated solution health.
     *
     * <p>Delegates to {@link ServiceRegistryService#checkHealth(UUID)} for each
     * member service in parallel, then aggregates the results.</p>
     *
     * @param solutionId the solution ID
     * @return the aggregated solution health response
     * @throws NotFoundException if the solution does not exist
     */
    @Transactional
    public SolutionHealthResponse checkSolutionHealth(UUID solutionId) {
        Solution solution = solutionRepository.findById(solutionId)
                .orElseThrow(() -> new NotFoundException("Solution", solutionId));

        List<SolutionMember> members = solutionMemberRepository.findBySolutionId(solutionId);

        // Perform parallel live health checks
        List<CompletableFuture<ServiceHealthResponse>> futures = members.stream()
                .map(m -> CompletableFuture.supplyAsync(
                        () -> serviceRegistryService.checkHealth(m.getService().getId())))
                .toList();

        List<ServiceHealthResponse> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        // Aggregate
        int up = 0, down = 0, degraded = 0, unknown = 0;
        for (ServiceHealthResponse r : results) {
            switch (r.healthStatus()) {
                case UP -> up++;
                case DOWN -> down++;
                case DEGRADED -> degraded++;
                case UNKNOWN -> unknown++;
            }
        }

        HealthStatus aggregated = computeOverallHealth(members.isEmpty(), down, degraded, unknown);

        return new SolutionHealthResponse(
                solution.getId(),
                solution.getName(),
                members.size(),
                up, down, degraded, unknown,
                aggregated,
                results);
    }

    /**
     * Returns the current cached health status for a single service without
     * performing a live check.
     *
     * @param serviceId the service ID
     * @return the cached health response
     * @throws NotFoundException if the service does not exist
     */
    public ServiceHealthResponse getServiceHealthHistory(UUID serviceId) {
        ServiceRegistration entity = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new NotFoundException("ServiceRegistration", serviceId));

        return mapToHealthResponse(entity);
    }

    // ──────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────

    /**
     * Maps a service entity to a ServiceHealthResponse using cached health data.
     */
    private ServiceHealthResponse mapToHealthResponse(ServiceRegistration entity) {
        return new ServiceHealthResponse(
                entity.getId(),
                entity.getName(),
                entity.getSlug(),
                entity.getLastHealthStatus() != null ? entity.getLastHealthStatus() : HealthStatus.UNKNOWN,
                entity.getLastHealthCheckAt(),
                entity.getHealthCheckUrl(),
                null,
                null);
    }

    /**
     * Computes worst-case overall health status from individual counts.
     */
    private HealthStatus computeOverallHealth(boolean empty, int down, int degraded, int unknown) {
        if (empty) {
            return HealthStatus.UNKNOWN;
        }
        if (down > 0) {
            return HealthStatus.DOWN;
        }
        if (degraded > 0) {
            return HealthStatus.DEGRADED;
        }
        if (unknown > 0) {
            return HealthStatus.UNKNOWN;
        }
        return HealthStatus.UP;
    }
}
