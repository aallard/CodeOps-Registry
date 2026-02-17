package com.codeops.registry.service;

import com.codeops.registry.config.AppConstants;
import com.codeops.registry.dto.request.CloneServiceRequest;
import com.codeops.registry.dto.request.CreateServiceRequest;
import com.codeops.registry.dto.request.UpdateServiceRequest;
import com.codeops.registry.dto.response.*;
import com.codeops.registry.entity.*;
import com.codeops.registry.entity.enums.*;
import com.codeops.registry.exception.NotFoundException;
import com.codeops.registry.exception.ValidationException;
import com.codeops.registry.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ServiceRegistryService}.
 */
@ExtendWith(MockitoExtension.class)
class ServiceRegistryServiceTest {

    @Mock
    private ServiceRegistrationRepository serviceRepository;

    @Mock
    private PortAllocationRepository portAllocationRepository;

    @Mock
    private ServiceDependencyRepository dependencyRepository;

    @Mock
    private SolutionMemberRepository solutionMemberRepository;

    @Mock
    private ApiRouteRegistrationRepository routeRepository;

    @Mock
    private InfraResourceRepository infraRepository;

    @Mock
    private EnvironmentConfigRepository envConfigRepository;

    @Mock
    private PortAllocationService portAllocationService;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private ServiceRegistryService serviceRegistryService;

    // ──────────────────────────────────────────────
    // Helper methods
    // ──────────────────────────────────────────────

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SERVICE_ID = UUID.randomUUID();

