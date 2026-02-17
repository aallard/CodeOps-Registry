package com.codeops.registry.service;

import com.codeops.registry.dto.response.ConfigTemplateResponse;
import com.codeops.registry.dto.response.DependencyNodeResponse;
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

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ConfigEngineService}.
 *
 * <p>Tests cover Docker Compose generation, Application YML generation, Claude Code Header
 * generation, batch generation, solution-wide Docker Compose, and template CRUD operations
 * including upsert version incrementing.</p>
 */
@ExtendWith(MockitoExtension.class)
class ConfigEngineServiceTest {

    @Mock
    private ServiceRegistrationRepository serviceRepository;
    @Mock
    private PortAllocationRepository portAllocationRepository;
    @Mock
    private ServiceDependencyRepository dependencyRepository;
    @Mock
    private EnvironmentConfigRepository environmentConfigRepository;
    @Mock
    private ConfigTemplateRepository configTemplateRepository;
    @Mock
    private ApiRouteRegistrationRepository routeRepository;
    @Mock
    private InfraResourceRepository infraResourceRepository;
    @Mock
    private SolutionRepository solutionRepository;
    @Mock
    private SolutionMemberRepository solutionMemberRepository;
    @Mock
    private DependencyGraphService dependencyGraphService;

    @InjectMocks
    private ConfigEngineService configEngineService;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID SERVICE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TEMPLATE_ID = UUID.randomUUID();
    private static final UUID SOLUTION_ID = UUID.randomUUID();
    private static final String ENVIRONMENT = "local";

    // ──────────────────────────────────────────────────────────────
    // Helper methods
    // ──────────────────────────────────────────────────────────────

    private ServiceRegistration buildService(UUID serviceId, UUID teamId, String name, String slug) {
        ServiceRegistration service = ServiceRegistration.builder()
                .teamId(teamId)
                .name(name)
                .slug(slug)
                .serviceType(ServiceType.SPRING_BOOT_API)
                .defaultBranch("main")
                .createdByUserId(USER_ID)
                .build();
        service.setId(serviceId);
        service.setCreatedAt(Instant.now());
        service.setUpdatedAt(Instant.now());
        return service;
    }

    private PortAllocation buildPort(ServiceRegistration service, String env, PortType type, int port) {
        PortAllocation pa = PortAllocation.builder()
                .service(service)
                .environment(env)
                .portType(type)
                .portNumber(port)
                .protocol("TCP")
                .isAutoAllocated(true)
                .allocatedByUserId(USER_ID)
                .build();
        pa.setId(UUID.randomUUID());
        pa.setCreatedAt(Instant.now());
        pa.setUpdatedAt(Instant.now());
        return pa;
    }

    private EnvironmentConfig buildEnvConfig(ServiceRegistration service, String env,
                                             String key, String value) {
        EnvironmentConfig ec = EnvironmentConfig.builder()
                .service(service)
                .environment(env)
                .configKey(key)
                .configValue(value)
                .configSource(ConfigSource.MANUAL)
                .build();
        ec.setId(UUID.randomUUID());
        ec.setCreatedAt(Instant.now());
        ec.setUpdatedAt(Instant.now());
        return ec;
    }

    private ServiceDependency buildDependency(ServiceRegistration source, ServiceRegistration target,
                                              DependencyType type) {
        ServiceDependency dep = ServiceDependency.builder()
                .sourceService(source)
                .targetService(target)
                .dependencyType(type)
                .isRequired(true)
                .build();
        dep.setId(UUID.randomUUID());
        dep.setCreatedAt(Instant.now());
        dep.setUpdatedAt(Instant.now());
        return dep;
    }

    private InfraResource buildInfraResource(ServiceRegistration service, InfraResourceType type,
                                             String name, String env) {
        InfraResource ir = InfraResource.builder()
                .teamId(service != null ? service.getTeamId() : TEAM_ID)
                .service(service)
                .resourceType(type)
                .resourceName(name)
                .environment(env)
                .region("us-east-1")
                .arnOrUrl("arn:aws:s3:::" + name)
                .createdByUserId(USER_ID)
                .build();
        ir.setId(UUID.randomUUID());
        ir.setCreatedAt(Instant.now());
        ir.setUpdatedAt(Instant.now());
        return ir;
    }

    private ApiRouteRegistration buildRoute(ServiceRegistration service, String prefix,
                                            String methods, String env) {
        ApiRouteRegistration route = ApiRouteRegistration.builder()
                .service(service)
                .routePrefix(prefix)
                .httpMethods(methods)
                .environment(env)
                .build();
        route.setId(UUID.randomUUID());
        route.setCreatedAt(Instant.now());
        route.setUpdatedAt(Instant.now());
        return route;
    }

    private ConfigTemplate buildConfigTemplate(ServiceRegistration service, ConfigTemplateType type,
                                               String env, String content, int version) {
        ConfigTemplate ct = ConfigTemplate.builder()
                .service(service)
                .templateType(type)
                .environment(env)
                .contentText(content)
                .isAutoGenerated(true)
                .generatedFrom("registry-data")
                .version(version)
                .build();
        ct.setId(UUID.randomUUID());
        ct.setCreatedAt(Instant.now());
        ct.setUpdatedAt(Instant.now());
        return ct;
    }

    private Solution buildSolution(UUID solutionId, UUID teamId, String name) {
        Solution sol = Solution.builder()
                .teamId(teamId)
                .name(name)
                .slug(name.toLowerCase().replace(' ', '-'))
                .category(SolutionCategory.PLATFORM)
                .createdByUserId(USER_ID)
                .build();
        sol.setId(solutionId);
        sol.setCreatedAt(Instant.now());
        sol.setUpdatedAt(Instant.now());
        return sol;
    }

    private SolutionMember buildMember(Solution solution, ServiceRegistration service, int order) {
        SolutionMember member = SolutionMember.builder()
                .solution(solution)
                .service(service)
                .role(SolutionMemberRole.CORE)
                .displayOrder(order)
                .build();
        member.setId(UUID.randomUUID());
        member.setCreatedAt(Instant.now());
        member.setUpdatedAt(Instant.now());
        return member;
    }

    private void setupSaveMock() {
        when(configTemplateRepository.save(any(ConfigTemplate.class))).thenAnswer(invocation -> {
            ConfigTemplate saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            if (saved.getCreatedAt() == null) {
                saved.setCreatedAt(Instant.now());
            }
            saved.setUpdatedAt(Instant.now());
            return saved;
        });
    }

    private void setupEmptyRepoDefaults(UUID serviceId) {
        lenient().when(portAllocationRepository.findByServiceIdAndEnvironment(serviceId, ENVIRONMENT))
                .thenReturn(Collections.emptyList());
        lenient().when(environmentConfigRepository.findByServiceIdAndEnvironment(serviceId, ENVIRONMENT))
                .thenReturn(Collections.emptyList());
        lenient().when(dependencyRepository.findBySourceServiceId(serviceId))
                .thenReturn(Collections.emptyList());
        lenient().when(dependencyRepository.findByTargetServiceId(serviceId))
                .thenReturn(Collections.emptyList());
        lenient().when(infraResourceRepository.findByServiceId(serviceId))
                .thenReturn(Collections.emptyList());
        lenient().when(routeRepository.findByServiceId(serviceId))
                .thenReturn(Collections.emptyList());
        lenient().when(configTemplateRepository.findByServiceIdAndTemplateTypeAndEnvironment(
                any(UUID.class), any(ConfigTemplateType.class), any(String.class)))
                .thenReturn(Optional.empty());
    }

