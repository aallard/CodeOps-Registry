package com.codeops.registry.service;

import com.codeops.registry.dto.response.ServiceHealthResponse;
import com.codeops.registry.dto.response.SolutionHealthResponse;
import com.codeops.registry.dto.response.TeamHealthSummaryResponse;
import com.codeops.registry.entity.ServiceRegistration;
import com.codeops.registry.entity.Solution;
import com.codeops.registry.entity.SolutionMember;
import com.codeops.registry.entity.enums.*;
import com.codeops.registry.exception.NotFoundException;
import com.codeops.registry.repository.ServiceRegistrationRepository;
import com.codeops.registry.repository.SolutionMemberRepository;
import com.codeops.registry.repository.SolutionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link HealthCheckService}.
 *
 * <p>Tests cover team health summary (cached), live team health checks, unhealthy
 * service filtering, never-checked service detection, solution health checking,
 * and single service health history.</p>
 */
@ExtendWith(MockitoExtension.class)
class HealthCheckServiceTest {

    @Mock
    private ServiceRegistrationRepository serviceRepository;

    @Mock
    private SolutionMemberRepository solutionMemberRepository;

    @Mock
    private SolutionRepository solutionRepository;

    @Mock
    private ServiceRegistryService serviceRegistryService;

    @InjectMocks
    private HealthCheckService healthCheckService;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    // ──────────────────────────────────────────────────────────────
    // Helper methods
    // ──────────────────────────────────────────────────────────────

    private ServiceRegistration buildService(UUID id, String name, String slug,
                                              HealthStatus healthStatus, String healthUrl) {
        ServiceRegistration svc = ServiceRegistration.builder()
                .teamId(TEAM_ID)
                .name(name)
                .slug(slug)
                .serviceType(ServiceType.SPRING_BOOT_API)
                .healthCheckUrl(healthUrl)
                .createdByUserId(USER_ID)
                .build();
        svc.setId(id);
        svc.setLastHealthStatus(healthStatus);
        if (healthStatus != null) {
            svc.setLastHealthCheckAt(Instant.now());
        }
        svc.setCreatedAt(Instant.now());
        svc.setUpdatedAt(Instant.now());
        return svc;
    }

    private ServiceRegistration buildActiveService(UUID id, String name, String slug,
                                                    HealthStatus healthStatus) {
        ServiceRegistration svc = buildService(id, name, slug, healthStatus, "http://localhost:8080/health");
        svc.setStatus(ServiceStatus.ACTIVE);
        return svc;
    }

    private Solution buildSolution(UUID id, String name) {
        Solution sol = Solution.builder()
                .teamId(TEAM_ID)
                .name(name)
                .slug(name.toLowerCase().replace(" ", "-"))
                .category(SolutionCategory.APPLICATION)
                .createdByUserId(USER_ID)
                .build();
        sol.setId(id);
        sol.setCreatedAt(Instant.now());
        sol.setUpdatedAt(Instant.now());
        return sol;
    }

    private SolutionMember buildMember(UUID id, Solution solution, ServiceRegistration service) {
        SolutionMember member = SolutionMember.builder()
                .solution(solution)
                .service(service)
                .role(SolutionMemberRole.CORE)
                .displayOrder(0)
                .build();
        member.setId(id);
        member.setCreatedAt(Instant.now());
        member.setUpdatedAt(Instant.now());
        return member;
    }

    // ──────────────────────────────────────────────────────────────
    // getTeamHealthSummary tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void getTeamHealthSummary_mixedStatuses() {
        UUID svc1Id = UUID.randomUUID();
        UUID svc2Id = UUID.randomUUID();
        UUID svc3Id = UUID.randomUUID();