    private ServiceRegistration buildServiceEntity(UUID id, String name, String slug) {
        ServiceRegistration entity = ServiceRegistration.builder()
                .teamId(TEAM_ID)
                .name(name)
                .slug(slug)
                .serviceType(ServiceType.SPRING_BOOT_API)
                .description("A test service")
                .repoUrl("https://github.com/test/" + slug)
                .repoFullName("test/" + slug)
                .defaultBranch("main")
                .techStack("Java 25, Spring Boot 3.3")
                .status(ServiceStatus.ACTIVE)
                .healthCheckUrl("http://localhost:8080/health")
                .healthCheckIntervalSeconds(30)
                .environmentsJson("{\"local\": true}")
                .metadataJson("{\"version\": \"1.0\"}")
                .createdByUserId(USER_ID)
                .build();
        entity.setId(id);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private ServiceRegistration buildServiceEntity() {
        return buildServiceEntity(SERVICE_ID, "Test Service", "test-service");
    }

    private CreateServiceRequest buildCreateRequest(String name, String slug, List<PortType> portTypes) {
        return new CreateServiceRequest(
                TEAM_ID, name, slug, ServiceType.SPRING_BOOT_API,
                "A test service", "https://github.com/test/repo",
                "test/repo", "main", "Java 25",
                "http://localhost:8080/health", 30,
                "{\"local\": true}", "{\"version\": \"1.0\"}",
                portTypes, "local"
        );
    }

    private void stubCountsForEntity(UUID serviceId) {
        lenient().when(portAllocationRepository.countByServiceId(serviceId)).thenReturn(2L);
        lenient().when(dependencyRepository.countBySourceServiceId(serviceId)).thenReturn(1L);
        lenient().when(solutionMemberRepository.countByServiceId(serviceId)).thenReturn(0L);
    }

    private PortAllocation buildPortAllocation(UUID serviceId, ServiceRegistration service,
                                                PortType portType, int portNumber, String environment) {
        PortAllocation pa = PortAllocation.builder()
                .service(service)
                .environment(environment)
                .portType(portType)
                .portNumber(portNumber)
                .protocol("TCP")
                .isAutoAllocated(true)
                .allocatedByUserId(USER_ID)
                .build();
        pa.setId(UUID.randomUUID());
        pa.setCreatedAt(Instant.now());
        pa.setUpdatedAt(Instant.now());
        return pa;
    }

    private ServiceDependency buildDependency(ServiceRegistration source, ServiceRegistration target,
                                               boolean isRequired) {
        ServiceDependency dep = ServiceDependency.builder()
                .sourceService(source)
                .targetService(target)
                .dependencyType(DependencyType.HTTP_REST)
                .description("Depends on target")
                .isRequired(isRequired)
                .targetEndpoint("/api/v1/target")
                .build();
        dep.setId(UUID.randomUUID());
        dep.setCreatedAt(Instant.now());
        dep.setUpdatedAt(Instant.now());
        return dep;
    }

    private SolutionMember buildSolutionMember(ServiceRegistration service, String solutionName) {
        Solution solution = Solution.builder()
                .teamId(TEAM_ID)
                .name(solutionName)
                .slug(solutionName.toLowerCase().replace(' ', '-'))
                .category(SolutionCategory.PLATFORM)
                .status(SolutionStatus.ACTIVE)
                .createdByUserId(USER_ID)
                .build();
        solution.setId(UUID.randomUUID());

        SolutionMember member = SolutionMember.builder()
                .solution(solution)
                .service(service)
                .role(SolutionMemberRole.CORE)
                .displayOrder(0)
                .build();
        member.setId(UUID.randomUUID());
        member.setCreatedAt(Instant.now());
        member.setUpdatedAt(Instant.now());
        return member;
    }

    private ApiRouteRegistration buildRoute(ServiceRegistration service) {
        ApiRouteRegistration route = ApiRouteRegistration.builder()
                .service(service)
                .gatewayService(null)
                .routePrefix("/api/v1/test")
                .httpMethods("GET,POST")
                .environment("local")
                .description("Test route")
                .build();
        route.setId(UUID.randomUUID());
        route.setCreatedAt(Instant.now());
        route.setUpdatedAt(Instant.now());
        return route;
    }

    private InfraResource buildInfraResource(ServiceRegistration service) {
        InfraResource infra = InfraResource.builder()
                .teamId(TEAM_ID)
                .service(service)
                .resourceType(InfraResourceType.S3_BUCKET)
                .resourceName("test-bucket")
                .environment("local")
                .region("us-east-1")
                .arnOrUrl("arn:aws:s3:::test-bucket")
                .description("Test bucket")
                .createdByUserId(USER_ID)
                .build();
        infra.setId(UUID.randomUUID());
        infra.setCreatedAt(Instant.now());
        infra.setUpdatedAt(Instant.now());
        return infra;
    }

    private EnvironmentConfig buildEnvironmentConfig(ServiceRegistration service, String environment) {
        EnvironmentConfig config = EnvironmentConfig.builder()
                .service(service)
                .environment(environment)
                .configKey("server.port")
                .configValue("8080")
                .configSource(ConfigSource.AUTO_GENERATED)
                .description("Server port config")
                .build();
        config.setId(UUID.randomUUID());
        config.setCreatedAt(Instant.now());
        config.setUpdatedAt(Instant.now());
        return config;
    }

    private PortAllocationResponse buildPortAllocationResponse(UUID serviceId) {
        return new PortAllocationResponse(
                UUID.randomUUID(), serviceId, "Test Service", "test-service",
                "local", PortType.HTTP_API, 8080, "TCP",
                null, true, USER_ID, Instant.now()
        );
    }

    // ──────────────────────────────────────────────
    // createService tests
    // ──────────────────────────────────────────────

    @Test
    void createService_generatesSlugFromName_whenSlugIsNull() {
        CreateServiceRequest request = buildCreateRequest("My New Service", null, null);
        when(serviceRepository.countByTeamId(TEAM_ID)).thenReturn(0L);
        when(serviceRepository.existsByTeamIdAndSlug(eq(TEAM_ID), anyString())).thenReturn(false);
        when(serviceRepository.save(any(ServiceRegistration.class)))
                .thenAnswer(invocation -> {
                    ServiceRegistration saved = invocation.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    saved.setCreatedAt(Instant.now());
                    saved.setUpdatedAt(Instant.now());
                    return saved;
                });
        stubCountsForEntity(any());

        ServiceRegistrationResponse response = serviceRegistryService.createService(request, USER_ID);

        assertThat(response.slug()).isEqualTo("my-new-service");
        assertThat(response.name()).isEqualTo("My New Service");
        verify(serviceRepository).save(any(ServiceRegistration.class));
    }

    @Test
    void createService_usesProvidedSlug_whenSlugIsGiven() {
        CreateServiceRequest request = buildCreateRequest("My Service", "custom-slug", null);
        when(serviceRepository.countByTeamId(TEAM_ID)).thenReturn(0L);
        when(serviceRepository.existsByTeamIdAndSlug(eq(TEAM_ID), anyString())).thenReturn(false);
        when(serviceRepository.save(any(ServiceRegistration.class)))
                .thenAnswer(invocation -> {
                    ServiceRegistration saved = invocation.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    saved.setCreatedAt(Instant.now());
                    saved.setUpdatedAt(Instant.now());
                    return saved;
                });
        stubCountsForEntity(any());

        ServiceRegistrationResponse response = serviceRegistryService.createService(request, USER_ID);

        assertThat(response.slug()).isEqualTo("custom-slug");
    }

    @Test
    void createService_appendsSuffix_whenSlugConflicts() {
        CreateServiceRequest request = buildCreateRequest("My Service", null, null);
        when(serviceRepository.countByTeamId(TEAM_ID)).thenReturn(0L);
        when(serviceRepository.existsByTeamIdAndSlug(TEAM_ID, "my-service")).thenReturn(true);
        when(serviceRepository.existsByTeamIdAndSlug(TEAM_ID, "my-service-2")).thenReturn(false);
        when(serviceRepository.save(any(ServiceRegistration.class)))
                .thenAnswer(invocation -> {
                    ServiceRegistration saved = invocation.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    saved.setCreatedAt(Instant.now());
                    saved.setUpdatedAt(Instant.now());
                    return saved;
                });
        stubCountsForEntity(any());

        ServiceRegistrationResponse response = serviceRegistryService.createService(request, USER_ID);

        assertThat(response.slug()).isEqualTo("my-service-2");
    }

    @Test
    void createService_throwsValidationException_whenTeamAtMaxServices() {
        CreateServiceRequest request = buildCreateRequest("New Service", null, null);
        when(serviceRepository.countByTeamId(TEAM_ID)).thenReturn((long) AppConstants.MAX_SERVICES_PER_TEAM);

        assertThatThrownBy(() -> serviceRegistryService.createService(request, USER_ID))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("maximum of " + AppConstants.MAX_SERVICES_PER_TEAM);
    }

    @Test
    void createService_callsAutoAllocateAll_whenPortTypesProvided() {
        List<PortType> portTypes = List.of(PortType.HTTP_API, PortType.DATABASE);
        CreateServiceRequest request = buildCreateRequest("Port Service", null, portTypes);
        when(serviceRepository.countByTeamId(TEAM_ID)).thenReturn(0L);
        when(serviceRepository.existsByTeamIdAndSlug(eq(TEAM_ID), anyString())).thenReturn(false);
        when(serviceRepository.save(any(ServiceRegistration.class)))
                .thenAnswer(invocation -> {
                    ServiceRegistration saved = invocation.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    saved.setCreatedAt(Instant.now());
                    saved.setUpdatedAt(Instant.now());
                    return saved;
                });
        stubCountsForEntity(any());
        when(portAllocationService.autoAllocateAll(any(UUID.class), eq("local"), eq(portTypes), eq(USER_ID)))
                .thenReturn(List.of());

        serviceRegistryService.createService(request, USER_ID);

        verify(portAllocationService).autoAllocateAll(any(UUID.class), eq("local"), eq(portTypes), eq(USER_ID));
    }

    // ──────────────────────────────────────────────
    // getService tests
    // ──────────────────────────────────────────────

    @Test
    void getService_returnsResponseWithCounts_whenFound() {
        ServiceRegistration entity = buildServiceEntity();
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(entity));
        when(portAllocationRepository.countByServiceId(SERVICE_ID)).thenReturn(3L);
        when(dependencyRepository.countBySourceServiceId(SERVICE_ID)).thenReturn(2L);
        when(solutionMemberRepository.countByServiceId(SERVICE_ID)).thenReturn(1L);

        ServiceRegistrationResponse response = serviceRegistryService.getService(SERVICE_ID);

        assertThat(response.id()).isEqualTo(SERVICE_ID);
        assertThat(response.name()).isEqualTo("Test Service");
        assertThat(response.portCount()).isEqualTo(3);
        assertThat(response.dependencyCount()).isEqualTo(2);
        assertThat(response.solutionCount()).isEqualTo(1);
    }

