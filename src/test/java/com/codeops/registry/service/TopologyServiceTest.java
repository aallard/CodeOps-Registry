package com.codeops.registry.service;

import com.codeops.registry.dto.response.*;
import com.codeops.registry.entity.ServiceDependency;
import com.codeops.registry.entity.ServiceRegistration;
import com.codeops.registry.entity.Solution;
import com.codeops.registry.entity.SolutionMember;
import com.codeops.registry.entity.enums.*;
import com.codeops.registry.exception.NotFoundException;
import com.codeops.registry.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TopologyService}.
 *
 * <p>Tests cover full team topology, solution-scoped topology, service neighborhood,
 * ecosystem stats, layer classification, and max depth computation.</p>
 */
@ExtendWith(MockitoExtension.class)
class TopologyServiceTest {

    @Mock
    private ServiceRegistrationRepository serviceRepository;

    @Mock
    private ServiceDependencyRepository dependencyRepository;

    @Mock
    private SolutionRepository solutionRepository;

    @Mock
    private SolutionMemberRepository solutionMemberRepository;

    @Mock
    private PortAllocationRepository portAllocationRepository;

    @InjectMocks
    private TopologyService topologyService;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    // ──────────────────────────────────────────────────────────────
    // Helper methods
    // ──────────────────────────────────────────────────────────────

    private ServiceRegistration buildService(UUID id, String name, String slug, ServiceType type) {
        ServiceRegistration svc = ServiceRegistration.builder()
                .teamId(TEAM_ID)
                .name(name)
                .slug(slug)
                .serviceType(type)
                .createdByUserId(USER_ID)
                .build();
        svc.setId(id);
        svc.setCreatedAt(Instant.now());
        svc.setUpdatedAt(Instant.now());
        return svc;
    }

    private ServiceRegistration buildServiceWithHealth(UUID id, String name, String slug,
                                                        ServiceType type, HealthStatus health) {
        ServiceRegistration svc = buildService(id, name, slug, type);
        svc.setLastHealthStatus(health);
        svc.setLastHealthCheckAt(Instant.now());
        return svc;
    }

    private ServiceDependency buildDependency(UUID id, ServiceRegistration source,
                                               ServiceRegistration target, DependencyType type) {
        ServiceDependency dep = ServiceDependency.builder()
                .sourceService(source)
                .targetService(target)
                .dependencyType(type)
                .isRequired(true)
                .build();
        dep.setId(id);
        dep.setCreatedAt(Instant.now());
        dep.setUpdatedAt(Instant.now());
        return dep;
    }

    private Solution buildSolution(UUID id, String name, String slug) {
        Solution sol = Solution.builder()
                .teamId(TEAM_ID)
                .name(name)
                .slug(slug)
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
    // getTopology tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void getTopology_completeScenario() {
        UUID svcAId = UUID.randomUUID();
        UUID svcBId = UUID.randomUUID();
        UUID svcCId = UUID.randomUUID();
        UUID solId = UUID.randomUUID();

        ServiceRegistration svcA = buildService(svcAId, "api-server", "api-server", ServiceType.SPRING_BOOT_API);
        ServiceRegistration svcB = buildService(svcBId, "database", "database", ServiceType.DATABASE_SERVICE);
        ServiceRegistration svcC = buildService(svcCId, "frontend", "frontend", ServiceType.REACT_SPA);

        ServiceDependency dep1 = buildDependency(UUID.randomUUID(), svcA, svcB, DependencyType.DATABASE_SHARED);
        ServiceDependency dep2 = buildDependency(UUID.randomUUID(), svcC, svcA, DependencyType.HTTP_REST);

        Solution sol = buildSolution(solId, "My App", "my-app");
        SolutionMember m1 = buildMember(UUID.randomUUID(), sol, svcA);
        SolutionMember m2 = buildMember(UUID.randomUUID(), sol, svcC);

        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(svcA, svcB, svcC));
        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(List.of(dep1, dep2));
        when(solutionRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(sol));
        when(solutionMemberRepository.findBySolutionId(solId)).thenReturn(List.of(m1, m2));
        when(portAllocationRepository.countByServiceId(any(UUID.class))).thenReturn(2L);