        ServiceRegistration svc1 = buildActiveService(svc1Id, "svc-up", "svc-up", HealthStatus.UP);
        ServiceRegistration svc2 = buildActiveService(svc2Id, "svc-down", "svc-down", HealthStatus.DOWN);
        ServiceRegistration svc3 = buildActiveService(svc3Id, "svc-degraded", "svc-degraded", HealthStatus.DEGRADED);

        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(svc1, svc2, svc3));

        TeamHealthSummaryResponse result = healthCheckService.getTeamHealthSummary(TEAM_ID);

        assertThat(result.teamId()).isEqualTo(TEAM_ID);
        assertThat(result.totalServices()).isEqualTo(3);
        assertThat(result.servicesUp()).isEqualTo(1);
        assertThat(result.servicesDown()).isEqualTo(1);
        assertThat(result.servicesDegraded()).isEqualTo(1);
        assertThat(result.overallHealth()).isEqualTo(HealthStatus.DOWN);
        assertThat(result.unhealthyServices()).hasSize(2);
        assertThat(result.checkedAt()).isNotNull();
    }

    @Test
    void getTeamHealthSummary_allUp() {
        UUID svc1Id = UUID.randomUUID();
        UUID svc2Id = UUID.randomUUID();

        ServiceRegistration svc1 = buildActiveService(svc1Id, "a", "a", HealthStatus.UP);
        ServiceRegistration svc2 = buildActiveService(svc2Id, "b", "b", HealthStatus.UP);

        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(svc1, svc2));

        TeamHealthSummaryResponse result = healthCheckService.getTeamHealthSummary(TEAM_ID);

        assertThat(result.overallHealth()).isEqualTo(HealthStatus.UP);
        assertThat(result.servicesUp()).isEqualTo(2);
        assertThat(result.unhealthyServices()).isEmpty();
    }

    @Test
    void getTeamHealthSummary_neverCheckedServices() {
        UUID svc1Id = UUID.randomUUID();
        UUID svc2Id = UUID.randomUUID();

        ServiceRegistration svc1 = buildActiveService(svc1Id, "checked", "checked", HealthStatus.UP);
        ServiceRegistration svc2 = buildService(svc2Id, "unchecked", "unchecked", null, "http://localhost/health");
        svc2.setStatus(ServiceStatus.ACTIVE);

        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(svc1, svc2));

        TeamHealthSummaryResponse result = healthCheckService.getTeamHealthSummary(TEAM_ID);

        assertThat(result.servicesNeverChecked()).isEqualTo(1);
        assertThat(result.servicesUnknown()).isEqualTo(1); // null → counted as unknown
        assertThat(result.overallHealth()).isEqualTo(HealthStatus.UNKNOWN);
    }

    @Test
    void getTeamHealthSummary_noServices() {
        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(List.of());

        TeamHealthSummaryResponse result = healthCheckService.getTeamHealthSummary(TEAM_ID);

        assertThat(result.totalServices()).isEqualTo(0);
        assertThat(result.activeServices()).isEqualTo(0);
        assertThat(result.servicesUp()).isEqualTo(0);
        assertThat(result.overallHealth()).isEqualTo(HealthStatus.UNKNOWN);
        assertThat(result.unhealthyServices()).isEmpty();
    }

    @Test
    void getTeamHealthSummary_unhealthyListContainsOnlyDownAndDegraded() {
        UUID svc1Id = UUID.randomUUID();
        UUID svc2Id = UUID.randomUUID();
        UUID svc3Id = UUID.randomUUID();

        ServiceRegistration svc1 = buildActiveService(svc1Id, "up", "up", HealthStatus.UP);
        ServiceRegistration svc2 = buildActiveService(svc2Id, "down", "down", HealthStatus.DOWN);
        ServiceRegistration svc3 = buildActiveService(svc3Id, "unknown", "unknown", HealthStatus.UNKNOWN);

        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(svc1, svc2, svc3));

        TeamHealthSummaryResponse result = healthCheckService.getTeamHealthSummary(TEAM_ID);

        assertThat(result.unhealthyServices()).hasSize(1);
        assertThat(result.unhealthyServices().get(0).name()).isEqualTo("down");
    }

    // ──────────────────────────────────────────────────────────────
    // checkTeamHealth tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void checkTeamHealth_delegatesAndBuildsSummary() {
        UUID svcId = UUID.randomUUID();
        ServiceRegistration svc = buildActiveService(svcId, "api", "api", HealthStatus.UP);

        when(serviceRegistryService.checkAllHealth(TEAM_ID)).thenReturn(List.of(
                new ServiceHealthResponse(svcId, "api", "api", HealthStatus.UP,
                        Instant.now(), "http://localhost/health", 50, null)));
        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(svc));

        TeamHealthSummaryResponse result = healthCheckService.checkTeamHealth(TEAM_ID);

        verify(serviceRegistryService).checkAllHealth(TEAM_ID);
        assertThat(result.totalServices()).isEqualTo(1);
        assertThat(result.servicesUp()).isEqualTo(1);
    }

    @Test
    void checkTeamHealth_noActiveServices() {
        when(serviceRegistryService.checkAllHealth(TEAM_ID)).thenReturn(List.of());
        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(List.of());

        TeamHealthSummaryResponse result = healthCheckService.checkTeamHealth(TEAM_ID);

        assertThat(result.totalServices()).isEqualTo(0);
        assertThat(result.overallHealth()).isEqualTo(HealthStatus.UNKNOWN);
    }

    @Test
    void checkTeamHealth_multipleServices() {
        UUID svc1Id = UUID.randomUUID();
        UUID svc2Id = UUID.randomUUID();

        ServiceRegistration svc1 = buildActiveService(svc1Id, "a", "a", HealthStatus.UP);
        ServiceRegistration svc2 = buildActiveService(svc2Id, "b", "b", HealthStatus.DOWN);

        when(serviceRegistryService.checkAllHealth(TEAM_ID)).thenReturn(List.of(
                new ServiceHealthResponse(svc1Id, "a", "a", HealthStatus.UP, Instant.now(), null, 10, null),
                new ServiceHealthResponse(svc2Id, "b", "b", HealthStatus.DOWN, Instant.now(), null, 100, "Connection refused")));
        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(svc1, svc2));

        TeamHealthSummaryResponse result = healthCheckService.checkTeamHealth(TEAM_ID);

        assertThat(result.overallHealth()).isEqualTo(HealthStatus.DOWN);
        assertThat(result.unhealthyServices()).hasSize(1);
    }

    // ──────────────────────────────────────────────────────────────
    // getUnhealthyServices tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void getUnhealthyServices_returnsDownAndDegraded() {
        UUID svc1Id = UUID.randomUUID();
        UUID svc2Id = UUID.randomUUID();
        UUID svc3Id = UUID.randomUUID();

        ServiceRegistration up = buildActiveService(svc1Id, "up", "up", HealthStatus.UP);
        ServiceRegistration down = buildActiveService(svc2Id, "down", "down", HealthStatus.DOWN);
        ServiceRegistration degraded = buildActiveService(svc3Id, "degraded", "degraded", HealthStatus.DEGRADED);

        when(serviceRepository.findByTeamIdAndStatus(TEAM_ID, ServiceStatus.ACTIVE))
                .thenReturn(List.of(up, down, degraded));

        List<ServiceHealthResponse> result = healthCheckService.getUnhealthyServices(TEAM_ID);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ServiceHealthResponse::name)
                .containsExactlyInAnyOrder("down", "degraded");
    }

    @Test
    void getUnhealthyServices_allHealthy() {
        UUID svcId = UUID.randomUUID();
        ServiceRegistration svc = buildActiveService(svcId, "up", "up", HealthStatus.UP);

        when(serviceRepository.findByTeamIdAndStatus(TEAM_ID, ServiceStatus.ACTIVE))
                .thenReturn(List.of(svc));

        List<ServiceHealthResponse> result = healthCheckService.getUnhealthyServices(TEAM_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void getUnhealthyServices_inactiveExcluded() {
        // Inactive services are excluded by the repository query (findByTeamIdAndStatus ACTIVE)
        when(serviceRepository.findByTeamIdAndStatus(TEAM_ID, ServiceStatus.ACTIVE))
                .thenReturn(List.of());

        List<ServiceHealthResponse> result = healthCheckService.getUnhealthyServices(TEAM_ID);

        assertThat(result).isEmpty();
    }

    // ──────────────────────────────────────────────────────────────
    // getServicesNeverChecked tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void getServicesNeverChecked_returnsNullStatusWithUrl() {
        UUID svc1Id = UUID.randomUUID();
        UUID svc2Id = UUID.randomUUID();

        ServiceRegistration neverChecked = buildService(svc1Id, "never", "never", null, "http://localhost/health");
        ServiceRegistration checked = buildService(svc2Id, "checked", "checked", HealthStatus.UP, "http://localhost/health");

        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(neverChecked, checked));

        List<ServiceHealthResponse> result = healthCheckService.getServicesNeverChecked(TEAM_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("never");
    }

    @Test
    void getServicesNeverChecked_excludesNoUrl() {
        UUID svcId = UUID.randomUUID();
        ServiceRegistration noUrl = buildService(svcId, "no-url", "no-url", null, null);

        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(noUrl));

        List<ServiceHealthResponse> result = healthCheckService.getServicesNeverChecked(TEAM_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void getServicesNeverChecked_allChecked() {
        UUID svcId = UUID.randomUUID();
        ServiceRegistration checked = buildService(svcId, "checked", "checked", HealthStatus.UP, "http://localhost/health");

        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(checked));

        List<ServiceHealthResponse> result = healthCheckService.getServicesNeverChecked(TEAM_ID);

        assertThat(result).isEmpty();
    }

    // ──────────────────────────────────────────────────────────────
    // checkSolutionHealth tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void checkSolutionHealth_parallelChecksAndAggregation() {
        UUID solId = UUID.randomUUID();
        UUID svc1Id = UUID.randomUUID();
        UUID svc2Id = UUID.randomUUID();
        UUID svc3Id = UUID.randomUUID();

        Solution sol = buildSolution(solId, "My App");

        ServiceRegistration svc1 = buildActiveService(svc1Id, "a", "a", HealthStatus.UP);
        ServiceRegistration svc2 = buildActiveService(svc2Id, "b", "b", HealthStatus.DOWN);
        ServiceRegistration svc3 = buildActiveService(svc3Id, "c", "c", HealthStatus.UP);

        SolutionMember m1 = buildMember(UUID.randomUUID(), sol, svc1);
        SolutionMember m2 = buildMember(UUID.randomUUID(), sol, svc2);
        SolutionMember m3 = buildMember(UUID.randomUUID(), sol, svc3);

        when(solutionRepository.findById(solId)).thenReturn(Optional.of(sol));
        when(solutionMemberRepository.findBySolutionId(solId)).thenReturn(List.of(m1, m2, m3));

        when(serviceRegistryService.checkHealth(svc1Id))
                .thenReturn(new ServiceHealthResponse(svc1Id, "a", "a", HealthStatus.UP, Instant.now(), null, 10, null));
        when(serviceRegistryService.checkHealth(svc2Id))
                .thenReturn(new ServiceHealthResponse(svc2Id, "b", "b", HealthStatus.DOWN, Instant.now(), null, 100, "timeout"));
        when(serviceRegistryService.checkHealth(svc3Id))
                .thenReturn(new ServiceHealthResponse(svc3Id, "c", "c", HealthStatus.UP, Instant.now(), null, 20, null));

        SolutionHealthResponse result = healthCheckService.checkSolutionHealth(solId);

        assertThat(result.solutionId()).isEqualTo(solId);
        assertThat(result.solutionName()).isEqualTo("My App");
        assertThat(result.totalServices()).isEqualTo(3);
        assertThat(result.servicesUp()).isEqualTo(2);
        assertThat(result.servicesDown()).isEqualTo(1);
        assertThat(result.aggregatedHealth()).isEqualTo(HealthStatus.DOWN);
        assertThat(result.serviceHealths()).hasSize(3);

        verify(serviceRegistryService).checkHealth(svc1Id);
        verify(serviceRegistryService).checkHealth(svc2Id);
        verify(serviceRegistryService).checkHealth(svc3Id);
    }

    @Test
    void checkSolutionHealth_notFound() {
        UUID missingSolId = UUID.randomUUID();
        when(solutionRepository.findById(missingSolId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> healthCheckService.checkSolutionHealth(missingSolId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Solution");
    }

    // ──────────────────────────────────────────────────────────────
    // getServiceHealthHistory tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void getServiceHealthHistory_returnsCachedData() {
        UUID svcId = UUID.randomUUID();
        ServiceRegistration svc = buildService(svcId, "api", "api", HealthStatus.UP, "http://localhost/health");

        when(serviceRepository.findById(svcId)).thenReturn(Optional.of(svc));

        ServiceHealthResponse result = healthCheckService.getServiceHealthHistory(svcId);

        assertThat(result.serviceId()).isEqualTo(svcId);
        assertThat(result.name()).isEqualTo("api");
        assertThat(result.healthStatus()).isEqualTo(HealthStatus.UP);
        assertThat(result.healthCheckUrl()).isEqualTo("http://localhost/health");
        assertThat(result.responseTimeMs()).isNull();
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    void getServiceHealthHistory_notFound() {
        UUID missingId = UUID.randomUUID();
        when(serviceRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> healthCheckService.getServiceHealthHistory(missingId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("ServiceRegistration");
    }
}
