package com.codeops.registry.service;

import com.codeops.registry.config.AppConstants;
import com.codeops.registry.dto.request.CreateDependencyRequest;
import com.codeops.registry.dto.response.DependencyEdgeResponse;
import com.codeops.registry.dto.response.DependencyGraphResponse;
import com.codeops.registry.dto.response.DependencyNodeResponse;
import com.codeops.registry.dto.response.ImpactAnalysisResponse;
import com.codeops.registry.dto.response.ImpactedServiceResponse;
import com.codeops.registry.dto.response.ServiceDependencyResponse;
import com.codeops.registry.entity.ServiceDependency;
import com.codeops.registry.entity.ServiceRegistration;
import com.codeops.registry.entity.enums.DependencyType;
import com.codeops.registry.entity.enums.HealthStatus;
import com.codeops.registry.entity.enums.ServiceStatus;
import com.codeops.registry.entity.enums.ServiceType;
import com.codeops.registry.exception.NotFoundException;
import com.codeops.registry.exception.ValidationException;
import com.codeops.registry.repository.ServiceDependencyRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DependencyGraphService}.
 *
 * <p>Tests cover dependency CRUD, graph visualization, BFS impact analysis,
 * Kahn's topological startup ordering, DFS cycle detection, and the
 * package-private {@code hasPath} BFS reachability check.</p>
 */
@ExtendWith(MockitoExtension.class)
class DependencyGraphServiceTest {

    @Mock
    private ServiceDependencyRepository dependencyRepository;

    @Mock
    private ServiceRegistrationRepository serviceRepository;

    @InjectMocks
    private DependencyGraphService dependencyGraphService;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

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

