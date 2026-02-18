package com.codeops.registry.config;

import com.codeops.registry.entity.*;
import com.codeops.registry.entity.enums.*;
import com.codeops.registry.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    @Mock
    private ServiceRegistrationRepository serviceRegistrationRepository;
    @Mock
    private ServiceDependencyRepository serviceDependencyRepository;
    @Mock
    private SolutionRepository solutionRepository;
    @Mock
    private SolutionMemberRepository solutionMemberRepository;
    @Mock
    private PortAllocationRepository portAllocationRepository;
    @Mock
    private PortRangeRepository portRangeRepository;
    @Mock
    private ApiRouteRegistrationRepository apiRouteRegistrationRepository;
    @Mock
    private EnvironmentConfigRepository environmentConfigRepository;
    @Mock
    private WorkstationProfileRepository workstationProfileRepository;
    @Mock
    private InfraResourceRepository infraResourceRepository;

    @InjectMocks
    private DataSeeder dataSeeder;

    @Captor
    private ArgumentCaptor<List<ServiceRegistration>> servicesCaptor;
    @Captor
    private ArgumentCaptor<List<ServiceDependency>> depsCaptor;
    @Captor
    private ArgumentCaptor<List<Solution>> solutionsCaptor;
    @Captor
    private ArgumentCaptor<List<SolutionMember>> membersCaptor;
    @Captor
    private ArgumentCaptor<List<PortAllocation>> portsCaptor;
    @Captor
    private ArgumentCaptor<List<PortRange>> rangesCaptor;
    @Captor
    private ArgumentCaptor<List<ApiRouteRegistration>> routesCaptor;
    @Captor
    private ArgumentCaptor<List<EnvironmentConfig>> configsCaptor;
    @Captor
    private ArgumentCaptor<List<WorkstationProfile>> profilesCaptor;
    @Captor
    private ArgumentCaptor<List<InfraResource>> infraCaptor;

    /**
     * Configures repository mocks to simulate JPA save behavior with ID generation.
     * Called by all tests that exercise the full seeding path.
     */
    private void configureSaveAllMocks() {
        when(serviceRegistrationRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<ServiceRegistration> list = invocation.getArgument(0);
            list.forEach(s -> {
                if (s.getId() == null) {
                    s.setId(UUID.randomUUID());
                }
            });
            return list;
        });
        when(solutionRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<Solution> list = invocation.getArgument(0);
            list.forEach(s -> {
                if (s.getId() == null) {
                    s.setId(UUID.randomUUID());
                }
            });
            return list;
        });
        when(serviceDependencyRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        when(solutionMemberRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        when(portAllocationRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        when(portRangeRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        when(apiRouteRegistrationRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        when(environmentConfigRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        when(workstationProfileRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
        when(infraResourceRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
    }

    private void runSeeder() throws Exception {
        when(serviceRegistrationRepository.count()).thenReturn(0L);
        configureSaveAllMocks();
        dataSeeder.run();
    }

    // ──────────────────────────────────────────────
    // Idempotency
    // ──────────────────────────────────────────────

    @Test
    void run_dataAlreadyExists_skipsSeeding() throws Exception {
        when(serviceRegistrationRepository.count()).thenReturn(5L);

        dataSeeder.run();

        verify(serviceRegistrationRepository, never()).saveAll(anyList());
        verify(solutionRepository, never()).saveAll(anyList());
    }

    @Test
    void run_emptyDatabase_seedsAllEntities() throws Exception {
        runSeeder();

        verify(serviceRegistrationRepository).saveAll(anyList());
        verify(serviceDependencyRepository).saveAll(anyList());
        verify(solutionRepository).saveAll(anyList());
        verify(solutionMemberRepository).saveAll(anyList());
        verify(portAllocationRepository).saveAll(anyList());
        verify(portRangeRepository).saveAll(anyList());
        verify(apiRouteRegistrationRepository).saveAll(anyList());
        verify(environmentConfigRepository).saveAll(anyList());
        verify(workstationProfileRepository).saveAll(anyList());
        verify(infraResourceRepository).saveAll(anyList());
    }

    // ──────────────────────────────────────────────
    // Service count and structure
    // ──────────────────────────────────────────────

    @Test
    void run_seeds9Services() throws Exception {
        runSeeder();

        verify(serviceRegistrationRepository).saveAll(servicesCaptor.capture());
        List<ServiceRegistration> services = servicesCaptor.getValue();

        assertThat(services).hasSize(9);
        assertThat(services).extracting(ServiceRegistration::getTeamId)
                .containsOnly(AppConstants.SEED_TEAM_ID);
    }

    @Test
    void run_seeds3Solutions() throws Exception {
        runSeeder();

        verify(solutionRepository).saveAll(solutionsCaptor.capture());
        List<Solution> solutions = solutionsCaptor.getValue();

        assertThat(solutions).hasSize(3);
        assertThat(solutions).extracting(Solution::getCategory)
                .containsExactly(SolutionCategory.PLATFORM, SolutionCategory.INFRASTRUCTURE, SolutionCategory.TOOLING);
    }

    @Test
    void run_seeds3WorkstationProfiles() throws Exception {
        runSeeder();

        verify(workstationProfileRepository).saveAll(profilesCaptor.capture());
        List<WorkstationProfile> profiles = profilesCaptor.getValue();

        assertThat(profiles).hasSize(3);
        assertThat(profiles).extracting(WorkstationProfile::getName)
                .containsExactly("Full Platform", "Backend Only", "Registry Dev");
    }

    @Test
    void run_defaultWorkstationProfileExists() throws Exception {
        runSeeder();

        verify(workstationProfileRepository).saveAll(profilesCaptor.capture());
        List<WorkstationProfile> profiles = profilesCaptor.getValue();

        long defaultCount = profiles.stream().filter(WorkstationProfile::getIsDefault).count();
        assertThat(defaultCount).isEqualTo(1);
        assertThat(profiles.get(0).getIsDefault()).isTrue();
        assertThat(profiles.get(0).getName()).isEqualTo("Full Platform");
    }

    // ──────────────────────────────────────────────
    // Data integrity
    // ──────────────────────────────────────────────

    @Test
    void run_seeds13Dependencies() throws Exception {
        runSeeder();

        verify(serviceDependencyRepository).saveAll(depsCaptor.capture());
        List<ServiceDependency> deps = depsCaptor.getValue();

        assertThat(deps).hasSize(13);
        assertThat(deps).allSatisfy(dep -> {
            assertThat(dep.getSourceService()).isNotNull();
            assertThat(dep.getTargetService()).isNotNull();
            assertThat(dep.getIsRequired()).isTrue();
        });
    }

    @Test
    void run_allDependenciesReferenceValidServices() throws Exception {
        runSeeder();

        verify(serviceRegistrationRepository).saveAll(servicesCaptor.capture());
        verify(serviceDependencyRepository).saveAll(depsCaptor.capture());

        Set<UUID> serviceIds = servicesCaptor.getValue().stream()
                .map(ServiceRegistration::getId)
                .collect(Collectors.toSet());
        List<ServiceDependency> deps = depsCaptor.getValue();

        deps.forEach(dep -> {
            assertThat(serviceIds).contains(dep.getSourceService().getId());
            assertThat(serviceIds).contains(dep.getTargetService().getId());
        });
    }

    @Test
    void run_portAllocationsDoNotConflict() throws Exception {
        runSeeder();

        verify(portAllocationRepository).saveAll(portsCaptor.capture());
        List<PortAllocation> ports = portsCaptor.getValue();

        assertThat(ports).isNotEmpty();
        ports.forEach(p -> assertThat(p.getPortNumber()).isPositive());
    }

    @Test
    void run_allServicesHaveHealthCheckUrlsExceptScribe() throws Exception {
        runSeeder();

        verify(serviceRegistrationRepository).saveAll(servicesCaptor.capture());
        List<ServiceRegistration> services = servicesCaptor.getValue();

        services.forEach(s -> {
            if ("codeops-scribe".equals(s.getSlug())) {
                assertThat(s.getHealthCheckUrl()).isNull();
            } else {
                assertThat(s.getHealthCheckUrl()).isNotNull();
            }
        });
    }

    @Test
    void run_seeds3PortRanges() throws Exception {
        runSeeder();

        verify(portRangeRepository).saveAll(rangesCaptor.capture());
        List<PortRange> ranges = rangesCaptor.getValue();

        assertThat(ranges).hasSize(3);
        assertThat(ranges).extracting(PortRange::getPortType)
                .containsExactlyInAnyOrder(PortType.HTTP_API, PortType.FRONTEND_DEV, PortType.DATABASE);
    }

    @Test
    void run_seeds6ApiRoutes() throws Exception {
        runSeeder();

        verify(apiRouteRegistrationRepository).saveAll(routesCaptor.capture());
        List<ApiRouteRegistration> routes = routesCaptor.getValue();

        assertThat(routes).hasSize(6);
        assertThat(routes).allSatisfy(r -> {
            assertThat(r.getRoutePrefix()).startsWith("/api/v1/");
            assertThat(r.getEnvironment()).isEqualTo("local");
        });
    }

    @Test
    void run_seeds2InfraResources() throws Exception {
        runSeeder();

        verify(infraResourceRepository).saveAll(infraCaptor.capture());
        List<InfraResource> resources = infraCaptor.getValue();

        assertThat(resources).hasSize(2);
        assertThat(resources).extracting(InfraResource::getResourceType)
                .containsExactly(InfraResourceType.DOCKER_NETWORK, InfraResourceType.DOCKER_VOLUME);
    }
}