    @Test
    void getService_throwsNotFoundException_whenNotFound() {
        UUID missingId = UUID.randomUUID();
        when(serviceRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceRegistryService.getService(missingId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(missingId.toString());
    }

    // ──────────────────────────────────────────────
    // getServiceBySlug tests
    // ──────────────────────────────────────────────

    @Test
    void getServiceBySlug_returnsResponse_whenFound() {
        ServiceRegistration entity = buildServiceEntity();
        when(serviceRepository.findByTeamIdAndSlug(TEAM_ID, "test-service")).thenReturn(Optional.of(entity));
        stubCountsForEntity(SERVICE_ID);

        ServiceRegistrationResponse response = serviceRegistryService.getServiceBySlug(TEAM_ID, "test-service");

        assertThat(response.slug()).isEqualTo("test-service");
        assertThat(response.teamId()).isEqualTo(TEAM_ID);
    }

    @Test
    void getServiceBySlug_throwsNotFoundException_whenNotFound() {
        when(serviceRepository.findByTeamIdAndSlug(TEAM_ID, "missing-slug")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceRegistryService.getServiceBySlug(TEAM_ID, "missing-slug"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("missing-slug");
    }

    // ──────────────────────────────────────────────
    // getServicesForTeam tests
    // ──────────────────────────────────────────────

    @Test
    void getServicesForTeam_noFilters_usesFindByTeamId() {
        Pageable pageable = PageRequest.of(0, 20);
        ServiceRegistration entity = buildServiceEntity();
        Page<ServiceRegistration> page = new PageImpl<>(List.of(entity), pageable, 1);
        when(serviceRepository.findByTeamId(TEAM_ID, pageable)).thenReturn(page);

        PageResponse<ServiceRegistrationResponse> response =
                serviceRegistryService.getServicesForTeam(TEAM_ID, null, null, null, pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.totalElements()).isEqualTo(1);
        verify(serviceRepository).findByTeamId(TEAM_ID, pageable);
    }

    @Test
    void getServicesForTeam_statusFilterOnly_usesFindByTeamIdAndStatus() {
        Pageable pageable = PageRequest.of(0, 20);
        ServiceRegistration entity = buildServiceEntity();
        Page<ServiceRegistration> page = new PageImpl<>(List.of(entity), pageable, 1);
        when(serviceRepository.findByTeamIdAndStatus(TEAM_ID, ServiceStatus.ACTIVE, pageable)).thenReturn(page);

        PageResponse<ServiceRegistrationResponse> response =
                serviceRegistryService.getServicesForTeam(TEAM_ID, ServiceStatus.ACTIVE, null, null, pageable);

        assertThat(response.content()).hasSize(1);
        verify(serviceRepository).findByTeamIdAndStatus(TEAM_ID, ServiceStatus.ACTIVE, pageable);
    }

    @Test
    void getServicesForTeam_typeFilterOnly_usesFindByTeamIdAndServiceType() {
        Pageable pageable = PageRequest.of(0, 20);
        ServiceRegistration entity = buildServiceEntity();
        Page<ServiceRegistration> page = new PageImpl<>(List.of(entity), pageable, 1);
        when(serviceRepository.findByTeamIdAndServiceType(TEAM_ID, ServiceType.SPRING_BOOT_API, pageable))
                .thenReturn(page);

        PageResponse<ServiceRegistrationResponse> response =
                serviceRegistryService.getServicesForTeam(TEAM_ID, null, ServiceType.SPRING_BOOT_API, null, pageable);

        assertThat(response.content()).hasSize(1);
        verify(serviceRepository).findByTeamIdAndServiceType(TEAM_ID, ServiceType.SPRING_BOOT_API, pageable);
    }

    @Test
    void getServicesForTeam_statusAndTypeFilters_usesFindByTeamIdAndStatusAndServiceType() {
        Pageable pageable = PageRequest.of(0, 20);
        ServiceRegistration entity = buildServiceEntity();
        Page<ServiceRegistration> page = new PageImpl<>(List.of(entity), pageable, 1);
        when(serviceRepository.findByTeamIdAndStatusAndServiceType(
                TEAM_ID, ServiceStatus.ACTIVE, ServiceType.SPRING_BOOT_API, pageable)).thenReturn(page);

        PageResponse<ServiceRegistrationResponse> response =
                serviceRegistryService.getServicesForTeam(
                        TEAM_ID, ServiceStatus.ACTIVE, ServiceType.SPRING_BOOT_API, null, pageable);

        assertThat(response.content()).hasSize(1);
        verify(serviceRepository).findByTeamIdAndStatusAndServiceType(
                TEAM_ID, ServiceStatus.ACTIVE, ServiceType.SPRING_BOOT_API, pageable);
    }

    @Test
    void getServicesForTeam_searchFilter_usesFindByNameContaining() {
        Pageable pageable = PageRequest.of(0, 20);
        ServiceRegistration entity = buildServiceEntity();
        Page<ServiceRegistration> page = new PageImpl<>(List.of(entity), pageable, 1);
        when(serviceRepository.findByTeamIdAndNameContainingIgnoreCase(TEAM_ID, "Test", pageable))
                .thenReturn(page);

        PageResponse<ServiceRegistrationResponse> response =
                serviceRegistryService.getServicesForTeam(TEAM_ID, null, null, "Test", pageable);

        assertThat(response.content()).hasSize(1);
        verify(serviceRepository).findByTeamIdAndNameContainingIgnoreCase(TEAM_ID, "Test", pageable);
    }

    @Test
    void getServicesForTeam_returnsPageResponse_withCorrectPagination() {
        Pageable pageable = PageRequest.of(0, 10);
        ServiceRegistration e1 = buildServiceEntity(UUID.randomUUID(), "Service A", "service-a");
        ServiceRegistration e2 = buildServiceEntity(UUID.randomUUID(), "Service B", "service-b");
        Page<ServiceRegistration> page = new PageImpl<>(List.of(e1, e2), pageable, 2);
        when(serviceRepository.findByTeamId(TEAM_ID, pageable)).thenReturn(page);

        PageResponse<ServiceRegistrationResponse> response =
                serviceRegistryService.getServicesForTeam(TEAM_ID, null, null, null, pageable);

        assertThat(response.content()).hasSize(2);
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.isLast()).isTrue();
    }

    // ──────────────────────────────────────────────
    // updateService tests
    // ──────────────────────────────────────────────

    @Test
    void updateService_appliesOnlyNonNullFields() {
        ServiceRegistration entity = buildServiceEntity();
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(entity));
        when(serviceRepository.save(any(ServiceRegistration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        stubCountsForEntity(SERVICE_ID);

        UpdateServiceRequest request = new UpdateServiceRequest(
                "Updated Name", null, null, null, null, null, null, null, null, null
        );

        ServiceRegistrationResponse response = serviceRegistryService.updateService(SERVICE_ID, request);

        assertThat(response.name()).isEqualTo("Updated Name");
        assertThat(response.description()).isEqualTo("A test service");
    }

    @Test
    void updateService_throwsNotFoundException_whenNotFound() {
        UUID missingId = UUID.randomUUID();
        when(serviceRepository.findById(missingId)).thenReturn(Optional.empty());

        UpdateServiceRequest request = new UpdateServiceRequest(
                "Updated", null, null, null, null, null, null, null, null, null
        );

        assertThatThrownBy(() -> serviceRegistryService.updateService(missingId, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(missingId.toString());
    }

    // ──────────────────────────────────────────────
    // updateServiceStatus tests
    // ──────────────────────────────────────────────

    @Test
    void updateServiceStatus_updatesStatusAndReturns() {
        ServiceRegistration entity = buildServiceEntity();
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(entity));
        when(serviceRepository.save(any(ServiceRegistration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        stubCountsForEntity(SERVICE_ID);

        ServiceRegistrationResponse response =
                serviceRegistryService.updateServiceStatus(SERVICE_ID, ServiceStatus.DEPRECATED);

        assertThat(response.status()).isEqualTo(ServiceStatus.DEPRECATED);
        verify(serviceRepository).save(any(ServiceRegistration.class));
    }

    // ──────────────────────────────────────────────
    // deleteService tests
    // ──────────────────────────────────────────────

    @Test
    void deleteService_deletesWhenNoMembershipsOrDependents() {
        ServiceRegistration entity = buildServiceEntity();
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(entity));
        when(solutionMemberRepository.findByServiceId(SERVICE_ID)).thenReturn(Collections.emptyList());
        when(dependencyRepository.findByTargetServiceId(SERVICE_ID)).thenReturn(Collections.emptyList());

        serviceRegistryService.deleteService(SERVICE_ID);

        verify(serviceRepository).delete(entity);
    }

    @Test
    void deleteService_throwsValidationException_whenServiceBelongsToSolutions() {
        ServiceRegistration entity = buildServiceEntity();
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(entity));

        SolutionMember member = buildSolutionMember(entity, "My Platform");
        when(solutionMemberRepository.findByServiceId(SERVICE_ID)).thenReturn(List.of(member));

        assertThatThrownBy(() -> serviceRegistryService.deleteService(SERVICE_ID))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("belongs to solutions")
                .hasMessageContaining("My Platform");
    }

    @Test
    void deleteService_throwsValidationException_whenServiceHasRequiredDependents() {
        ServiceRegistration entity = buildServiceEntity();
        ServiceRegistration dependentService = buildServiceEntity(UUID.randomUUID(), "Dependent Service", "dependent-svc");

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(entity));
        when(solutionMemberRepository.findByServiceId(SERVICE_ID)).thenReturn(Collections.emptyList());

        ServiceDependency requiredDep = buildDependency(dependentService, entity, true);
        when(dependencyRepository.findByTargetServiceId(SERVICE_ID)).thenReturn(List.of(requiredDep));

        assertThatThrownBy(() -> serviceRegistryService.deleteService(SERVICE_ID))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("active dependents")
                .hasMessageContaining("Dependent Service");
    }

    @Test
    void deleteService_allowsDeletion_whenDependentsAreNotRequired() {
        ServiceRegistration entity = buildServiceEntity();
        ServiceRegistration dependentService = buildServiceEntity(UUID.randomUUID(), "Optional Dep", "optional-dep");

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(entity));
        when(solutionMemberRepository.findByServiceId(SERVICE_ID)).thenReturn(Collections.emptyList());

        ServiceDependency optionalDep = buildDependency(dependentService, entity, false);
        when(dependencyRepository.findByTargetServiceId(SERVICE_ID)).thenReturn(List.of(optionalDep));

        serviceRegistryService.deleteService(SERVICE_ID);

        verify(serviceRepository).delete(entity);
    }

    @Test
    void deleteService_throwsNotFoundException_whenNotFound() {
        UUID missingId = UUID.randomUUID();
        when(serviceRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> serviceRegistryService.deleteService(missingId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(missingId.toString());
    }

    // ──────────────────────────────────────────────
    // cloneService tests
    // ──────────────────────────────────────────────

    @Test
    void cloneService_copiesFieldsAndAutoAllocatesPorts() {
        ServiceRegistration original = buildServiceEntity();
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(original));
        when(serviceRepository.existsByTeamIdAndSlug(eq(TEAM_ID), anyString())).thenReturn(false);
        when(serviceRepository.save(any(ServiceRegistration.class)))
                .thenAnswer(invocation -> {
                    ServiceRegistration saved = invocation.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    saved.setCreatedAt(Instant.now());
                    saved.setUpdatedAt(Instant.now());
                    return saved;
                });

        PortAllocation originalPort = buildPortAllocation(SERVICE_ID, original, PortType.HTTP_API, 8080, "local");
        when(portAllocationRepository.findByServiceId(SERVICE_ID)).thenReturn(List.of(originalPort));
        when(portAllocationService.autoAllocate(any(UUID.class), eq("local"), eq(PortType.HTTP_API), eq(USER_ID)))
                .thenReturn(buildPortAllocationResponse(UUID.randomUUID()));
        stubCountsForEntity(any());

        CloneServiceRequest request = new CloneServiceRequest("Cloned Service", null);
        ServiceRegistrationResponse response = serviceRegistryService.cloneService(SERVICE_ID, request, USER_ID);

        assertThat(response.name()).isEqualTo("Cloned Service");
        assertThat(response.slug()).isEqualTo("cloned-service");
        assertThat(response.serviceType()).isEqualTo(original.getServiceType());
        assertThat(response.description()).isEqualTo(original.getDescription());
        assertThat(response.repoUrl()).isEqualTo(original.getRepoUrl());
        assertThat(response.techStack()).isEqualTo(original.getTechStack());
        verify(portAllocationService).autoAllocate(any(UUID.class), eq("local"), eq(PortType.HTTP_API), eq(USER_ID));
    }

    @Test
    void cloneService_usesProvidedSlug() {
        ServiceRegistration original = buildServiceEntity();
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(original));
        when(serviceRepository.existsByTeamIdAndSlug(eq(TEAM_ID), anyString())).thenReturn(false);
        when(serviceRepository.save(any(ServiceRegistration.class)))
                .thenAnswer(invocation -> {
                    ServiceRegistration saved = invocation.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    saved.setCreatedAt(Instant.now());
                    saved.setUpdatedAt(Instant.now());
                    return saved;
                });
        when(portAllocationRepository.findByServiceId(SERVICE_ID)).thenReturn(Collections.emptyList());
        stubCountsForEntity(any());

        CloneServiceRequest request = new CloneServiceRequest("Clone", "my-custom-clone");
        ServiceRegistrationResponse response = serviceRegistryService.cloneService(SERVICE_ID, request, USER_ID);

        assertThat(response.slug()).isEqualTo("my-custom-clone");
    }

    @Test
    void cloneService_throwsNotFoundException_whenOriginalNotFound() {
        UUID missingId = UUID.randomUUID();
        when(serviceRepository.findById(missingId)).thenReturn(Optional.empty());

        CloneServiceRequest request = new CloneServiceRequest("Clone", null);

        assertThatThrownBy(() -> serviceRegistryService.cloneService(missingId, request, USER_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(missingId.toString());
    }

    // ──────────────────────────────────────────────
    // getServiceIdentity tests
    // ──────────────────────────────────────────────

    @Test
    void getServiceIdentity_assemblesFullIdentity() {
        ServiceRegistration entity = buildServiceEntity();
        ServiceRegistration targetService = buildServiceEntity(UUID.randomUUID(), "Target Svc", "target-svc");
        ServiceRegistration sourceService = buildServiceEntity(UUID.randomUUID(), "Source Svc", "source-svc");

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(entity));
        stubCountsForEntity(SERVICE_ID);

        PortAllocation port = buildPortAllocation(SERVICE_ID, entity, PortType.HTTP_API, 8080, "local");
        when(portAllocationRepository.findByServiceId(SERVICE_ID)).thenReturn(List.of(port));
        when(portAllocationService.mapToResponse(any(PortAllocation.class)))
                .thenReturn(buildPortAllocationResponse(SERVICE_ID));

        ServiceDependency upstream = buildDependency(entity, targetService, true);
        ServiceDependency downstream = buildDependency(sourceService, entity, false);
        when(dependencyRepository.findBySourceServiceId(SERVICE_ID)).thenReturn(List.of(upstream));
        when(dependencyRepository.findByTargetServiceId(SERVICE_ID)).thenReturn(List.of(downstream));

        ApiRouteRegistration route = buildRoute(entity);
        when(routeRepository.findByServiceId(SERVICE_ID)).thenReturn(List.of(route));

        InfraResource infra = buildInfraResource(entity);
        when(infraRepository.findByServiceId(SERVICE_ID)).thenReturn(List.of(infra));

        EnvironmentConfig config = buildEnvironmentConfig(entity, "local");
        when(envConfigRepository.findByServiceId(SERVICE_ID)).thenReturn(List.of(config));

        ServiceIdentityResponse identity = serviceRegistryService.getServiceIdentity(SERVICE_ID, null);

        assertThat(identity.service().id()).isEqualTo(SERVICE_ID);
        assertThat(identity.ports()).hasSize(1);
        assertThat(identity.upstreamDependencies()).hasSize(1);
        assertThat(identity.downstreamDependencies()).hasSize(1);
        assertThat(identity.routes()).hasSize(1);
        assertThat(identity.infraResources()).hasSize(1);
        assertThat(identity.environmentConfigs()).hasSize(1);
    }

    @Test
    void getServiceIdentity_filtersByEnvironment_whenSpecified() {
        ServiceRegistration entity = buildServiceEntity();
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(entity));
        stubCountsForEntity(SERVICE_ID);

        PortAllocation port = buildPortAllocation(SERVICE_ID, entity, PortType.HTTP_API, 8080, "staging");
        when(portAllocationRepository.findByServiceIdAndEnvironment(SERVICE_ID, "staging"))
                .thenReturn(List.of(port));
        when(portAllocationService.mapToResponse(any(PortAllocation.class)))
                .thenReturn(buildPortAllocationResponse(SERVICE_ID));

        when(dependencyRepository.findBySourceServiceId(SERVICE_ID)).thenReturn(Collections.emptyList());
        when(dependencyRepository.findByTargetServiceId(SERVICE_ID)).thenReturn(Collections.emptyList());
        when(routeRepository.findByServiceId(SERVICE_ID)).thenReturn(Collections.emptyList());
        when(infraRepository.findByServiceId(SERVICE_ID)).thenReturn(Collections.emptyList());

        EnvironmentConfig config = buildEnvironmentConfig(entity, "staging");
        when(envConfigRepository.findByServiceIdAndEnvironment(SERVICE_ID, "staging"))
                .thenReturn(List.of(config));

        ServiceIdentityResponse identity = serviceRegistryService.getServiceIdentity(SERVICE_ID, "staging");

        assertThat(identity.ports()).hasSize(1);
        assertThat(identity.environmentConfigs()).hasSize(1);
        verify(portAllocationRepository).findByServiceIdAndEnvironment(SERVICE_ID, "staging");
        verify(envConfigRepository).findByServiceIdAndEnvironment(SERVICE_ID, "staging");
        verify(portAllocationRepository, never()).findByServiceId(SERVICE_ID);
        verify(envConfigRepository, never()).findByServiceId(SERVICE_ID);
    }

    @Test
    void getServiceIdentity_usesAllPortsAndConfigs_whenEnvironmentIsNull() {
        ServiceRegistration entity = buildServiceEntity();
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(entity));
        stubCountsForEntity(SERVICE_ID);

        when(portAllocationRepository.findByServiceId(SERVICE_ID)).thenReturn(Collections.emptyList());
        when(dependencyRepository.findBySourceServiceId(SERVICE_ID)).thenReturn(Collections.emptyList());
        when(dependencyRepository.findByTargetServiceId(SERVICE_ID)).thenReturn(Collections.emptyList());
        when(routeRepository.findByServiceId(SERVICE_ID)).thenReturn(Collections.emptyList());
        when(infraRepository.findByServiceId(SERVICE_ID)).thenReturn(Collections.emptyList());
        when(envConfigRepository.findByServiceId(SERVICE_ID)).thenReturn(Collections.emptyList());

        serviceRegistryService.getServiceIdentity(SERVICE_ID, null);

        verify(portAllocationRepository).findByServiceId(SERVICE_ID);
        verify(envConfigRepository).findByServiceId(SERVICE_ID);
    }

    // ──────────────────────────────────────────────
    // checkHealth tests
    // ──────────────────────────────────────────────

    @Test
    void checkHealth_returnsUnknown_whenNoHealthCheckUrlConfigured() {
        ServiceRegistration entity = buildServiceEntity();
        entity.setHealthCheckUrl(null);
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(entity));

        ServiceHealthResponse response = serviceRegistryService.checkHealth(SERVICE_ID);

        assertThat(response.healthStatus()).isEqualTo(HealthStatus.UNKNOWN);
        assertThat(response.errorMessage()).isEqualTo("No health check URL configured");
        assertThat(response.healthCheckUrl()).isNull();
        verify(serviceRepository, never()).save(any());
    }

    @Test
    void checkHealth_returnsUnknown_whenHealthCheckUrlIsBlank() {
        ServiceRegistration entity = buildServiceEntity();
        entity.setHealthCheckUrl("   ");
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(entity));

        ServiceHealthResponse response = serviceRegistryService.checkHealth(SERVICE_ID);

        assertThat(response.healthStatus()).isEqualTo(HealthStatus.UNKNOWN);
    }

    @Test
    void checkHealth_returnsUp_when200Response() {
        ServiceRegistration entity = buildServiceEntity();
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(entity));
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("OK", HttpStatus.OK));
        when(serviceRepository.save(any(ServiceRegistration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ServiceHealthResponse response = serviceRegistryService.checkHealth(SERVICE_ID);

        assertThat(response.healthStatus()).isEqualTo(HealthStatus.UP);
        assertThat(response.serviceId()).isEqualTo(SERVICE_ID);
        assertThat(response.errorMessage()).isNull();
        verify(serviceRepository).save(any(ServiceRegistration.class));
    }

    @Test
    void checkHealth_returnsDegraded_whenNon200Response() {
        ServiceRegistration entity = buildServiceEntity();
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(entity));
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("Service Unavailable", HttpStatus.SERVICE_UNAVAILABLE));
        when(serviceRepository.save(any(ServiceRegistration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ServiceHealthResponse response = serviceRegistryService.checkHealth(SERVICE_ID);

        assertThat(response.healthStatus()).isEqualTo(HealthStatus.DEGRADED);
        assertThat(response.errorMessage()).contains("HTTP");
    }

    @Test
    void checkHealth_returnsDown_onResourceAccessException() {
        ServiceRegistration entity = buildServiceEntity();
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(entity));
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));
        when(serviceRepository.save(any(ServiceRegistration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ServiceHealthResponse response = serviceRegistryService.checkHealth(SERVICE_ID);

        assertThat(response.healthStatus()).isEqualTo(HealthStatus.DOWN);
        assertThat(response.errorMessage()).contains("Connection refused");
        verify(serviceRepository).save(any(ServiceRegistration.class));
    }

    @Test
    void checkHealth_updatesEntityLastHealthStatusAndTimestamp() {
        ServiceRegistration entity = buildServiceEntity();
        entity.setLastHealthStatus(null);
        entity.setLastHealthCheckAt(null);
        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(entity));
        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("OK", HttpStatus.OK));
        when(serviceRepository.save(any(ServiceRegistration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        serviceRegistryService.checkHealth(SERVICE_ID);

        assertThat(entity.getLastHealthStatus()).isEqualTo(HealthStatus.UP);
        assertThat(entity.getLastHealthCheckAt()).isNotNull();
    }

    // ──────────────────────────────────────────────
    // checkAllHealth tests
    // ──────────────────────────────────────────────

    @Test
    void checkAllHealth_loadsActiveServicesAndChecksEach() {
        ServiceRegistration svc1 = buildServiceEntity(UUID.randomUUID(), "Service A", "service-a");
        ServiceRegistration svc2 = buildServiceEntity(UUID.randomUUID(), "Service B", "service-b");

        when(serviceRepository.findByTeamIdAndStatus(TEAM_ID, ServiceStatus.ACTIVE))
                .thenReturn(List.of(svc1, svc2));

        // checkHealth calls findById internally for each service
        when(serviceRepository.findById(svc1.getId())).thenReturn(Optional.of(svc1));
        when(serviceRepository.findById(svc2.getId())).thenReturn(Optional.of(svc2));

        when(restTemplate.getForEntity(anyString(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("OK", HttpStatus.OK));
        when(serviceRepository.save(any(ServiceRegistration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<ServiceHealthResponse> responses = serviceRegistryService.checkAllHealth(TEAM_ID);

        assertThat(responses).hasSize(2);
        assertThat(responses).allMatch(r -> r.healthStatus() == HealthStatus.UP);
    }

    @Test
    void checkAllHealth_returnsEmptyList_whenNoActiveServices() {
        when(serviceRepository.findByTeamIdAndStatus(TEAM_ID, ServiceStatus.ACTIVE))
                .thenReturn(Collections.emptyList());

        List<ServiceHealthResponse> responses = serviceRegistryService.checkAllHealth(TEAM_ID);

        assertThat(responses).isEmpty();
    }
}