    private ServiceRegistration buildService(UUID serviceId, String name, String slug, UUID teamId) {
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

    private ServiceDependency buildDependency(UUID depId, ServiceRegistration source,
                                               ServiceRegistration target, DependencyType type) {
        ServiceDependency dep = ServiceDependency.builder()
                .sourceService(source)
                .targetService(target)
                .dependencyType(type)
                .isRequired(true)
                .build();
        dep.setId(depId);
        dep.setCreatedAt(Instant.now());
        dep.setUpdatedAt(Instant.now());
        return dep;
    }

    // ──────────────────────────────────────────────────────────────
    // createDependency tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void createDependency_success() {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        ServiceRegistration source = buildService(sourceId, "api-gateway", "api-gateway");
        ServiceRegistration target = buildService(targetId, "user-service", "user-service");

        CreateDependencyRequest request = new CreateDependencyRequest(
                sourceId, targetId, DependencyType.HTTP_REST, "REST call", true, "/api/users");

        when(serviceRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(serviceRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(dependencyRepository.findBySourceServiceIdAndTargetServiceIdAndDependencyType(
                sourceId, targetId, DependencyType.HTTP_REST)).thenReturn(Optional.empty());
        when(dependencyRepository.countBySourceServiceId(sourceId)).thenReturn(0L);
        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(Collections.emptyList());
        when(dependencyRepository.save(any(ServiceDependency.class))).thenAnswer(invocation -> {
            ServiceDependency saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(Instant.now());
            return saved;
        });

        ServiceDependencyResponse response = dependencyGraphService.createDependency(request);

        assertThat(response).isNotNull();
        assertThat(response.sourceServiceId()).isEqualTo(sourceId);
        assertThat(response.targetServiceId()).isEqualTo(targetId);
        assertThat(response.dependencyType()).isEqualTo(DependencyType.HTTP_REST);
        assertThat(response.isRequired()).isTrue();
        assertThat(response.targetEndpoint()).isEqualTo("/api/users");
        assertThat(response.description()).isEqualTo("REST call");
        verify(dependencyRepository).save(any(ServiceDependency.class));
    }

    @Test
    void createDependency_selfDependency() {
        UUID serviceId = UUID.randomUUID();
        CreateDependencyRequest request = new CreateDependencyRequest(
                serviceId, serviceId, DependencyType.HTTP_REST, null, null, null);

        assertThatThrownBy(() -> dependencyGraphService.createDependency(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cannot depend on itself");

        verify(serviceRepository, never()).findById(any(UUID.class));
    }

    @Test
    void createDependency_sourceNotFound() {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        CreateDependencyRequest request = new CreateDependencyRequest(
                sourceId, targetId, DependencyType.HTTP_REST, null, null, null);

        when(serviceRepository.findById(sourceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dependencyGraphService.createDependency(request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("ServiceRegistration")
                .hasMessageContaining(sourceId.toString());
    }

    @Test
    void createDependency_targetNotFound() {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        ServiceRegistration source = buildService(sourceId, "api-gateway", "api-gateway");
        CreateDependencyRequest request = new CreateDependencyRequest(
                sourceId, targetId, DependencyType.HTTP_REST, null, null, null);

        when(serviceRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(serviceRepository.findById(targetId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dependencyGraphService.createDependency(request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("ServiceRegistration")
                .hasMessageContaining(targetId.toString());
    }

    @Test
    void createDependency_differentTeams() {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID otherTeamId = UUID.randomUUID();
        ServiceRegistration source = buildService(sourceId, "api-gateway", "api-gateway");
        ServiceRegistration target = buildService(targetId, "user-service", "user-service", otherTeamId);

        CreateDependencyRequest request = new CreateDependencyRequest(
                sourceId, targetId, DependencyType.HTTP_REST, null, null, null);

        when(serviceRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(serviceRepository.findById(targetId)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> dependencyGraphService.createDependency(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("different teams");
    }

    @Test
    void createDependency_duplicateEdge() {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        ServiceRegistration source = buildService(sourceId, "api-gateway", "api-gateway");
        ServiceRegistration target = buildService(targetId, "user-service", "user-service");
        ServiceDependency existing = buildDependency(UUID.randomUUID(), source, target, DependencyType.HTTP_REST);

        CreateDependencyRequest request = new CreateDependencyRequest(
                sourceId, targetId, DependencyType.HTTP_REST, null, null, null);

        when(serviceRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(serviceRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(dependencyRepository.findBySourceServiceIdAndTargetServiceIdAndDependencyType(
                sourceId, targetId, DependencyType.HTTP_REST)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> dependencyGraphService.createDependency(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Dependency already exists");
    }

    @Test
    void createDependency_dependencyLimitReached() {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        ServiceRegistration source = buildService(sourceId, "api-gateway", "api-gateway");
        ServiceRegistration target = buildService(targetId, "user-service", "user-service");

        CreateDependencyRequest request = new CreateDependencyRequest(
                sourceId, targetId, DependencyType.HTTP_REST, null, null, null);

        when(serviceRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(serviceRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(dependencyRepository.findBySourceServiceIdAndTargetServiceIdAndDependencyType(
                sourceId, targetId, DependencyType.HTTP_REST)).thenReturn(Optional.empty());
        when(dependencyRepository.countBySourceServiceId(sourceId))
                .thenReturn((long) AppConstants.MAX_DEPENDENCIES_PER_SERVICE);

        assertThatThrownBy(() -> dependencyGraphService.createDependency(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("maximum")
                .hasMessageContaining(String.valueOf(AppConstants.MAX_DEPENDENCIES_PER_SERVICE));
    }

    @Test
    void createDependency_wouldCreateCycle() {
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        ServiceRegistration svcA = buildService(idA, "service-a", "service-a");
        ServiceRegistration svcB = buildService(idB, "service-b", "service-b");

        // Existing: A → B
        ServiceDependency existingAB = buildDependency(UUID.randomUUID(), svcA, svcB, DependencyType.HTTP_REST);

        // Attempting: B → A (would create cycle)
        CreateDependencyRequest request = new CreateDependencyRequest(
                idB, idA, DependencyType.HTTP_REST, null, null, null);

        when(serviceRepository.findById(idB)).thenReturn(Optional.of(svcB));
        when(serviceRepository.findById(idA)).thenReturn(Optional.of(svcA));
        when(dependencyRepository.findBySourceServiceIdAndTargetServiceIdAndDependencyType(
                idB, idA, DependencyType.HTTP_REST)).thenReturn(Optional.empty());
        when(dependencyRepository.countBySourceServiceId(idB)).thenReturn(0L);
        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(List.of(existingAB));

        assertThatThrownBy(() -> dependencyGraphService.createDependency(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void createDependency_isRequiredDefaultsToTrue() {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        ServiceRegistration source = buildService(sourceId, "api-gateway", "api-gateway");
        ServiceRegistration target = buildService(targetId, "user-service", "user-service");

        // isRequired is null in the request
        CreateDependencyRequest request = new CreateDependencyRequest(
                sourceId, targetId, DependencyType.HTTP_REST, null, null, null);

        when(serviceRepository.findById(sourceId)).thenReturn(Optional.of(source));
        when(serviceRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(dependencyRepository.findBySourceServiceIdAndTargetServiceIdAndDependencyType(
                sourceId, targetId, DependencyType.HTTP_REST)).thenReturn(Optional.empty());
        when(dependencyRepository.countBySourceServiceId(sourceId)).thenReturn(0L);
        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(Collections.emptyList());
        when(dependencyRepository.save(any(ServiceDependency.class))).thenAnswer(invocation -> {
            ServiceDependency saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(Instant.now());
            return saved;
        });

        ServiceDependencyResponse response = dependencyGraphService.createDependency(request);

        assertThat(response.isRequired()).isTrue();
    }

    // ──────────────────────────────────────────────────────────────
    // removeDependency tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void removeDependency_success() {
        UUID depId = UUID.randomUUID();
        ServiceRegistration source = buildService(UUID.randomUUID(), "api-gateway", "api-gateway");
        ServiceRegistration target = buildService(UUID.randomUUID(), "user-service", "user-service");
        ServiceDependency dep = buildDependency(depId, source, target, DependencyType.HTTP_REST);

        when(dependencyRepository.findById(depId)).thenReturn(Optional.of(dep));

        dependencyGraphService.removeDependency(depId);

        verify(dependencyRepository).delete(dep);
    }

    @Test
    void removeDependency_notFound() {
        UUID depId = UUID.randomUUID();
        when(dependencyRepository.findById(depId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dependencyGraphService.removeDependency(depId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("ServiceDependency")
                .hasMessageContaining(depId.toString());
    }

    // ──────────────────────────────────────────────────────────────
    // getDependencyGraph tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void getDependencyGraph_empty() {
        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(Collections.emptyList());
        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(Collections.emptyList());

        DependencyGraphResponse response = dependencyGraphService.getDependencyGraph(TEAM_ID);

        assertThat(response.teamId()).isEqualTo(TEAM_ID);
        assertThat(response.nodes()).isEmpty();
        assertThat(response.edges()).isEmpty();
    }

    @Test
    void getDependencyGraph_withServicesAndDeps() {
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        UUID idC = UUID.randomUUID();
        ServiceRegistration svcA = buildService(idA, "service-a", "service-a");
        svcA.setLastHealthStatus(HealthStatus.UP);
        ServiceRegistration svcB = buildService(idB, "service-b", "service-b");
        svcB.setLastHealthStatus(HealthStatus.DOWN);
        ServiceRegistration svcC = buildService(idC, "service-c", "service-c");
        // svcC has null lastHealthStatus — should default to UNKNOWN

        ServiceDependency depAB = buildDependency(UUID.randomUUID(), svcA, svcB, DependencyType.HTTP_REST);
        ServiceDependency depAC = buildDependency(UUID.randomUUID(), svcA, svcC, DependencyType.GRPC);

        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(svcA, svcB, svcC));
        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(List.of(depAB, depAC));

        DependencyGraphResponse response = dependencyGraphService.getDependencyGraph(TEAM_ID);

        assertThat(response.teamId()).isEqualTo(TEAM_ID);
        assertThat(response.nodes()).hasSize(3);
        assertThat(response.edges()).hasSize(2);

        // Verify nodes
        DependencyNodeResponse nodeA = response.nodes().stream()
                .filter(n -> n.serviceId().equals(idA)).findFirst().orElseThrow();
        assertThat(nodeA.name()).isEqualTo("service-a");
        assertThat(nodeA.healthStatus()).isEqualTo(HealthStatus.UP);

        DependencyNodeResponse nodeC = response.nodes().stream()
                .filter(n -> n.serviceId().equals(idC)).findFirst().orElseThrow();
        assertThat(nodeC.healthStatus()).isEqualTo(HealthStatus.UNKNOWN);

        // Verify edges
        DependencyEdgeResponse edgeAB = response.edges().stream()
                .filter(e -> e.sourceServiceId().equals(idA) && e.targetServiceId().equals(idB))
                .findFirst().orElseThrow();
        assertThat(edgeAB.dependencyType()).isEqualTo(DependencyType.HTTP_REST);
    }

    // ──────────────────────────────────────────────────────────────
    // getImpactAnalysis tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void getImpactAnalysis_noImpact() {
        UUID leafId = UUID.randomUUID();
        ServiceRegistration leaf = buildService(leafId, "leaf-svc", "leaf-svc");

        when(serviceRepository.findById(leafId)).thenReturn(Optional.of(leaf));
        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(Collections.emptyList());

        ImpactAnalysisResponse response = dependencyGraphService.getImpactAnalysis(leafId);

        assertThat(response.sourceServiceId()).isEqualTo(leafId);
        assertThat(response.sourceServiceName()).isEqualTo("leaf-svc");
        assertThat(response.impactedServices()).isEmpty();
        assertThat(response.totalAffected()).isZero();
    }

    @Test
    void getImpactAnalysis_directImpact() {
        // A depends on B (A → B). Analyzing B — A is directly impacted.
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        ServiceRegistration svcA = buildService(idA, "service-a", "service-a");
        ServiceRegistration svcB = buildService(idB, "service-b", "service-b");

        ServiceDependency depAB = buildDependency(UUID.randomUUID(), svcA, svcB, DependencyType.HTTP_REST);

        when(serviceRepository.findById(idB)).thenReturn(Optional.of(svcB));
        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(List.of(depAB));

        ImpactAnalysisResponse response = dependencyGraphService.getImpactAnalysis(idB);

        assertThat(response.sourceServiceId()).isEqualTo(idB);
        assertThat(response.totalAffected()).isEqualTo(1);
        assertThat(response.impactedServices()).hasSize(1);

        ImpactedServiceResponse impacted = response.impactedServices().get(0);
        assertThat(impacted.serviceId()).isEqualTo(idA);
        assertThat(impacted.serviceName()).isEqualTo("service-a");
        assertThat(impacted.depth()).isEqualTo(1);
        assertThat(impacted.connectionType()).isEqualTo(DependencyType.HTTP_REST);
        assertThat(impacted.isRequired()).isTrue();
    }

    @Test
    void getImpactAnalysis_transitiveImpact() {
        // A → B → C. Analyzing C: B at depth 1, A at depth 2.
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        UUID idC = UUID.randomUUID();
        ServiceRegistration svcA = buildService(idA, "service-a", "service-a");
        ServiceRegistration svcB = buildService(idB, "service-b", "service-b");
        ServiceRegistration svcC = buildService(idC, "service-c", "service-c");

        ServiceDependency depAB = buildDependency(UUID.randomUUID(), svcA, svcB, DependencyType.GRPC);
        ServiceDependency depBC = buildDependency(UUID.randomUUID(), svcB, svcC, DependencyType.HTTP_REST);

        when(serviceRepository.findById(idC)).thenReturn(Optional.of(svcC));
        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(List.of(depAB, depBC));

        ImpactAnalysisResponse response = dependencyGraphService.getImpactAnalysis(idC);

        assertThat(response.totalAffected()).isEqualTo(2);

        ImpactedServiceResponse bImpact = response.impactedServices().stream()
                .filter(i -> i.serviceId().equals(idB)).findFirst().orElseThrow();
        assertThat(bImpact.depth()).isEqualTo(1);

        ImpactedServiceResponse aImpact = response.impactedServices().stream()
                .filter(i -> i.serviceId().equals(idA)).findFirst().orElseThrow();
        assertThat(aImpact.depth()).isEqualTo(2);
    }

    @Test
    void getImpactAnalysis_diamond() {
        // Diamond: A → C, B → C, D → A, D → B. Analyzing C: impacted = A(1), B(1), D(2).
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        UUID idC = UUID.randomUUID();
        UUID idD = UUID.randomUUID();
        ServiceRegistration svcA = buildService(idA, "service-a", "service-a");
        ServiceRegistration svcB = buildService(idB, "service-b", "service-b");
        ServiceRegistration svcC = buildService(idC, "service-c", "service-c");
        ServiceRegistration svcD = buildService(idD, "service-d", "service-d");

        ServiceDependency depAC = buildDependency(UUID.randomUUID(), svcA, svcC, DependencyType.HTTP_REST);
        ServiceDependency depBC = buildDependency(UUID.randomUUID(), svcB, svcC, DependencyType.HTTP_REST);
        ServiceDependency depDA = buildDependency(UUID.randomUUID(), svcD, svcA, DependencyType.HTTP_REST);
        ServiceDependency depDB = buildDependency(UUID.randomUUID(), svcD, svcB, DependencyType.HTTP_REST);

        when(serviceRepository.findById(idC)).thenReturn(Optional.of(svcC));
        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(List.of(depAC, depBC, depDA, depDB));

        ImpactAnalysisResponse response = dependencyGraphService.getImpactAnalysis(idC);

        assertThat(response.totalAffected()).isEqualTo(3);

        ImpactedServiceResponse aImpact = response.impactedServices().stream()
                .filter(i -> i.serviceId().equals(idA)).findFirst().orElseThrow();
        assertThat(aImpact.depth()).isEqualTo(1);

        ImpactedServiceResponse bImpact = response.impactedServices().stream()
                .filter(i -> i.serviceId().equals(idB)).findFirst().orElseThrow();
        assertThat(bImpact.depth()).isEqualTo(1);

        ImpactedServiceResponse dImpact = response.impactedServices().stream()
                .filter(i -> i.serviceId().equals(idD)).findFirst().orElseThrow();
        assertThat(dImpact.depth()).isEqualTo(2);
    }

    @Test
    void getImpactAnalysis_serviceNotFound() {
        UUID serviceId = UUID.randomUUID();
        when(serviceRepository.findById(serviceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dependencyGraphService.getImpactAnalysis(serviceId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("ServiceRegistration")
                .hasMessageContaining(serviceId.toString());
    }

    // ──────────────────────────────────────────────────────────────
    // getStartupOrder tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void getStartupOrder_empty() {
        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(Collections.emptyList());
        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(Collections.emptyList());

        List<DependencyNodeResponse> order = dependencyGraphService.getStartupOrder(TEAM_ID);

        assertThat(order).isEmpty();
    }

    @Test
    void getStartupOrder_noEdges() {
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        UUID idC = UUID.randomUUID();
        ServiceRegistration svcA = buildService(idA, "service-a", "service-a");
        ServiceRegistration svcB = buildService(idB, "service-b", "service-b");
        ServiceRegistration svcC = buildService(idC, "service-c", "service-c");

        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(svcA, svcB, svcC));
        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(Collections.emptyList());

        List<DependencyNodeResponse> order = dependencyGraphService.getStartupOrder(TEAM_ID);

        assertThat(order).hasSize(3);
        // All services should be present (no dependencies, so all have in-degree 0)
        assertThat(order).extracting(DependencyNodeResponse::serviceId)
                .containsExactlyInAnyOrder(idA, idB, idC);
    }

    @Test
    void getStartupOrder_linearChain() {
        // A → B → C: startup order is C, B, A
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        UUID idC = UUID.randomUUID();
        ServiceRegistration svcA = buildService(idA, "service-a", "service-a");
        ServiceRegistration svcB = buildService(idB, "service-b", "service-b");
        ServiceRegistration svcC = buildService(idC, "service-c", "service-c");

        ServiceDependency depAB = buildDependency(UUID.randomUUID(), svcA, svcB, DependencyType.HTTP_REST);
        ServiceDependency depBC = buildDependency(UUID.randomUUID(), svcB, svcC, DependencyType.HTTP_REST);

        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(svcA, svcB, svcC));
        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(List.of(depAB, depBC));

        List<DependencyNodeResponse> order = dependencyGraphService.getStartupOrder(TEAM_ID);

        assertThat(order).hasSize(3);
        // C must come before B, B must come before A
        List<UUID> ids = order.stream().map(DependencyNodeResponse::serviceId).toList();
        assertThat(ids.indexOf(idC)).isLessThan(ids.indexOf(idB));
        assertThat(ids.indexOf(idB)).isLessThan(ids.indexOf(idA));
    }

    @Test
    void getStartupOrder_diamond() {
        // A → B, A → C, B → D, C → D: D first, then B/C (either order), then A
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        UUID idC = UUID.randomUUID();
        UUID idD = UUID.randomUUID();
        ServiceRegistration svcA = buildService(idA, "service-a", "service-a");
        ServiceRegistration svcB = buildService(idB, "service-b", "service-b");
        ServiceRegistration svcC = buildService(idC, "service-c", "service-c");
        ServiceRegistration svcD = buildService(idD, "service-d", "service-d");

        ServiceDependency depAB = buildDependency(UUID.randomUUID(), svcA, svcB, DependencyType.HTTP_REST);
        ServiceDependency depAC = buildDependency(UUID.randomUUID(), svcA, svcC, DependencyType.HTTP_REST);
        ServiceDependency depBD = buildDependency(UUID.randomUUID(), svcB, svcD, DependencyType.HTTP_REST);
        ServiceDependency depCD = buildDependency(UUID.randomUUID(), svcC, svcD, DependencyType.HTTP_REST);

        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(svcA, svcB, svcC, svcD));
        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(List.of(depAB, depAC, depBD, depCD));

        List<DependencyNodeResponse> order = dependencyGraphService.getStartupOrder(TEAM_ID);

        assertThat(order).hasSize(4);
        List<UUID> ids = order.stream().map(DependencyNodeResponse::serviceId).toList();

        // D must be first (no dependencies in startup sense)
        assertThat(ids.get(0)).isEqualTo(idD);
        // B and C must come before A
        assertThat(ids.indexOf(idB)).isLessThan(ids.indexOf(idA));
        assertThat(ids.indexOf(idC)).isLessThan(ids.indexOf(idA));
        // A must be last
        assertThat(ids.get(3)).isEqualTo(idA);
    }

    @Test
    void getStartupOrder_multipleRoots() {
        // Two independent chains: A → B, C → D
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        UUID idC = UUID.randomUUID();
        UUID idD = UUID.randomUUID();
        ServiceRegistration svcA = buildService(idA, "service-a", "service-a");
        ServiceRegistration svcB = buildService(idB, "service-b", "service-b");
        ServiceRegistration svcC = buildService(idC, "service-c", "service-c");
        ServiceRegistration svcD = buildService(idD, "service-d", "service-d");

        ServiceDependency depAB = buildDependency(UUID.randomUUID(), svcA, svcB, DependencyType.HTTP_REST);
        ServiceDependency depCD = buildDependency(UUID.randomUUID(), svcC, svcD, DependencyType.HTTP_REST);

        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(svcA, svcB, svcC, svcD));
        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(List.of(depAB, depCD));

        List<DependencyNodeResponse> order = dependencyGraphService.getStartupOrder(TEAM_ID);

        assertThat(order).hasSize(4);
        List<UUID> ids = order.stream().map(DependencyNodeResponse::serviceId).toList();

        // B must come before A, D must come before C
        assertThat(ids.indexOf(idB)).isLessThan(ids.indexOf(idA));
        assertThat(ids.indexOf(idD)).isLessThan(ids.indexOf(idC));
    }

    // ──────────────────────────────────────────────────────────────
    // detectCycles tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void detectCycles_noCycles() {
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        ServiceRegistration svcA = buildService(idA, "service-a", "service-a");
        ServiceRegistration svcB = buildService(idB, "service-b", "service-b");

        ServiceDependency depAB = buildDependency(UUID.randomUUID(), svcA, svcB, DependencyType.HTTP_REST);

        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(List.of(depAB));
        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(svcA, svcB));

        List<UUID> cycleNodes = dependencyGraphService.detectCycles(TEAM_ID);

        assertThat(cycleNodes).isEmpty();
    }

    @Test
    void detectCycles_simpleCycle() {
        // A → B and B → A (manually built — should not happen with cycle prevention, but testing detection)
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        ServiceRegistration svcA = buildService(idA, "service-a", "service-a");
        ServiceRegistration svcB = buildService(idB, "service-b", "service-b");

        ServiceDependency depAB = buildDependency(UUID.randomUUID(), svcA, svcB, DependencyType.HTTP_REST);
        ServiceDependency depBA = buildDependency(UUID.randomUUID(), svcB, svcA, DependencyType.HTTP_REST);

        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(List.of(depAB, depBA));
        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(svcA, svcB));

        List<UUID> cycleNodes = dependencyGraphService.detectCycles(TEAM_ID);

        assertThat(cycleNodes).containsExactlyInAnyOrder(idA, idB);
    }

    @Test
    void detectCycles_noCycleInDAG() {
        // A → B, A → C, B → D, C → D — diamond, no cycle
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        UUID idC = UUID.randomUUID();
        UUID idD = UUID.randomUUID();
        ServiceRegistration svcA = buildService(idA, "service-a", "service-a");
        ServiceRegistration svcB = buildService(idB, "service-b", "service-b");
        ServiceRegistration svcC = buildService(idC, "service-c", "service-c");
        ServiceRegistration svcD = buildService(idD, "service-d", "service-d");

        ServiceDependency depAB = buildDependency(UUID.randomUUID(), svcA, svcB, DependencyType.HTTP_REST);
        ServiceDependency depAC = buildDependency(UUID.randomUUID(), svcA, svcC, DependencyType.HTTP_REST);
        ServiceDependency depBD = buildDependency(UUID.randomUUID(), svcB, svcD, DependencyType.HTTP_REST);
        ServiceDependency depCD = buildDependency(UUID.randomUUID(), svcC, svcD, DependencyType.HTTP_REST);

        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(List.of(depAB, depAC, depBD, depCD));
        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(svcA, svcB, svcC, svcD));

        List<UUID> cycleNodes = dependencyGraphService.detectCycles(TEAM_ID);

        assertThat(cycleNodes).isEmpty();
    }

    @Test
    void detectCycles_complexCycle() {
        // A → B → C → A
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        UUID idC = UUID.randomUUID();
        ServiceRegistration svcA = buildService(idA, "service-a", "service-a");
        ServiceRegistration svcB = buildService(idB, "service-b", "service-b");
        ServiceRegistration svcC = buildService(idC, "service-c", "service-c");

        ServiceDependency depAB = buildDependency(UUID.randomUUID(), svcA, svcB, DependencyType.HTTP_REST);
        ServiceDependency depBC = buildDependency(UUID.randomUUID(), svcB, svcC, DependencyType.HTTP_REST);
        ServiceDependency depCA = buildDependency(UUID.randomUUID(), svcC, svcA, DependencyType.HTTP_REST);

        when(dependencyRepository.findAllByTeamId(TEAM_ID)).thenReturn(List.of(depAB, depBC, depCA));
        when(serviceRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(svcA, svcB, svcC));

        List<UUID> cycleNodes = dependencyGraphService.detectCycles(TEAM_ID);

        assertThat(cycleNodes).containsExactlyInAnyOrder(idA, idB, idC);
    }

    // ──────────────────────────────────────────────────────────────
    // hasPath tests (package-private method)
    // ──────────────────────────────────────────────────────────────

    @Test
    void hasPath_directPath() {
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        ServiceRegistration svcA = buildService(idA, "service-a", "service-a");
        ServiceRegistration svcB = buildService(idB, "service-b", "service-b");

        ServiceDependency depAB = buildDependency(UUID.randomUUID(), svcA, svcB, DependencyType.HTTP_REST);

        boolean result = dependencyGraphService.hasPath(idA, idB, List.of(depAB));

        assertThat(result).isTrue();
    }

    @Test
    void hasPath_transitivePath() {
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        UUID idC = UUID.randomUUID();
        ServiceRegistration svcA = buildService(idA, "service-a", "service-a");
        ServiceRegistration svcB = buildService(idB, "service-b", "service-b");
        ServiceRegistration svcC = buildService(idC, "service-c", "service-c");

        ServiceDependency depAB = buildDependency(UUID.randomUUID(), svcA, svcB, DependencyType.HTTP_REST);
        ServiceDependency depBC = buildDependency(UUID.randomUUID(), svcB, svcC, DependencyType.HTTP_REST);

        boolean result = dependencyGraphService.hasPath(idA, idC, List.of(depAB, depBC));

        assertThat(result).isTrue();
    }

    @Test
    void hasPath_noPath() {
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        UUID idC = UUID.randomUUID();
        UUID idD = UUID.randomUUID();
        ServiceRegistration svcA = buildService(idA, "service-a", "service-a");
        ServiceRegistration svcB = buildService(idB, "service-b", "service-b");
        ServiceRegistration svcC = buildService(idC, "service-c", "service-c");
        ServiceRegistration svcD = buildService(idD, "service-d", "service-d");

        // Two disconnected edges: A → B, C → D
        ServiceDependency depAB = buildDependency(UUID.randomUUID(), svcA, svcB, DependencyType.HTTP_REST);
        ServiceDependency depCD = buildDependency(UUID.randomUUID(), svcC, svcD, DependencyType.HTTP_REST);

        boolean result = dependencyGraphService.hasPath(idA, idD, List.of(depAB, depCD));

        assertThat(result).isFalse();
    }

    @Test
    void hasPath_emptyGraph() {
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();

        boolean result = dependencyGraphService.hasPath(idA, idB, Collections.emptyList());

        assertThat(result).isFalse();
    }

    @Test
    void hasPath_sameNode() {
        UUID idA = UUID.randomUUID();

        boolean result = dependencyGraphService.hasPath(idA, idA, Collections.emptyList());

        assertThat(result).isTrue();
    }
}