    // ──────────────────────────────────────────────────────────────
    // generateDockerCompose tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void generateDockerCompose_success_withAllSections() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");
        service.setHealthCheckUrl("http://localhost:8090/health");
        service.setHealthCheckIntervalSeconds(30);

        ServiceRegistration depService = buildService(UUID.randomUUID(), TEAM_ID, "Dep Service", "dep-service");
        PortAllocation port = buildPort(service, ENVIRONMENT, PortType.HTTP_API, 8090);
        EnvironmentConfig envConfig = buildEnvConfig(service, ENVIRONMENT, "DB_HOST", "localhost");
        ServiceDependency dep = buildDependency(service, depService, DependencyType.HTTP_REST);
        InfraResource volume = buildInfraResource(service, InfraResourceType.DOCKER_VOLUME, "app-data", ENVIRONMENT);

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        when(portAllocationRepository.findByServiceIdAndEnvironment(SERVICE_ID, ENVIRONMENT))
                .thenReturn(List.of(port));
        when(environmentConfigRepository.findByServiceIdAndEnvironment(SERVICE_ID, ENVIRONMENT))
                .thenReturn(List.of(envConfig));
        when(dependencyRepository.findBySourceServiceId(SERVICE_ID)).thenReturn(List.of(dep));
        when(infraResourceRepository.findByServiceId(SERVICE_ID)).thenReturn(List.of(volume));
        when(configTemplateRepository.findByServiceIdAndTemplateTypeAndEnvironment(
                SERVICE_ID, ConfigTemplateType.DOCKER_COMPOSE, ENVIRONMENT))
                .thenReturn(Optional.empty());
        setupSaveMock();

        ConfigTemplateResponse response = configEngineService.generateDockerCompose(SERVICE_ID, ENVIRONMENT);

        assertThat(response).isNotNull();
        assertThat(response.templateType()).isEqualTo(ConfigTemplateType.DOCKER_COMPOSE);
        assertThat(response.environment()).isEqualTo(ENVIRONMENT);
        assertThat(response.version()).isEqualTo(1);
        assertThat(response.isAutoGenerated()).isTrue();

        String content = response.contentText();
        assertThat(content).contains("version: '3.8'");
        assertThat(content).contains("my-app:");
        assertThat(content).contains("image: my-app:latest");
        assertThat(content).contains("container_name: my-app");
        assertThat(content).contains("8090:8090");
        assertThat(content).contains("DB_HOST: localhost");
        assertThat(content).contains("dep-service");
        assertThat(content).contains("codeops-network");
        assertThat(content).contains("app-data:/data/app-data");
        assertThat(content).contains("com.codeops.service-id");
        assertThat(content).contains("healthcheck");
        assertThat(content).contains("curl");