        TopologyResponse result = topologyService.getTopology(TEAM_ID);

        assertThat(result.teamId()).isEqualTo(TEAM_ID);
        assertThat(result.nodes()).hasSize(3);
        assertThat(result.edges()).hasSize(2);
        assertThat(result.solutionGroups()).hasSize(1);
        assertThat(result.solutionGroups().get(0).name()).isEqualTo("My App");
        assertThat(result.solutionGroups().get(0).serviceIds()).containsExactlyInAnyOrder(svcAId, svcCId);
        assertThat(result.layers()).isNotEmpty();
        assertThat(result.stats()).isNotNull();
        assertThat(result.stats().totalServices()).isEqualTo(3);
        assertThat(result.stats().totalDependencies()).isEqualTo(2);
        assertThat(result.stats().totalSolutions()).isEqualTo(1);
    }

    @Test
    void getTopology_emptyTeam() {
        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(List.of());
        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(List.of());
        when(solutionRepository.findByTeamId(TEAM_ID)).thenReturn(List.of());

        TopologyResponse result = topologyService.getTopology(TEAM_ID);

        assertThat(result.nodes()).isEmpty();
        assertThat(result.edges()).isEmpty();
        assertThat(result.solutionGroups()).isEmpty();
        assertThat(result.layers()).isEmpty();
        assertThat(result.stats().totalServices()).isEqualTo(0);
    }

    @Test
    void getTopology_servicesInMultipleSolutions() {
        UUID svcId = UUID.randomUUID();
        UUID sol1Id = UUID.randomUUID();
        UUID sol2Id = UUID.randomUUID();

        ServiceRegistration svc = buildService(svcId, "shared-svc", "shared-svc", ServiceType.SPRING_BOOT_API);
        Solution sol1 = buildSolution(sol1Id, "App 1", "app-1");
        Solution sol2 = buildSolution(sol2Id, "App 2", "app-2");

        SolutionMember m1 = buildMember(UUID.randomUUID(), sol1, svc);
        SolutionMember m2 = buildMember(UUID.randomUUID(), sol2, svc);

        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(svc));
        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(List.of());
        when(solutionRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(sol1, sol2));
        when(solutionMemberRepository.findBySolutionId(sol1Id)).thenReturn(List.of(m1));
        when(solutionMemberRepository.findBySolutionId(sol2Id)).thenReturn(List.of(m2));
        when(portAllocationRepository.countByServiceId(svcId)).thenReturn(0L);

        TopologyResponse result = topologyService.getTopology(TEAM_ID);

        TopologyNodeResponse node = result.nodes().get(0);
        assertThat(node.solutionIds()).containsExactlyInAnyOrder(sol1Id, sol2Id);
    }

    @Test
    void getTopology_layerClassification() {
        UUID infraId = UUID.randomUUID();
        UUID backendId = UUID.randomUUID();
        UUID frontendId = UUID.randomUUID();
        UUID standaloneId = UUID.randomUUID();

        ServiceRegistration infra = buildService(infraId, "pg-db", "pg-db", ServiceType.DATABASE_SERVICE);
        ServiceRegistration backend = buildService(backendId, "api", "api", ServiceType.SPRING_BOOT_API);
        ServiceRegistration frontend = buildService(frontendId, "web", "web", ServiceType.REACT_SPA);
        ServiceRegistration standalone = buildService(standaloneId, "lib", "lib", ServiceType.LIBRARY);

        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(infra, backend, frontend, standalone));
        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(List.of());
        when(solutionRepository.findByTeamId(TEAM_ID)).thenReturn(List.of());
        when(portAllocationRepository.countByServiceId(any(UUID.class))).thenReturn(0L);

        TopologyResponse result = topologyService.getTopology(TEAM_ID);

        Map<UUID, String> nodeLayerMap = new HashMap<>();
        for (TopologyNodeResponse node : result.nodes()) {
            nodeLayerMap.put(node.serviceId(), node.layer());
        }

        assertThat(nodeLayerMap.get(infraId)).isEqualTo("infrastructure");
        assertThat(nodeLayerMap.get(backendId)).isEqualTo("backend");
        assertThat(nodeLayerMap.get(frontendId)).isEqualTo("frontend");
        assertThat(nodeLayerMap.get(standaloneId)).isEqualTo("standalone");
    }

    @Test
    void getTopology_orphanedServicesCount() {
        UUID orphanId = UUID.randomUUID();
        UUID connectedId = UUID.randomUUID();

        ServiceRegistration orphan = buildService(orphanId, "orphan", "orphan", ServiceType.SPRING_BOOT_API);
        ServiceRegistration connected = buildService(connectedId, "connected", "connected", ServiceType.SPRING_BOOT_API);

        ServiceDependency dep = buildDependency(UUID.randomUUID(), connected, orphan, DependencyType.HTTP_REST);

        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(orphan, connected));
        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(List.of(dep));
        when(solutionRepository.findByTeamId(TEAM_ID)).thenReturn(List.of());
        when(portAllocationRepository.countByServiceId(any(UUID.class))).thenReturn(0L);

        TopologyResponse result = topologyService.getTopology(TEAM_ID);

        // orphan has downstream (is a target) so not orphaned. connected has upstream. Neither orphaned.
        assertThat(result.stats().orphanedServices()).isEqualTo(0);
    }

    @Test
    void getTopology_trueOrphan_noDepsNoSolutions() {
        UUID orphanId = UUID.randomUUID();

        ServiceRegistration orphan = buildService(orphanId, "orphan", "orphan", ServiceType.SPRING_BOOT_API);

        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(orphan));
        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(List.of());
        when(solutionRepository.findByTeamId(TEAM_ID)).thenReturn(List.of());
        when(portAllocationRepository.countByServiceId(orphanId)).thenReturn(0L);

        TopologyResponse result = topologyService.getTopology(TEAM_ID);

        assertThat(result.stats().orphanedServices()).isEqualTo(1);
    }

    @Test
    void getTopology_maxDependencyDepth_chain() {
        // Chain: A → B → C (depth = 2)
        UUID svcAId = UUID.randomUUID();
        UUID svcBId = UUID.randomUUID();
        UUID svcCId = UUID.randomUUID();

        ServiceRegistration svcA = buildService(svcAId, "svc-a", "svc-a", ServiceType.SPRING_BOOT_API);
        ServiceRegistration svcB = buildService(svcBId, "svc-b", "svc-b", ServiceType.SPRING_BOOT_API);
        ServiceRegistration svcC = buildService(svcCId, "svc-c", "svc-c", ServiceType.DATABASE_SERVICE);

        // A depends on B, B depends on C
        ServiceDependency depAB = buildDependency(UUID.randomUUID(), svcA, svcB, DependencyType.HTTP_REST);
        ServiceDependency depBC = buildDependency(UUID.randomUUID(), svcB, svcC, DependencyType.DATABASE_SHARED);

        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(svcA, svcB, svcC));
        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(List.of(depAB, depBC));
        when(solutionRepository.findByTeamId(TEAM_ID)).thenReturn(List.of());
        when(portAllocationRepository.countByServiceId(any(UUID.class))).thenReturn(0L);

        TopologyResponse result = topologyService.getTopology(TEAM_ID);

        assertThat(result.stats().maxDependencyDepth()).isEqualTo(2);
    }

    @Test
    void getTopology_servicesWithNoDeps_servicesWithNoConsumers() {
        UUID svcAId = UUID.randomUUID();
        UUID svcBId = UUID.randomUUID();
        UUID svcCId = UUID.randomUUID();

        ServiceRegistration svcA = buildService(svcAId, "a", "a", ServiceType.SPRING_BOOT_API);
        ServiceRegistration svcB = buildService(svcBId, "b", "b", ServiceType.DATABASE_SERVICE);
        ServiceRegistration svcC = buildService(svcCId, "c", "c", ServiceType.REACT_SPA);

        // A depends on B. C has no deps.
        ServiceDependency dep = buildDependency(UUID.randomUUID(), svcA, svcB, DependencyType.HTTP_REST);

        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(svcA, svcB, svcC));
        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(List.of(dep));
        when(solutionRepository.findByTeamId(TEAM_ID)).thenReturn(List.of());
        when(portAllocationRepository.countByServiceId(any(UUID.class))).thenReturn(0L);

        TopologyResponse result = topologyService.getTopology(TEAM_ID);

        // B and C have no upstream deps (not sources)
        assertThat(result.stats().servicesWithNoDependencies()).isEqualTo(2);
        // A and C have no downstream deps (not targets)
        assertThat(result.stats().servicesWithNoConsumers()).isEqualTo(2);
    }

    // ──────────────────────────────────────────────────────────────
    // getTopologyForSolution tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void getTopologyForSolution_success() {
        UUID svcAId = UUID.randomUUID();
        UUID svcBId = UUID.randomUUID();
        UUID svcCId = UUID.randomUUID();
        UUID solId = UUID.randomUUID();

        ServiceRegistration svcA = buildService(svcAId, "api", "api", ServiceType.SPRING_BOOT_API);
        ServiceRegistration svcB = buildService(svcBId, "db", "db", ServiceType.DATABASE_SERVICE);
        ServiceRegistration svcC = buildService(svcCId, "external", "external", ServiceType.SPRING_BOOT_API);

        Solution sol = buildSolution(solId, "My App", "my-app");
        SolutionMember m1 = buildMember(UUID.randomUUID(), sol, svcA);
        SolutionMember m2 = buildMember(UUID.randomUUID(), sol, svcB);

        // A → B (both in solution), A → C (C not in solution)
        ServiceDependency dep1 = buildDependency(UUID.randomUUID(), svcA, svcB, DependencyType.DATABASE_SHARED);
        ServiceDependency dep2 = buildDependency(UUID.randomUUID(), svcA, svcC, DependencyType.HTTP_REST);

        when(solutionRepository.findById(solId)).thenReturn(Optional.of(sol));
        when(solutionMemberRepository.findBySolutionId(solId)).thenReturn(List.of(m1, m2));
        when(serviceRepository.findByTeamIdAndIdIn(eq(TEAM_ID), any())).thenReturn(List.of(svcA, svcB));
        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(List.of(dep1, dep2));
        when(portAllocationRepository.countByServiceId(any(UUID.class))).thenReturn(0L);

        TopologyResponse result = topologyService.getTopologyForSolution(solId);

        assertThat(result.nodes()).hasSize(2);
        assertThat(result.edges()).hasSize(1); // Only dep1 (both in solution)
        assertThat(result.edges().get(0).sourceServiceId()).isEqualTo(svcAId);
        assertThat(result.edges().get(0).targetServiceId()).isEqualTo(svcBId);
        assertThat(result.solutionGroups()).hasSize(1);
    }

    @Test
    void getTopologyForSolution_notFound() {
        UUID missingSolId = UUID.randomUUID();
        when(solutionRepository.findById(missingSolId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> topologyService.getTopologyForSolution(missingSolId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Solution");
    }

    @Test
    void getTopologyForSolution_noMembers() {
        UUID solId = UUID.randomUUID();
        Solution sol = buildSolution(solId, "Empty", "empty");

        when(solutionRepository.findById(solId)).thenReturn(Optional.of(sol));
        when(solutionMemberRepository.findBySolutionId(solId)).thenReturn(List.of());
        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(List.of());

        TopologyResponse result = topologyService.getTopologyForSolution(solId);

        assertThat(result.nodes()).isEmpty();
        assertThat(result.edges()).isEmpty();
    }

    @Test
    void getTopologyForSolution_externalDependencyExcluded() {
        UUID svcAId = UUID.randomUUID();
        UUID svcBId = UUID.randomUUID();
        UUID solId = UUID.randomUUID();

        ServiceRegistration svcA = buildService(svcAId, "api", "api", ServiceType.SPRING_BOOT_API);
        ServiceRegistration svcB = buildService(svcBId, "external", "external", ServiceType.SPRING_BOOT_API);

        Solution sol = buildSolution(solId, "Solo", "solo");
        SolutionMember m1 = buildMember(UUID.randomUUID(), sol, svcA);

        // A → B, but B is not in solution
        ServiceDependency dep = buildDependency(UUID.randomUUID(), svcA, svcB, DependencyType.HTTP_REST);

        when(solutionRepository.findById(solId)).thenReturn(Optional.of(sol));
        when(solutionMemberRepository.findBySolutionId(solId)).thenReturn(List.of(m1));
        when(serviceRepository.findByTeamIdAndIdIn(eq(TEAM_ID), any())).thenReturn(List.of(svcA));
        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(List.of(dep));
        when(portAllocationRepository.countByServiceId(any(UUID.class))).thenReturn(0L);

        TopologyResponse result = topologyService.getTopologyForSolution(solId);

        assertThat(result.nodes()).hasSize(1);
        assertThat(result.edges()).isEmpty();
    }

    // ──────────────────────────────────────────────────────────────
    // getServiceNeighborhood tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void getServiceNeighborhood_depth1() {
        UUID centerId = UUID.randomUUID();
        UUID upstreamId = UUID.randomUUID();
        UUID downstream1Id = UUID.randomUUID();
        UUID downstream2Id = UUID.randomUUID();

        ServiceRegistration center = buildService(centerId, "center", "center", ServiceType.SPRING_BOOT_API);
        ServiceRegistration upstream = buildService(upstreamId, "upstream", "upstream", ServiceType.DATABASE_SERVICE);
        ServiceRegistration ds1 = buildService(downstream1Id, "ds1", "ds1", ServiceType.REACT_SPA);
        ServiceRegistration ds2 = buildService(downstream2Id, "ds2", "ds2", ServiceType.SPRING_BOOT_API);

        // center depends on upstream
        ServiceDependency dep1 = buildDependency(UUID.randomUUID(), center, upstream, DependencyType.DATABASE_SHARED);
        // ds1 depends on center
        ServiceDependency dep2 = buildDependency(UUID.randomUUID(), ds1, center, DependencyType.HTTP_REST);
        // ds2 depends on ds1 (2 hops away)
        ServiceDependency dep3 = buildDependency(UUID.randomUUID(), ds2, ds1, DependencyType.HTTP_REST);

        when(serviceRepository.findById(centerId)).thenReturn(Optional.of(center));
        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(List.of(dep1, dep2, dep3));
        when(serviceRepository.findByTeamIdAndIdIn(eq(TEAM_ID), any()))
                .thenReturn(List.of(center, upstream, ds1));
        when(solutionRepository.findByTeamId(TEAM_ID)).thenReturn(List.of());
        when(portAllocationRepository.countByServiceId(any(UUID.class))).thenReturn(0L);

        TopologyResponse result = topologyService.getServiceNeighborhood(centerId, 1);

        // Should include: center, upstream (1 hop up), ds1 (1 hop down). NOT ds2 (2 hops).
        assertThat(result.nodes()).hasSize(3);
    }

    @Test
    void getServiceNeighborhood_depth2_includesTransitive() {
        UUID centerId = UUID.randomUUID();
        UUID upstreamId = UUID.randomUUID();
        UUID transitiveId = UUID.randomUUID();

        ServiceRegistration center = buildService(centerId, "center", "center", ServiceType.SPRING_BOOT_API);
        ServiceRegistration upstream = buildService(upstreamId, "upstream", "upstream", ServiceType.SPRING_BOOT_API);
        ServiceRegistration transitive = buildService(transitiveId, "transitive", "transitive", ServiceType.DATABASE_SERVICE);

        // center → upstream → transitive
        ServiceDependency dep1 = buildDependency(UUID.randomUUID(), center, upstream, DependencyType.HTTP_REST);
        ServiceDependency dep2 = buildDependency(UUID.randomUUID(), upstream, transitive, DependencyType.DATABASE_SHARED);

        when(serviceRepository.findById(centerId)).thenReturn(Optional.of(center));
        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(List.of(dep1, dep2));
        when(serviceRepository.findByTeamIdAndIdIn(eq(TEAM_ID), any()))
                .thenReturn(List.of(center, upstream, transitive));
        when(solutionRepository.findByTeamId(TEAM_ID)).thenReturn(List.of());
        when(portAllocationRepository.countByServiceId(any(UUID.class))).thenReturn(0L);

        TopologyResponse result = topologyService.getServiceNeighborhood(centerId, 2);

        assertThat(result.nodes()).hasSize(3);
    }

    @Test
    void getServiceNeighborhood_serviceNotFound() {
        UUID missingId = UUID.randomUUID();
        when(serviceRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> topologyService.getServiceNeighborhood(missingId, 1))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("ServiceRegistration");
    }

    @Test
    void getServiceNeighborhood_depthCappedAt3() {
        UUID centerId = UUID.randomUUID();
        ServiceRegistration center = buildService(centerId, "center", "center", ServiceType.SPRING_BOOT_API);

        when(serviceRepository.findById(centerId)).thenReturn(Optional.of(center));
        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(List.of());
        when(serviceRepository.findByTeamIdAndIdIn(eq(TEAM_ID), any())).thenReturn(List.of(center));
        when(solutionRepository.findByTeamId(TEAM_ID)).thenReturn(List.of());
        when(portAllocationRepository.countByServiceId(centerId)).thenReturn(0L);

        // Even with depth=10, should work (capped internally at 3)
        TopologyResponse result = topologyService.getServiceNeighborhood(centerId, 10);

        assertThat(result.nodes()).hasSize(1);
    }

    // ──────────────────────────────────────────────────────────────
    // getEcosystemStats tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void getEcosystemStats_correctCounts() {
        UUID svcAId = UUID.randomUUID();
        UUID svcBId = UUID.randomUUID();
        UUID solId = UUID.randomUUID();

        ServiceRegistration svcA = buildService(svcAId, "a", "a", ServiceType.SPRING_BOOT_API);
        ServiceRegistration svcB = buildService(svcBId, "b", "b", ServiceType.DATABASE_SERVICE);

        ServiceDependency dep = buildDependency(UUID.randomUUID(), svcA, svcB, DependencyType.DATABASE_SHARED);

        Solution sol = buildSolution(solId, "App", "app");
        SolutionMember member = buildMember(UUID.randomUUID(), sol, svcA);

        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(svcA, svcB));
        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(List.of(dep));
        when(solutionRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(sol));
        when(solutionMemberRepository.findBySolutionId(solId)).thenReturn(List.of(member));

        TopologyStatsResponse result = topologyService.getEcosystemStats(TEAM_ID);

        assertThat(result.totalServices()).isEqualTo(2);
        assertThat(result.totalDependencies()).isEqualTo(1);
        assertThat(result.totalSolutions()).isEqualTo(1);
        assertThat(result.maxDependencyDepth()).isEqualTo(1);
    }

    @Test
    void getEcosystemStats_noServices() {
        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(List.of());
        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(List.of());
        when(solutionRepository.findByTeamId(TEAM_ID)).thenReturn(List.of());

        TopologyStatsResponse result = topologyService.getEcosystemStats(TEAM_ID);

        assertThat(result.totalServices()).isEqualTo(0);
        assertThat(result.totalDependencies()).isEqualTo(0);
        assertThat(result.totalSolutions()).isEqualTo(0);
        assertThat(result.servicesWithNoDependencies()).isEqualTo(0);
        assertThat(result.servicesWithNoConsumers()).isEqualTo(0);
        assertThat(result.orphanedServices()).isEqualTo(0);
        assertThat(result.maxDependencyDepth()).isEqualTo(0);
    }

    @Test
    void getEcosystemStats_orphanDetection() {
        UUID orphanId = UUID.randomUUID();
        UUID connectedId = UUID.randomUUID();
        UUID solId = UUID.randomUUID();

        ServiceRegistration orphan = buildService(orphanId, "orphan", "orphan", ServiceType.SPRING_BOOT_API);
        ServiceRegistration connected = buildService(connectedId, "connected", "connected", ServiceType.SPRING_BOOT_API);

        // connected is in a solution, orphan is not and has no deps
        Solution sol = buildSolution(solId, "App", "app");
        SolutionMember member = buildMember(UUID.randomUUID(), sol, connected);

        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(orphan, connected));
        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(List.of());
        when(solutionRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(sol));
        when(solutionMemberRepository.findBySolutionId(solId)).thenReturn(List.of(member));

        TopologyStatsResponse result = topologyService.getEcosystemStats(TEAM_ID);

        assertThat(result.orphanedServices()).isEqualTo(1); // orphan has no deps, not in solution
    }

    @Test
    void getEcosystemStats_maxDepthCalculation() {
        UUID svcAId = UUID.randomUUID();
        UUID svcBId = UUID.randomUUID();
        UUID svcCId = UUID.randomUUID();

        ServiceRegistration svcA = buildService(svcAId, "a", "a", ServiceType.SPRING_BOOT_API);
        ServiceRegistration svcB = buildService(svcBId, "b", "b", ServiceType.SPRING_BOOT_API);
        ServiceRegistration svcC = buildService(svcCId, "c", "c", ServiceType.DATABASE_SERVICE);

        // A → B → C (chain of 3, depth = 2)
        ServiceDependency dep1 = buildDependency(UUID.randomUUID(), svcA, svcB, DependencyType.HTTP_REST);
        ServiceDependency dep2 = buildDependency(UUID.randomUUID(), svcB, svcC, DependencyType.DATABASE_SHARED);

        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(svcA, svcB, svcC));
        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(List.of(dep1, dep2));
        when(solutionRepository.findByTeamId(TEAM_ID)).thenReturn(List.of());

        TopologyStatsResponse result = topologyService.getEcosystemStats(TEAM_ID);

        assertThat(result.maxDependencyDepth()).isEqualTo(2);
    }

    // ──────────────────────────────────────────────────────────────
    // classifyLayer tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void classifyLayer_allTypes() {
        assertThat(topologyService.classifyLayer(ServiceType.DATABASE_SERVICE)).isEqualTo("infrastructure");
        assertThat(topologyService.classifyLayer(ServiceType.CACHE_SERVICE)).isEqualTo("infrastructure");
        assertThat(topologyService.classifyLayer(ServiceType.MESSAGE_BROKER)).isEqualTo("infrastructure");
        assertThat(topologyService.classifyLayer(ServiceType.SPRING_BOOT_API)).isEqualTo("backend");
        assertThat(topologyService.classifyLayer(ServiceType.EXPRESS_API)).isEqualTo("backend");
        assertThat(topologyService.classifyLayer(ServiceType.FASTAPI)).isEqualTo("backend");
        assertThat(topologyService.classifyLayer(ServiceType.DOTNET_API)).isEqualTo("backend");
        assertThat(topologyService.classifyLayer(ServiceType.GO_API)).isEqualTo("backend");
        assertThat(topologyService.classifyLayer(ServiceType.WORKER)).isEqualTo("backend");
        assertThat(topologyService.classifyLayer(ServiceType.MCP_SERVER)).isEqualTo("backend");
        assertThat(topologyService.classifyLayer(ServiceType.REACT_SPA)).isEqualTo("frontend");
        assertThat(topologyService.classifyLayer(ServiceType.VUE_SPA)).isEqualTo("frontend");
        assertThat(topologyService.classifyLayer(ServiceType.NEXT_JS)).isEqualTo("frontend");
        assertThat(topologyService.classifyLayer(ServiceType.FLUTTER_WEB)).isEqualTo("frontend");
        assertThat(topologyService.classifyLayer(ServiceType.FLUTTER_DESKTOP)).isEqualTo("frontend");
        assertThat(topologyService.classifyLayer(ServiceType.FLUTTER_MOBILE)).isEqualTo("frontend");
        assertThat(topologyService.classifyLayer(ServiceType.GATEWAY)).isEqualTo("gateway");
        assertThat(topologyService.classifyLayer(ServiceType.LIBRARY)).isEqualTo("standalone");
        assertThat(topologyService.classifyLayer(ServiceType.CLI_TOOL)).isEqualTo("standalone");
        assertThat(topologyService.classifyLayer(ServiceType.OTHER)).isEqualTo("standalone");
    }
}