        verify(configTemplateRepository).save(any(ConfigTemplate.class));
    }

    @Test
    void generateDockerCompose_noPorts() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        setupEmptyRepoDefaults(SERVICE_ID);
        setupSaveMock();

        ConfigTemplateResponse response = configEngineService.generateDockerCompose(SERVICE_ID, ENVIRONMENT);

        assertThat(response.contentText()).doesNotContain("ports:");
        assertThat(response.contentText()).contains("image: my-app:latest");
    }

    @Test
    void generateDockerCompose_noDependencies() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        setupEmptyRepoDefaults(SERVICE_ID);
        setupSaveMock();

        ConfigTemplateResponse response = configEngineService.generateDockerCompose(SERVICE_ID, ENVIRONMENT);

        assertThat(response.contentText()).doesNotContain("depends_on:");
    }

    @Test
    void generateDockerCompose_withHealthCheck() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");
        service.setHealthCheckUrl("http://localhost:8090/health");
        service.setHealthCheckIntervalSeconds(15);

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        setupEmptyRepoDefaults(SERVICE_ID);
        setupSaveMock();

        ConfigTemplateResponse response = configEngineService.generateDockerCompose(SERVICE_ID, ENVIRONMENT);

        assertThat(response.contentText()).contains("healthcheck:");
        assertThat(response.contentText()).contains("http://localhost:8090/health");
        assertThat(response.contentText()).contains("15s");
    }

    @Test
    void generateDockerCompose_withDockerVolume() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");
        InfraResource volume = buildInfraResource(service, InfraResourceType.DOCKER_VOLUME, "db-data", ENVIRONMENT);

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        when(portAllocationRepository.findByServiceIdAndEnvironment(SERVICE_ID, ENVIRONMENT))
                .thenReturn(Collections.emptyList());
        when(environmentConfigRepository.findByServiceIdAndEnvironment(SERVICE_ID, ENVIRONMENT))
                .thenReturn(Collections.emptyList());
        when(dependencyRepository.findBySourceServiceId(SERVICE_ID))
                .thenReturn(Collections.emptyList());
        when(infraResourceRepository.findByServiceId(SERVICE_ID)).thenReturn(List.of(volume));
        when(configTemplateRepository.findByServiceIdAndTemplateTypeAndEnvironment(
                SERVICE_ID, ConfigTemplateType.DOCKER_COMPOSE, ENVIRONMENT))
                .thenReturn(Optional.empty());
        setupSaveMock();

        ConfigTemplateResponse response = configEngineService.generateDockerCompose(SERVICE_ID, ENVIRONMENT);

        assertThat(response.contentText()).contains("db-data:/data/db-data");
    }

    @Test
    void generateDockerCompose_serviceNotFound() {
        UUID missingId = UUID.randomUUID();
        when(serviceRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> configEngineService.generateDockerCompose(missingId, ENVIRONMENT))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("ServiceRegistration")
                .hasMessageContaining(missingId.toString());

        verify(configTemplateRepository, never()).save(any());
    }

    @Test
    void generateDockerCompose_upsert_incrementsVersion() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");
        ConfigTemplate existing = buildConfigTemplate(service, ConfigTemplateType.DOCKER_COMPOSE,
                ENVIRONMENT, "old content", 3);

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        setupEmptyRepoDefaults(SERVICE_ID);
        when(configTemplateRepository.findByServiceIdAndTemplateTypeAndEnvironment(
                SERVICE_ID, ConfigTemplateType.DOCKER_COMPOSE, ENVIRONMENT))
                .thenReturn(Optional.of(existing));
        setupSaveMock();

        ConfigTemplateResponse response = configEngineService.generateDockerCompose(SERVICE_ID, ENVIRONMENT);

        assertThat(response.version()).isEqualTo(4);
        assertThat(response.contentText()).isNotEqualTo("old content");
    }

    @Test
    void generateDockerCompose_environmentFiltering_volumesByEnv() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");
        InfraResource localVolume = buildInfraResource(service, InfraResourceType.DOCKER_VOLUME,
                "local-data", ENVIRONMENT);
        InfraResource prodVolume = buildInfraResource(service, InfraResourceType.DOCKER_VOLUME,
                "prod-data", "production");

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        when(portAllocationRepository.findByServiceIdAndEnvironment(SERVICE_ID, ENVIRONMENT))
                .thenReturn(Collections.emptyList());
        when(environmentConfigRepository.findByServiceIdAndEnvironment(SERVICE_ID, ENVIRONMENT))
                .thenReturn(Collections.emptyList());
        when(dependencyRepository.findBySourceServiceId(SERVICE_ID))
                .thenReturn(Collections.emptyList());
        when(infraResourceRepository.findByServiceId(SERVICE_ID))
                .thenReturn(List.of(localVolume, prodVolume));
        when(configTemplateRepository.findByServiceIdAndTemplateTypeAndEnvironment(
                SERVICE_ID, ConfigTemplateType.DOCKER_COMPOSE, ENVIRONMENT))
                .thenReturn(Optional.empty());
        setupSaveMock();

        ConfigTemplateResponse response = configEngineService.generateDockerCompose(SERVICE_ID, ENVIRONMENT);

        assertThat(response.contentText()).contains("local-data:/data/local-data");
        assertThat(response.contentText()).doesNotContain("prod-data");
    }

    @Test
    void generateDockerCompose_noEnvConfigs() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        setupEmptyRepoDefaults(SERVICE_ID);
        setupSaveMock();

        ConfigTemplateResponse response = configEngineService.generateDockerCompose(SERVICE_ID, ENVIRONMENT);

        assertThat(response.contentText()).doesNotContain("environment:");
    }

    @Test
    void generateDockerCompose_labelsIncluded() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        setupEmptyRepoDefaults(SERVICE_ID);
        setupSaveMock();

        ConfigTemplateResponse response = configEngineService.generateDockerCompose(SERVICE_ID, ENVIRONMENT);

        assertThat(response.contentText()).contains("com.codeops.service-id: " + SERVICE_ID);
        assertThat(response.contentText()).contains("com.codeops.service-type: SPRING_BOOT_API");
        assertThat(response.contentText()).contains("com.codeops.team-id: " + TEAM_ID);
    }

    @Test
    void generateDockerCompose_networksIncluded() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        setupEmptyRepoDefaults(SERVICE_ID);
        setupSaveMock();

        ConfigTemplateResponse response = configEngineService.generateDockerCompose(SERVICE_ID, ENVIRONMENT);

        assertThat(response.contentText()).contains("codeops-network");
        assertThat(response.contentText()).contains("driver: bridge");
    }

    @Test
    void generateDockerCompose_newTemplate_versionOne() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        setupEmptyRepoDefaults(SERVICE_ID);
        setupSaveMock();

        ConfigTemplateResponse response = configEngineService.generateDockerCompose(SERVICE_ID, ENVIRONMENT);

        assertThat(response.version()).isEqualTo(1);
        assertThat(response.generatedFrom()).isEqualTo("registry-data");
    }

    // ──────────────────────────────────────────────────────────────
    // generateApplicationYml tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void generateApplicationYml_success_withHttpPort() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");
        PortAllocation httpPort = buildPort(service, ENVIRONMENT, PortType.HTTP_API, 8090);

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        when(portAllocationRepository.findByServiceIdAndEnvironment(SERVICE_ID, ENVIRONMENT))
                .thenReturn(List.of(httpPort));
        when(environmentConfigRepository.findByServiceIdAndEnvironment(SERVICE_ID, ENVIRONMENT))
                .thenReturn(Collections.emptyList());
        when(dependencyRepository.findBySourceServiceId(SERVICE_ID))
                .thenReturn(Collections.emptyList());
        when(configTemplateRepository.findByServiceIdAndTemplateTypeAndEnvironment(
                SERVICE_ID, ConfigTemplateType.APPLICATION_YML, ENVIRONMENT))
                .thenReturn(Optional.empty());
        setupSaveMock();

        ConfigTemplateResponse response = configEngineService.generateApplicationYml(SERVICE_ID, ENVIRONMENT);

        assertThat(response.templateType()).isEqualTo(ConfigTemplateType.APPLICATION_YML);
        assertThat(response.contentText()).contains("port: '8090'");
    }

    @Test
    void generateApplicationYml_withEnvConfigs() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");
        EnvironmentConfig config1 = buildEnvConfig(service, ENVIRONMENT, "custom.property", "value1");
        EnvironmentConfig config2 = buildEnvConfig(service, ENVIRONMENT, "another.key", "value2");

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        when(portAllocationRepository.findByServiceIdAndEnvironment(SERVICE_ID, ENVIRONMENT))
                .thenReturn(Collections.emptyList());
        when(environmentConfigRepository.findByServiceIdAndEnvironment(SERVICE_ID, ENVIRONMENT))
                .thenReturn(List.of(config1, config2));
        when(dependencyRepository.findBySourceServiceId(SERVICE_ID))
                .thenReturn(Collections.emptyList());
        when(configTemplateRepository.findByServiceIdAndTemplateTypeAndEnvironment(
                SERVICE_ID, ConfigTemplateType.APPLICATION_YML, ENVIRONMENT))
                .thenReturn(Optional.empty());
        setupSaveMock();

        ConfigTemplateResponse response = configEngineService.generateApplicationYml(SERVICE_ID, ENVIRONMENT);

        assertThat(response.contentText()).contains("property: value1");
        assertThat(response.contentText()).contains("key: value2");
    }

    @Test
    void generateApplicationYml_withUpstreamDeps() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");
        UUID depId = UUID.randomUUID();
        ServiceRegistration depService = buildService(depId, TEAM_ID, "Other Service", "other-service");
        ServiceDependency dep = buildDependency(service, depService, DependencyType.HTTP_REST);
        PortAllocation depPort = buildPort(depService, ENVIRONMENT, PortType.HTTP_API, 8091);

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        when(portAllocationRepository.findByServiceIdAndEnvironment(SERVICE_ID, ENVIRONMENT))
                .thenReturn(Collections.emptyList());
        when(portAllocationRepository.findByServiceIdAndEnvironment(depId, ENVIRONMENT))
                .thenReturn(List.of(depPort));
        when(environmentConfigRepository.findByServiceIdAndEnvironment(SERVICE_ID, ENVIRONMENT))
                .thenReturn(Collections.emptyList());
        when(dependencyRepository.findBySourceServiceId(SERVICE_ID)).thenReturn(List.of(dep));
        when(configTemplateRepository.findByServiceIdAndTemplateTypeAndEnvironment(
                SERVICE_ID, ConfigTemplateType.APPLICATION_YML, ENVIRONMENT))
                .thenReturn(Optional.empty());
        setupSaveMock();

        ConfigTemplateResponse response = configEngineService.generateApplicationYml(SERVICE_ID, ENVIRONMENT);

        assertThat(response.contentText()).contains("other-service");
        assertThat(response.contentText()).contains("http://localhost:8091");
    }

    @Test
    void generateApplicationYml_withDatasourceConfigs() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");
        EnvironmentConfig dsUrl = buildEnvConfig(service, ENVIRONMENT,
                "spring.datasource.url", "jdbc:postgresql://localhost:5432/mydb");
        EnvironmentConfig dsUser = buildEnvConfig(service, ENVIRONMENT,
                "spring.datasource.username", "admin");

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        when(portAllocationRepository.findByServiceIdAndEnvironment(SERVICE_ID, ENVIRONMENT))
                .thenReturn(Collections.emptyList());
        when(environmentConfigRepository.findByServiceIdAndEnvironment(SERVICE_ID, ENVIRONMENT))
                .thenReturn(List.of(dsUrl, dsUser));
        when(dependencyRepository.findBySourceServiceId(SERVICE_ID))
                .thenReturn(Collections.emptyList());
        when(configTemplateRepository.findByServiceIdAndTemplateTypeAndEnvironment(
                SERVICE_ID, ConfigTemplateType.APPLICATION_YML, ENVIRONMENT))
                .thenReturn(Optional.empty());
        setupSaveMock();

        ConfigTemplateResponse response = configEngineService.generateApplicationYml(SERVICE_ID, ENVIRONMENT);

        assertThat(response.contentText()).contains("datasource:");
        assertThat(response.contentText()).contains("username: admin");
    }

    @Test
    void generateApplicationYml_noHttpPort() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        when(portAllocationRepository.findByServiceIdAndEnvironment(SERVICE_ID, ENVIRONMENT))
                .thenReturn(Collections.emptyList());
        when(environmentConfigRepository.findByServiceIdAndEnvironment(SERVICE_ID, ENVIRONMENT))
                .thenReturn(Collections.emptyList());
        when(dependencyRepository.findBySourceServiceId(SERVICE_ID))
                .thenReturn(Collections.emptyList());
        when(configTemplateRepository.findByServiceIdAndTemplateTypeAndEnvironment(
                SERVICE_ID, ConfigTemplateType.APPLICATION_YML, ENVIRONMENT))
                .thenReturn(Optional.empty());
        setupSaveMock();

        ConfigTemplateResponse response = configEngineService.generateApplicationYml(SERVICE_ID, ENVIRONMENT);

        assertThat(response.contentText()).doesNotContain("server:");
        assertThat(response.contentText()).contains("name: my-app");
    }

    @Test
    void generateApplicationYml_noEnvConfigs_minimalYaml() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        when(portAllocationRepository.findByServiceIdAndEnvironment(SERVICE_ID, ENVIRONMENT))
                .thenReturn(Collections.emptyList());
        when(environmentConfigRepository.findByServiceIdAndEnvironment(SERVICE_ID, ENVIRONMENT))
                .thenReturn(Collections.emptyList());
        when(dependencyRepository.findBySourceServiceId(SERVICE_ID))
                .thenReturn(Collections.emptyList());
        when(configTemplateRepository.findByServiceIdAndTemplateTypeAndEnvironment(
                SERVICE_ID, ConfigTemplateType.APPLICATION_YML, ENVIRONMENT))
                .thenReturn(Optional.empty());
        setupSaveMock();

        ConfigTemplateResponse response = configEngineService.generateApplicationYml(SERVICE_ID, ENVIRONMENT);

        assertThat(response.contentText()).contains("spring:");
        assertThat(response.contentText()).contains("application:");
        assertThat(response.contentText()).contains("name: my-app");
    }

    @Test
    void generateApplicationYml_serviceNotFound() {
        UUID missingId = UUID.randomUUID();
        when(serviceRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> configEngineService.generateApplicationYml(missingId, ENVIRONMENT))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("ServiceRegistration");

        verify(configTemplateRepository, never()).save(any());
    }

    @Test
    void generateApplicationYml_upsert_incrementsVersion() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");
        ConfigTemplate existing = buildConfigTemplate(service, ConfigTemplateType.APPLICATION_YML,
                ENVIRONMENT, "old content", 5);

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        when(portAllocationRepository.findByServiceIdAndEnvironment(SERVICE_ID, ENVIRONMENT))
                .thenReturn(Collections.emptyList());
        when(environmentConfigRepository.findByServiceIdAndEnvironment(SERVICE_ID, ENVIRONMENT))
                .thenReturn(Collections.emptyList());
        when(dependencyRepository.findBySourceServiceId(SERVICE_ID))
                .thenReturn(Collections.emptyList());
        when(configTemplateRepository.findByServiceIdAndTemplateTypeAndEnvironment(
                SERVICE_ID, ConfigTemplateType.APPLICATION_YML, ENVIRONMENT))
                .thenReturn(Optional.of(existing));
        setupSaveMock();

        ConfigTemplateResponse response = configEngineService.generateApplicationYml(SERVICE_ID, ENVIRONMENT);

        assertThat(response.version()).isEqualTo(6);
    }

    @Test
    void generateApplicationYml_springApplicationName() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My Cool App", "my-cool-app");

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        when(portAllocationRepository.findByServiceIdAndEnvironment(SERVICE_ID, ENVIRONMENT))
                .thenReturn(Collections.emptyList());
        when(environmentConfigRepository.findByServiceIdAndEnvironment(SERVICE_ID, ENVIRONMENT))
                .thenReturn(Collections.emptyList());
        when(dependencyRepository.findBySourceServiceId(SERVICE_ID))
                .thenReturn(Collections.emptyList());
        when(configTemplateRepository.findByServiceIdAndTemplateTypeAndEnvironment(
                SERVICE_ID, ConfigTemplateType.APPLICATION_YML, ENVIRONMENT))
                .thenReturn(Optional.empty());
        setupSaveMock();

        ConfigTemplateResponse response = configEngineService.generateApplicationYml(SERVICE_ID, ENVIRONMENT);

        assertThat(response.contentText()).contains("name: my-cool-app");
    }

    @Test
    void generateApplicationYml_depWithNoPort_noUrlGenerated() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");
        UUID depId = UUID.randomUUID();
        ServiceRegistration depService = buildService(depId, TEAM_ID, "No Port", "no-port");
        ServiceDependency dep = buildDependency(service, depService, DependencyType.HTTP_REST);

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        when(portAllocationRepository.findByServiceIdAndEnvironment(SERVICE_ID, ENVIRONMENT))
                .thenReturn(Collections.emptyList());
        when(portAllocationRepository.findByServiceIdAndEnvironment(depId, ENVIRONMENT))
                .thenReturn(Collections.emptyList());
        when(environmentConfigRepository.findByServiceIdAndEnvironment(SERVICE_ID, ENVIRONMENT))
                .thenReturn(Collections.emptyList());
        when(dependencyRepository.findBySourceServiceId(SERVICE_ID)).thenReturn(List.of(dep));
        when(configTemplateRepository.findByServiceIdAndTemplateTypeAndEnvironment(
                SERVICE_ID, ConfigTemplateType.APPLICATION_YML, ENVIRONMENT))
                .thenReturn(Optional.empty());
        setupSaveMock();

        ConfigTemplateResponse response = configEngineService.generateApplicationYml(SERVICE_ID, ENVIRONMENT);

        assertThat(response.contentText()).doesNotContain("no-port");
        assertThat(response.contentText()).doesNotContain("codeops:");
    }

    @Test
    void generateApplicationYml_multipleUpstreamDeps() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");
        UUID dep1Id = UUID.randomUUID();
        UUID dep2Id = UUID.randomUUID();
        ServiceRegistration dep1 = buildService(dep1Id, TEAM_ID, "Auth Service", "auth-service");
        ServiceRegistration dep2 = buildService(dep2Id, TEAM_ID, "Data Service", "data-service");
        ServiceDependency sd1 = buildDependency(service, dep1, DependencyType.HTTP_REST);
        ServiceDependency sd2 = buildDependency(service, dep2, DependencyType.HTTP_REST);
        PortAllocation dep1Port = buildPort(dep1, ENVIRONMENT, PortType.HTTP_API, 8091);
        PortAllocation dep2Port = buildPort(dep2, ENVIRONMENT, PortType.HTTP_API, 8092);

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        when(portAllocationRepository.findByServiceIdAndEnvironment(SERVICE_ID, ENVIRONMENT))
                .thenReturn(Collections.emptyList());
        when(portAllocationRepository.findByServiceIdAndEnvironment(dep1Id, ENVIRONMENT))
                .thenReturn(List.of(dep1Port));
        when(portAllocationRepository.findByServiceIdAndEnvironment(dep2Id, ENVIRONMENT))
                .thenReturn(List.of(dep2Port));
        when(environmentConfigRepository.findByServiceIdAndEnvironment(SERVICE_ID, ENVIRONMENT))
                .thenReturn(Collections.emptyList());
        when(dependencyRepository.findBySourceServiceId(SERVICE_ID))
                .thenReturn(List.of(sd1, sd2));
        when(configTemplateRepository.findByServiceIdAndTemplateTypeAndEnvironment(
                SERVICE_ID, ConfigTemplateType.APPLICATION_YML, ENVIRONMENT))
                .thenReturn(Optional.empty());
        setupSaveMock();

        ConfigTemplateResponse response = configEngineService.generateApplicationYml(SERVICE_ID, ENVIRONMENT);

        assertThat(response.contentText()).contains("auth-service");
        assertThat(response.contentText()).contains("http://localhost:8091");
        assertThat(response.contentText()).contains("data-service");
        assertThat(response.contentText()).contains("http://localhost:8092");
    }

    // ──────────────────────────────────────────────────────────────
    // generateClaudeCodeHeader tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void generateClaudeCodeHeader_success_fullData() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");
        service.setRepoUrl("https://github.com/team/my-app");
        service.setTechStack("Java, Spring Boot");

        ServiceRegistration depTarget = buildService(UUID.randomUUID(), TEAM_ID, "Dep Target", "dep-target");
        ServiceRegistration depSource = buildService(UUID.randomUUID(), TEAM_ID, "Dep Source", "dep-source");

        PortAllocation port = buildPort(service, ENVIRONMENT, PortType.HTTP_API, 8090);
        ServiceDependency upstream = buildDependency(service, depTarget, DependencyType.HTTP_REST);
        ServiceDependency downstream = buildDependency(depSource, service, DependencyType.GRPC);
        ApiRouteRegistration route = buildRoute(service, "/api/v1/users", "GET,POST", ENVIRONMENT);
        InfraResource infra = buildInfraResource(service, InfraResourceType.S3_BUCKET, "my-bucket", ENVIRONMENT);
        EnvironmentConfig config = buildEnvConfig(service, ENVIRONMENT, "db.url", "jdbc:postgresql://localhost");

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        when(portAllocationRepository.findByServiceIdAndEnvironment(SERVICE_ID, ENVIRONMENT))
                .thenReturn(List.of(port));
        when(dependencyRepository.findBySourceServiceId(SERVICE_ID)).thenReturn(List.of(upstream));
        when(dependencyRepository.findByTargetServiceId(SERVICE_ID)).thenReturn(List.of(downstream));
        when(routeRepository.findByServiceId(SERVICE_ID)).thenReturn(List.of(route));
        when(infraResourceRepository.findByServiceId(SERVICE_ID)).thenReturn(List.of(infra));
        when(environmentConfigRepository.findByServiceIdAndEnvironment(SERVICE_ID, ENVIRONMENT))
                .thenReturn(List.of(config));
        when(portAllocationRepository.findByServiceIdAndEnvironment(depTarget.getId(), ENVIRONMENT))
                .thenReturn(Collections.emptyList());
        when(configTemplateRepository.findByServiceIdAndTemplateTypeAndEnvironment(
                SERVICE_ID, ConfigTemplateType.CLAUDE_CODE_HEADER, ENVIRONMENT))
                .thenReturn(Optional.empty());
        setupSaveMock();

        ConfigTemplateResponse response = configEngineService.generateClaudeCodeHeader(SERVICE_ID, ENVIRONMENT);

        assertThat(response.templateType()).isEqualTo(ConfigTemplateType.CLAUDE_CODE_HEADER);
        String content = response.contentText();
        assertThat(content).contains("# Service: My App (my-app)");
        assertThat(content).contains("# Type: SPRING_BOOT_API");
        assertThat(content).contains("# Repo: https://github.com/team/my-app");
        assertThat(content).contains("# Tech Stack: Java, Spring Boot");
        assertThat(content).contains("HTTP_API: 8090");
        assertThat(content).contains("Dep Target");
        assertThat(content).contains("[HTTP_REST]");
        assertThat(content).contains("Dep Source");
        assertThat(content).contains("[GRPC]");
        assertThat(content).contains("/api/v1/users");
        assertThat(content).contains("GET,POST");
        assertThat(content).contains("S3_BUCKET: my-bucket");
        assertThat(content).contains("db.url = jdbc:postgresql://localhost");
    }

    @Test
    void generateClaudeCodeHeader_noRoutes() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        setupEmptyRepoDefaults(SERVICE_ID);
        setupSaveMock();

        ConfigTemplateResponse response = configEngineService.generateClaudeCodeHeader(SERVICE_ID, ENVIRONMENT);

        assertThat(response.contentText()).contains("# API Routes:\n#   None");
    }

    @Test
    void generateClaudeCodeHeader_noInfra() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        setupEmptyRepoDefaults(SERVICE_ID);
        setupSaveMock();

        ConfigTemplateResponse response = configEngineService.generateClaudeCodeHeader(SERVICE_ID, ENVIRONMENT);

        assertThat(response.contentText()).contains("# Infrastructure:\n#   None");
    }

    @Test
    void generateClaudeCodeHeader_upstreamAndDownstreamDeps() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");
        ServiceRegistration upTarget = buildService(UUID.randomUUID(), TEAM_ID, "Upstream", "upstream");
        ServiceRegistration downSource = buildService(UUID.randomUUID(), TEAM_ID, "Downstream", "downstream");
        ServiceDependency upDep = buildDependency(service, upTarget, DependencyType.HTTP_REST);
        ServiceDependency downDep = buildDependency(downSource, service, DependencyType.KAFKA_TOPIC);

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        when(portAllocationRepository.findByServiceIdAndEnvironment(SERVICE_ID, ENVIRONMENT))
                .thenReturn(Collections.emptyList());
        when(portAllocationRepository.findByServiceIdAndEnvironment(upTarget.getId(), ENVIRONMENT))
                .thenReturn(Collections.emptyList());
        when(dependencyRepository.findBySourceServiceId(SERVICE_ID)).thenReturn(List.of(upDep));
        when(dependencyRepository.findByTargetServiceId(SERVICE_ID)).thenReturn(List.of(downDep));
        when(routeRepository.findByServiceId(SERVICE_ID)).thenReturn(Collections.emptyList());
        when(infraResourceRepository.findByServiceId(SERVICE_ID)).thenReturn(Collections.emptyList());
        when(environmentConfigRepository.findByServiceIdAndEnvironment(SERVICE_ID, ENVIRONMENT))
                .thenReturn(Collections.emptyList());
        when(configTemplateRepository.findByServiceIdAndTemplateTypeAndEnvironment(
                SERVICE_ID, ConfigTemplateType.CLAUDE_CODE_HEADER, ENVIRONMENT))
                .thenReturn(Optional.empty());
        setupSaveMock();

        ConfigTemplateResponse response = configEngineService.generateClaudeCodeHeader(SERVICE_ID, ENVIRONMENT);

        String content = response.contentText();
        assertThat(content).contains("Upstream (upstream)");
        assertThat(content).contains("[HTTP_REST]");
        assertThat(content).contains("Downstream (downstream)");
        assertThat(content).contains("[KAFKA_TOPIC]");
    }

    @Test
    void generateClaudeCodeHeader_serviceNotFound() {
        UUID missingId = UUID.randomUUID();
        when(serviceRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> configEngineService.generateClaudeCodeHeader(missingId, ENVIRONMENT))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("ServiceRegistration");
    }

    @Test
    void generateClaudeCodeHeader_upsert_incrementsVersion() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");
        ConfigTemplate existing = buildConfigTemplate(service, ConfigTemplateType.CLAUDE_CODE_HEADER,
                ENVIRONMENT, "old", 2);

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        setupEmptyRepoDefaults(SERVICE_ID);
        when(configTemplateRepository.findByServiceIdAndTemplateTypeAndEnvironment(
                SERVICE_ID, ConfigTemplateType.CLAUDE_CODE_HEADER, ENVIRONMENT))
                .thenReturn(Optional.of(existing));
        setupSaveMock();

        ConfigTemplateResponse response = configEngineService.generateClaudeCodeHeader(SERVICE_ID, ENVIRONMENT);

        assertThat(response.version()).isEqualTo(3);
    }

    @Test
    void generateClaudeCodeHeader_noPorts() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        setupEmptyRepoDefaults(SERVICE_ID);
        setupSaveMock();

        ConfigTemplateResponse response = configEngineService.generateClaudeCodeHeader(SERVICE_ID, ENVIRONMENT);

        assertThat(response.contentText()).contains("# Ports (local):\n#   None");
    }

    @Test
    void generateClaudeCodeHeader_noConfigs() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        setupEmptyRepoDefaults(SERVICE_ID);
        setupSaveMock();

        ConfigTemplateResponse response = configEngineService.generateClaudeCodeHeader(SERVICE_ID, ENVIRONMENT);

        assertThat(response.contentText()).contains("# Environment Config Keys:\n#   None");
    }

    @Test
    void generateClaudeCodeHeader_repoUrlNull_showsNA() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");
        service.setRepoUrl(null);
        service.setTechStack(null);

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        setupEmptyRepoDefaults(SERVICE_ID);
        setupSaveMock();

        ConfigTemplateResponse response = configEngineService.generateClaudeCodeHeader(SERVICE_ID, ENVIRONMENT);

        assertThat(response.contentText()).contains("# Repo: N/A");
        assertThat(response.contentText()).contains("# Tech Stack: N/A");
    }

    // ──────────────────────────────────────────────────────────────
    // generateAllForService tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void generateAllForService_returnsThreeTemplates() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        setupEmptyRepoDefaults(SERVICE_ID);
        setupSaveMock();

        List<ConfigTemplateResponse> results = configEngineService
                .generateAllForService(SERVICE_ID, ENVIRONMENT);

        assertThat(results).hasSize(3);
        assertThat(results).extracting(ConfigTemplateResponse::templateType)
                .containsExactly(
                        ConfigTemplateType.DOCKER_COMPOSE,
                        ConfigTemplateType.APPLICATION_YML,
                        ConfigTemplateType.CLAUDE_CODE_HEADER);
    }

    @Test
    void generateAllForService_oneGenerationFails_othersSucceed() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        setupEmptyRepoDefaults(SERVICE_ID);

        // Make save succeed, then fail, then succeed
        when(configTemplateRepository.save(any(ConfigTemplate.class)))
                .thenAnswer(invocation -> {
                    ConfigTemplate saved = invocation.getArgument(0);
                    if (saved.getId() == null) saved.setId(UUID.randomUUID());
                    if (saved.getCreatedAt() == null) saved.setCreatedAt(Instant.now());
                    saved.setUpdatedAt(Instant.now());
                    return saved;
                })
                .thenThrow(new RuntimeException("Database error"))
                .thenAnswer(invocation -> {
                    ConfigTemplate saved = invocation.getArgument(0);
                    if (saved.getId() == null) saved.setId(UUID.randomUUID());
                    if (saved.getCreatedAt() == null) saved.setCreatedAt(Instant.now());
                    saved.setUpdatedAt(Instant.now());
                    return saved;
                });

        List<ConfigTemplateResponse> results = configEngineService
                .generateAllForService(SERVICE_ID, ENVIRONMENT);

        assertThat(results).hasSize(2);
    }

    @Test
    void generateAllForService_serviceNotFound() {
        UUID missingId = UUID.randomUUID();
        when(serviceRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> configEngineService.generateAllForService(missingId, ENVIRONMENT))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("ServiceRegistration");
    }

    @Test
    void generateAllForService_allFail_returnsEmptyList() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        setupEmptyRepoDefaults(SERVICE_ID);
        when(configTemplateRepository.save(any(ConfigTemplate.class)))
                .thenThrow(new RuntimeException("Database unavailable"));

        List<ConfigTemplateResponse> results = configEngineService
                .generateAllForService(SERVICE_ID, ENVIRONMENT);

        assertThat(results).isEmpty();
    }

    @Test
    void generateAllForService_partialSuccess() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");

        when(serviceRepository.findById(SERVICE_ID)).thenReturn(Optional.of(service));
        setupEmptyRepoDefaults(SERVICE_ID);

        // First two succeed, third fails
        when(configTemplateRepository.save(any(ConfigTemplate.class)))
                .thenAnswer(invocation -> {
                    ConfigTemplate saved = invocation.getArgument(0);
                    if (saved.getId() == null) saved.setId(UUID.randomUUID());
                    if (saved.getCreatedAt() == null) saved.setCreatedAt(Instant.now());
                    saved.setUpdatedAt(Instant.now());
                    return saved;
                })
                .thenAnswer(invocation -> {
                    ConfigTemplate saved = invocation.getArgument(0);
                    if (saved.getId() == null) saved.setId(UUID.randomUUID());
                    if (saved.getCreatedAt() == null) saved.setCreatedAt(Instant.now());
                    saved.setUpdatedAt(Instant.now());
                    return saved;
                })
                .thenThrow(new RuntimeException("Database error"));

        List<ConfigTemplateResponse> results = configEngineService
                .generateAllForService(SERVICE_ID, ENVIRONMENT);

        assertThat(results).hasSize(2);
    }

    // ──────────────────────────────────────────────────────────────
    // generateSolutionDockerCompose tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void generateSolutionDockerCompose_success_multipleServices() {
        Solution solution = buildSolution(SOLUTION_ID, TEAM_ID, "My Platform");
        UUID svc1Id = UUID.randomUUID();
        UUID svc2Id = UUID.randomUUID();
        UUID svc3Id = UUID.randomUUID();
        ServiceRegistration svc1 = buildService(svc1Id, TEAM_ID, "Frontend", "frontend");
        ServiceRegistration svc2 = buildService(svc2Id, TEAM_ID, "Backend", "backend");
        ServiceRegistration svc3 = buildService(svc3Id, TEAM_ID, "Database", "database");

        SolutionMember m1 = buildMember(solution, svc1, 0);
        SolutionMember m2 = buildMember(solution, svc2, 1);
        SolutionMember m3 = buildMember(solution, svc3, 2);

        when(solutionRepository.findById(SOLUTION_ID)).thenReturn(Optional.of(solution));
        when(solutionMemberRepository.findBySolutionIdOrderByDisplayOrderAsc(SOLUTION_ID))
                .thenReturn(List.of(m1, m2, m3));
        when(dependencyGraphService.getStartupOrder(TEAM_ID)).thenReturn(List.of(
                new DependencyNodeResponse(svc3Id, "Database", "database",
                        ServiceType.DATABASE_SERVICE, ServiceStatus.ACTIVE, HealthStatus.UNKNOWN),
                new DependencyNodeResponse(svc2Id, "Backend", "backend",
                        ServiceType.SPRING_BOOT_API, ServiceStatus.ACTIVE, HealthStatus.UNKNOWN),
                new DependencyNodeResponse(svc1Id, "Frontend", "frontend",
                        ServiceType.REACT_SPA, ServiceStatus.ACTIVE, HealthStatus.UNKNOWN)
        ));

        for (UUID id : List.of(svc1Id, svc2Id, svc3Id)) {
            when(portAllocationRepository.findByServiceIdAndEnvironment(id, ENVIRONMENT))
                    .thenReturn(Collections.emptyList());
            when(environmentConfigRepository.findByServiceIdAndEnvironment(id, ENVIRONMENT))
                    .thenReturn(Collections.emptyList());
            when(dependencyRepository.findBySourceServiceId(id)).thenReturn(Collections.emptyList());
            when(infraResourceRepository.findByServiceId(id)).thenReturn(Collections.emptyList());
        }
        when(configTemplateRepository.findByServiceIdAndTemplateTypeAndEnvironment(
                any(UUID.class), any(ConfigTemplateType.class), any(String.class)))
                .thenReturn(Optional.empty());
        setupSaveMock();

        ConfigTemplateResponse response = configEngineService
                .generateSolutionDockerCompose(SOLUTION_ID, ENVIRONMENT);

        assertThat(response).isNotNull();
        assertThat(response.templateType()).isEqualTo(ConfigTemplateType.DOCKER_COMPOSE);
        assertThat(response.generatedFrom()).isEqualTo("solution:" + SOLUTION_ID);

        String content = response.contentText();
        assertThat(content).contains("frontend:");
        assertThat(content).contains("backend:");
        assertThat(content).contains("database:");
        assertThat(content).contains("codeops-network");
    }

    @Test
    void generateSolutionDockerCompose_startupOrderRespected() {
        Solution solution = buildSolution(SOLUTION_ID, TEAM_ID, "My Platform");
        UUID svc1Id = UUID.randomUUID();
        UUID svc2Id = UUID.randomUUID();
        ServiceRegistration svc1 = buildService(svc1Id, TEAM_ID, "App", "app");
        ServiceRegistration svc2 = buildService(svc2Id, TEAM_ID, "DB", "db");

        SolutionMember m1 = buildMember(solution, svc1, 0);
        SolutionMember m2 = buildMember(solution, svc2, 1);

        when(solutionRepository.findById(SOLUTION_ID)).thenReturn(Optional.of(solution));
        when(solutionMemberRepository.findBySolutionIdOrderByDisplayOrderAsc(SOLUTION_ID))
                .thenReturn(List.of(m1, m2));
        // Startup order: DB first, then App
        when(dependencyGraphService.getStartupOrder(TEAM_ID)).thenReturn(List.of(
                new DependencyNodeResponse(svc2Id, "DB", "db",
                        ServiceType.DATABASE_SERVICE, ServiceStatus.ACTIVE, HealthStatus.UNKNOWN),
                new DependencyNodeResponse(svc1Id, "App", "app",
                        ServiceType.SPRING_BOOT_API, ServiceStatus.ACTIVE, HealthStatus.UNKNOWN)
        ));

        for (UUID id : List.of(svc1Id, svc2Id)) {
            when(portAllocationRepository.findByServiceIdAndEnvironment(id, ENVIRONMENT))
                    .thenReturn(Collections.emptyList());
            when(environmentConfigRepository.findByServiceIdAndEnvironment(id, ENVIRONMENT))
                    .thenReturn(Collections.emptyList());
            when(dependencyRepository.findBySourceServiceId(id)).thenReturn(Collections.emptyList());
            when(infraResourceRepository.findByServiceId(id)).thenReturn(Collections.emptyList());
        }
        when(configTemplateRepository.findByServiceIdAndTemplateTypeAndEnvironment(
                any(UUID.class), any(ConfigTemplateType.class), any(String.class)))
                .thenReturn(Optional.empty());
        setupSaveMock();

        ConfigTemplateResponse response = configEngineService
                .generateSolutionDockerCompose(SOLUTION_ID, ENVIRONMENT);

        String content = response.contentText();
        // DB should appear before App in the YAML (startup order)
        int dbIndex = content.indexOf("db:");
        int appIndex = content.indexOf("app:");
        assertThat(dbIndex).isLessThan(appIndex);
    }

    @Test
    void generateSolutionDockerCompose_sharedNetworkAndVolumes() {
        Solution solution = buildSolution(SOLUTION_ID, TEAM_ID, "My Platform");
        UUID svc1Id = UUID.randomUUID();
        ServiceRegistration svc1 = buildService(svc1Id, TEAM_ID, "App", "app");
        SolutionMember m1 = buildMember(solution, svc1, 0);

        InfraResource volume = buildInfraResource(svc1, InfraResourceType.DOCKER_VOLUME,
                "shared-data", ENVIRONMENT);

        when(solutionRepository.findById(SOLUTION_ID)).thenReturn(Optional.of(solution));
        when(solutionMemberRepository.findBySolutionIdOrderByDisplayOrderAsc(SOLUTION_ID))
                .thenReturn(List.of(m1));
        when(dependencyGraphService.getStartupOrder(TEAM_ID)).thenReturn(List.of(
                new DependencyNodeResponse(svc1Id, "App", "app",
                        ServiceType.SPRING_BOOT_API, ServiceStatus.ACTIVE, HealthStatus.UNKNOWN)
        ));
        when(portAllocationRepository.findByServiceIdAndEnvironment(svc1Id, ENVIRONMENT))
                .thenReturn(Collections.emptyList());
        when(environmentConfigRepository.findByServiceIdAndEnvironment(svc1Id, ENVIRONMENT))
                .thenReturn(Collections.emptyList());
        when(dependencyRepository.findBySourceServiceId(svc1Id)).thenReturn(Collections.emptyList());
        when(infraResourceRepository.findByServiceId(svc1Id)).thenReturn(List.of(volume));
        when(configTemplateRepository.findByServiceIdAndTemplateTypeAndEnvironment(
                any(UUID.class), any(ConfigTemplateType.class), any(String.class)))
                .thenReturn(Optional.empty());
        setupSaveMock();

        ConfigTemplateResponse response = configEngineService
                .generateSolutionDockerCompose(SOLUTION_ID, ENVIRONMENT);

        String content = response.contentText();
        assertThat(content).contains("codeops-network");
        assertThat(content).contains("driver: bridge");
        assertThat(content).contains("volumes:");
        assertThat(content).contains("shared-data");
    }

    @Test
    void generateSolutionDockerCompose_solutionNotFound() {
        UUID missingId = UUID.randomUUID();
        when(solutionRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> configEngineService.generateSolutionDockerCompose(missingId, ENVIRONMENT))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Solution");

        verify(configTemplateRepository, never()).save(any());
    }

    @Test
    void generateSolutionDockerCompose_noMembers_throwsValidation() {
        Solution solution = buildSolution(SOLUTION_ID, TEAM_ID, "Empty Solution");

        when(solutionRepository.findById(SOLUTION_ID)).thenReturn(Optional.of(solution));
        when(solutionMemberRepository.findBySolutionIdOrderByDisplayOrderAsc(SOLUTION_ID))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> configEngineService.generateSolutionDockerCompose(SOLUTION_ID, ENVIRONMENT))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("has no members");

        verify(configTemplateRepository, never()).save(any());
    }

    @Test
    void generateSolutionDockerCompose_generatedFromContainsSolutionId() {
        Solution solution = buildSolution(SOLUTION_ID, TEAM_ID, "My Platform");
        UUID svcId = UUID.randomUUID();
        ServiceRegistration svc = buildService(svcId, TEAM_ID, "App", "app");
        SolutionMember m = buildMember(solution, svc, 0);

        when(solutionRepository.findById(SOLUTION_ID)).thenReturn(Optional.of(solution));
        when(solutionMemberRepository.findBySolutionIdOrderByDisplayOrderAsc(SOLUTION_ID))
                .thenReturn(List.of(m));
        when(dependencyGraphService.getStartupOrder(TEAM_ID)).thenReturn(List.of(
                new DependencyNodeResponse(svcId, "App", "app",
                        ServiceType.SPRING_BOOT_API, ServiceStatus.ACTIVE, HealthStatus.UNKNOWN)
        ));
        when(portAllocationRepository.findByServiceIdAndEnvironment(svcId, ENVIRONMENT))
                .thenReturn(Collections.emptyList());
        when(environmentConfigRepository.findByServiceIdAndEnvironment(svcId, ENVIRONMENT))
                .thenReturn(Collections.emptyList());
        when(dependencyRepository.findBySourceServiceId(svcId)).thenReturn(Collections.emptyList());
        when(infraResourceRepository.findByServiceId(svcId)).thenReturn(Collections.emptyList());
        when(configTemplateRepository.findByServiceIdAndTemplateTypeAndEnvironment(
                any(UUID.class), any(ConfigTemplateType.class), any(String.class)))
                .thenReturn(Optional.empty());
        setupSaveMock();

        ConfigTemplateResponse response = configEngineService
                .generateSolutionDockerCompose(SOLUTION_ID, ENVIRONMENT);

        assertThat(response.generatedFrom()).isEqualTo("solution:" + SOLUTION_ID);
    }

    // ──────────────────────────────────────────────────────────────
    // getTemplate tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void getTemplate_success() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");
        ConfigTemplate template = buildConfigTemplate(service, ConfigTemplateType.DOCKER_COMPOSE,
                ENVIRONMENT, "version: '3.8'", 2);

        when(configTemplateRepository.findByServiceIdAndTemplateTypeAndEnvironment(
                SERVICE_ID, ConfigTemplateType.DOCKER_COMPOSE, ENVIRONMENT))
                .thenReturn(Optional.of(template));

        ConfigTemplateResponse response = configEngineService
                .getTemplate(SERVICE_ID, ConfigTemplateType.DOCKER_COMPOSE, ENVIRONMENT);

        assertThat(response).isNotNull();
        assertThat(response.templateType()).isEqualTo(ConfigTemplateType.DOCKER_COMPOSE);
        assertThat(response.contentText()).isEqualTo("version: '3.8'");
        assertThat(response.version()).isEqualTo(2);
        assertThat(response.serviceName()).isEqualTo("My App");
    }

    @Test
    void getTemplate_notFound() {
        when(configTemplateRepository.findByServiceIdAndTemplateTypeAndEnvironment(
                SERVICE_ID, ConfigTemplateType.APPLICATION_YML, ENVIRONMENT))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> configEngineService
                .getTemplate(SERVICE_ID, ConfigTemplateType.APPLICATION_YML, ENVIRONMENT))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("ConfigTemplate");
    }

    // ──────────────────────────────────────────────────────────────
    // getTemplatesForService tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void getTemplatesForService_returnsList() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");
        ConfigTemplate t1 = buildConfigTemplate(service, ConfigTemplateType.DOCKER_COMPOSE,
                ENVIRONMENT, "content1", 1);
        ConfigTemplate t2 = buildConfigTemplate(service, ConfigTemplateType.APPLICATION_YML,
                ENVIRONMENT, "content2", 1);

        when(configTemplateRepository.findByServiceId(SERVICE_ID)).thenReturn(List.of(t1, t2));

        List<ConfigTemplateResponse> results = configEngineService.getTemplatesForService(SERVICE_ID);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(ConfigTemplateResponse::templateType)
                .containsExactly(ConfigTemplateType.DOCKER_COMPOSE, ConfigTemplateType.APPLICATION_YML);
    }

    @Test
    void getTemplatesForService_empty() {
        when(configTemplateRepository.findByServiceId(SERVICE_ID)).thenReturn(Collections.emptyList());

        List<ConfigTemplateResponse> results = configEngineService.getTemplatesForService(SERVICE_ID);

        assertThat(results).isEmpty();
    }

    // ──────────────────────────────────────────────────────────────
    // deleteTemplate tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void deleteTemplate_success() {
        ServiceRegistration service = buildService(SERVICE_ID, TEAM_ID, "My App", "my-app");
        ConfigTemplate template = buildConfigTemplate(service, ConfigTemplateType.DOCKER_COMPOSE,
                ENVIRONMENT, "content", 1);

        when(configTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));

        configEngineService.deleteTemplate(TEMPLATE_ID);

        verify(configTemplateRepository).delete(template);
    }

    @Test
    void deleteTemplate_notFound() {
        UUID missingId = UUID.randomUUID();
        when(configTemplateRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> configEngineService.deleteTemplate(missingId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("ConfigTemplate")
                .hasMessageContaining(missingId.toString());

        verify(configTemplateRepository, never()).delete(any(ConfigTemplate.class));
    }
}
