package com.codeops.registry.config;

import com.codeops.registry.entity.*;
import com.codeops.registry.entity.enums.*;
import com.codeops.registry.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Seeds the database with realistic sample data representing the CodeOps ecosystem.
 *
 * <p>Runs only in the {@code dev} profile and is idempotent: if any services
 * already exist, seeding is skipped entirely. Uses direct repository access
 * (not services) to bypass validation limits that don't apply during seeding.</p>
 *
 * <p>The seed data models the real CodeOps platform architecture with 9 services,
 * their dependencies, 3 solutions, port ranges and allocations, API routes,
 * workstation profiles, environment configs, and infrastructure resources.</p>
 *
 * @see AppConstants#SEED_TEAM_ID
 * @see AppConstants#SEED_USER_ID
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Profile("dev")
public class DataSeeder implements CommandLineRunner {

    private final ServiceRegistrationRepository serviceRegistrationRepository;
    private final ServiceDependencyRepository serviceDependencyRepository;
    private final SolutionRepository solutionRepository;
    private final SolutionMemberRepository solutionMemberRepository;
    private final PortAllocationRepository portAllocationRepository;
    private final PortRangeRepository portRangeRepository;
    private final ApiRouteRegistrationRepository apiRouteRegistrationRepository;
    private final EnvironmentConfigRepository environmentConfigRepository;
    private final WorkstationProfileRepository workstationProfileRepository;
    private final InfraResourceRepository infraResourceRepository;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final UUID TEAM_ID = AppConstants.SEED_TEAM_ID;
    private static final UUID USER_ID = AppConstants.SEED_USER_ID;

    /**
     * Seeds the database if no services exist yet.
     *
     * @param args command-line arguments (unused)
     */
    @Override
    @Transactional
    public void run(String... args) {
        if (serviceRegistrationRepository.count() > 0) {
            log.info("Data already seeded — skipping");
            return;
        }
        log.info("Seeding CodeOps Registry with sample data...");
        seedData();
        log.info("Seeding complete");
    }

    /**
     * Orchestrates all seed operations in dependency order.
     */
    private void seedData() {
        Map<String, ServiceRegistration> services = seedServices();
        seedDependencies(services);
        Map<String, Solution> solutions = seedSolutions();
        seedSolutionMembers(solutions, services);
        seedPortRanges();
        seedPortAllocations(services);
        seedApiRoutes(services);
        seedEnvironmentConfigs(services);
        seedWorkstationProfiles(services);
        seedInfraResources(services);
    }

    /**
     * Seeds 9 services matching the real CodeOps platform architecture.
     *
     * @return a map of service slug to persisted entity
     */
    private Map<String, ServiceRegistration> seedServices() {
        List<ServiceRegistration> services = List.of(
                buildService("CodeOps Server", "codeops-server", ServiceType.SPRING_BOOT_API,
                        "Core authentication and team management server",
                        "https://github.com/aallard/CodeOps-Server",
                        "aallard/CodeOps-Server", "Java, Spring Boot 3.3, PostgreSQL, Redis, Kafka",
                        "http://localhost:8090/api/v1/health"),
                buildService("CodeOps Registry", "codeops-registry", ServiceType.SPRING_BOOT_API,
                        "Service registry and development control plane",
                        "https://github.com/aallard/CodeOps-Registry",
                        "aallard/CodeOps-Registry", "Java, Spring Boot 3.3, PostgreSQL",
                        "http://localhost:8096/api/v1/health"),
                buildService("CodeOps Vault", "codeops-vault", ServiceType.SPRING_BOOT_API,
                        "Secrets and credential management service",
                        "https://github.com/aallard/CodeOps-Vault",
                        "aallard/CodeOps-Vault", "Java, Spring Boot 3.3, PostgreSQL",
                        "http://localhost:8097/api/v1/health"),
                buildService("CodeOps Logger", "codeops-logger", ServiceType.SPRING_BOOT_API,
                        "Centralized logging and audit trail service",
                        "https://github.com/aallard/CodeOps-Logger",
                        "aallard/CodeOps-Logger", "Java, Spring Boot 3.3, PostgreSQL",
                        "http://localhost:8098/api/v1/health"),
                buildService("CodeOps Courier", "codeops-courier", ServiceType.SPRING_BOOT_API,
                        "Notification and messaging delivery service",
                        "https://github.com/aallard/CodeOps-Courier",
                        "aallard/CodeOps-Courier", "Java, Spring Boot 3.3, PostgreSQL",
                        "http://localhost:8099/api/v1/health"),
                buildService("CodeOps DataLens", "codeops-datalens", ServiceType.SPRING_BOOT_API,
                        "Analytics and data visualization service",
                        "https://github.com/aallard/CodeOps-DataLens",
                        "aallard/CodeOps-DataLens", "Java, Spring Boot 3.3, PostgreSQL",
                        "http://localhost:8100/api/v1/health"),
                buildService("CodeOps Client", "codeops-client", ServiceType.REACT_SPA,
                        "Primary web application for the CodeOps platform",
                        "https://github.com/aallard/CodeOps-Client",
                        "aallard/CodeOps-Client", "React, TypeScript, Vite",
                        "http://localhost:5173"),
                buildService("CodeOps Scribe", "codeops-scribe", ServiceType.REACT_SPA,
                        "Documentation and knowledge base module",
                        "https://github.com/aallard/CodeOps-Scribe",
                        "aallard/CodeOps-Scribe", "React, TypeScript",
                        null),
                buildService("CodeOps Gateway", "codeops-gateway", ServiceType.GATEWAY,
                        "API gateway for routing and load balancing",
                        "https://github.com/aallard/CodeOps-Gateway",
                        "aallard/CodeOps-Gateway", "Spring Cloud Gateway",
                        "http://localhost:8080/actuator/health")
        );

        List<ServiceRegistration> saved = serviceRegistrationRepository.saveAll(services);
        log.info("Seeded {} services", saved.size());
        return saved.stream().collect(Collectors.toMap(ServiceRegistration::getSlug, s -> s));
    }

    /**
     * Seeds 13 service-to-service dependencies.
     *
     * @param services map of slug to service entity
     */
    private void seedDependencies(Map<String, ServiceRegistration> services) {
        ServiceRegistration server = services.get("codeops-server");
        ServiceRegistration registry = services.get("codeops-registry");
        ServiceRegistration vault = services.get("codeops-vault");
        ServiceRegistration logger = services.get("codeops-logger");
        ServiceRegistration courier = services.get("codeops-courier");
        ServiceRegistration datalens = services.get("codeops-datalens");
        ServiceRegistration client = services.get("codeops-client");
        ServiceRegistration gateway = services.get("codeops-gateway");

        List<ServiceDependency> deps = List.of(
                buildDep(client, server, DependencyType.HTTP_REST, "/api/v1/auth"),
                buildDep(client, registry, DependencyType.HTTP_REST, "/api/v1/registry"),
                buildDep(client, vault, DependencyType.HTTP_REST, "/api/v1/vault"),
                buildDep(client, logger, DependencyType.HTTP_REST, "/api/v1/logs"),
                buildDep(client, courier, DependencyType.HTTP_REST, "/api/v1/courier"),
                buildDep(client, datalens, DependencyType.HTTP_REST, "/api/v1/datalens"),
                buildDep(registry, server, DependencyType.HTTP_REST, "/api/v1/auth"),
                buildDep(vault, server, DependencyType.HTTP_REST, "/api/v1/auth"),
                buildDep(logger, server, DependencyType.HTTP_REST, "/api/v1/auth"),
                buildDep(courier, server, DependencyType.HTTP_REST, "/api/v1/auth"),
                buildDep(datalens, server, DependencyType.HTTP_REST, "/api/v1/auth"),
                buildDep(gateway, server, DependencyType.HTTP_REST, "/api/v1/auth"),
                buildDep(gateway, registry, DependencyType.HTTP_REST, "/api/v1/registry")
        );

        serviceDependencyRepository.saveAll(deps);
        log.info("Seeded {} dependencies", deps.size());
    }

    /**
     * Seeds 3 solutions for the CodeOps platform.
     *
     * @return a map of solution slug to persisted entity
     */
    private Map<String, Solution> seedSolutions() {
        List<Solution> solutions = List.of(
                Solution.builder()
                        .teamId(TEAM_ID).name("CodeOps Control Plane").slug("codeops-control-plane")
                        .description("Core platform services powering the CodeOps ecosystem")
                        .category(SolutionCategory.PLATFORM).status(SolutionStatus.ACTIVE)
                        .iconName("dashboard").colorHex("#2196F3")
                        .ownerUserId(USER_ID).createdByUserId(USER_ID).build(),
                Solution.builder()
                        .teamId(TEAM_ID).name("CodeOps Infrastructure").slug("codeops-infrastructure")
                        .description("Infrastructure and routing layer for the platform")
                        .category(SolutionCategory.INFRASTRUCTURE).status(SolutionStatus.ACTIVE)
                        .iconName("cloud").colorHex("#FF9800")
                        .ownerUserId(USER_ID).createdByUserId(USER_ID).build(),
                Solution.builder()
                        .teamId(TEAM_ID).name("CodeOps Developer Tools").slug("codeops-developer-tools")
                        .description("Developer productivity and communication tools")
                        .category(SolutionCategory.TOOLING).status(SolutionStatus.ACTIVE)
                        .iconName("build").colorHex("#4CAF50")
                        .ownerUserId(USER_ID).createdByUserId(USER_ID).build()
        );

        List<Solution> saved = solutionRepository.saveAll(solutions);
        log.info("Seeded {} solutions", saved.size());
        return saved.stream().collect(Collectors.toMap(Solution::getSlug, s -> s));
    }

    /**
     * Seeds solution membership assignments.
     *
     * @param solutions map of solution slug to entity
     * @param services  map of service slug to entity
     */
    private void seedSolutionMembers(Map<String, Solution> solutions,
                                     Map<String, ServiceRegistration> services) {
        Solution controlPlane = solutions.get("codeops-control-plane");
        Solution infra = solutions.get("codeops-infrastructure");
        Solution devTools = solutions.get("codeops-developer-tools");

        List<SolutionMember> members = List.of(
                buildMember(controlPlane, services.get("codeops-server"), SolutionMemberRole.CORE, 0),
                buildMember(controlPlane, services.get("codeops-registry"), SolutionMemberRole.CORE, 1),
                buildMember(controlPlane, services.get("codeops-vault"), SolutionMemberRole.CORE, 2),
                buildMember(controlPlane, services.get("codeops-logger"), SolutionMemberRole.SUPPORTING, 3),
                buildMember(controlPlane, services.get("codeops-courier"), SolutionMemberRole.SUPPORTING, 4),
                buildMember(controlPlane, services.get("codeops-datalens"), SolutionMemberRole.SUPPORTING, 5),
                buildMember(controlPlane, services.get("codeops-client"), SolutionMemberRole.CORE, 6),

                buildMember(infra, services.get("codeops-server"), SolutionMemberRole.CORE, 0),
                buildMember(infra, services.get("codeops-gateway"), SolutionMemberRole.CORE, 1),

                buildMember(devTools, services.get("codeops-courier"), SolutionMemberRole.CORE, 0),
                buildMember(devTools, services.get("codeops-datalens"), SolutionMemberRole.CORE, 1),
                buildMember(devTools, services.get("codeops-scribe"), SolutionMemberRole.CORE, 2)
        );

        solutionMemberRepository.saveAll(members);
        log.info("Seeded {} solution members", members.size());
    }

    /**
     * Seeds port ranges for HTTP_API, FRONTEND_DEV, and DATABASE types.
     */
    private void seedPortRanges() {
        List<PortRange> ranges = List.of(
                PortRange.builder()
                        .teamId(TEAM_ID).portType(PortType.HTTP_API)
                        .rangeStart(8080).rangeEnd(8199).environment("local")
                        .description("HTTP API ports for backend services").build(),
                PortRange.builder()
                        .teamId(TEAM_ID).portType(PortType.FRONTEND_DEV)
                        .rangeStart(5170).rangeEnd(5199).environment("local")
                        .description("Frontend development server ports").build(),
                PortRange.builder()
                        .teamId(TEAM_ID).portType(PortType.DATABASE)
                        .rangeStart(5430).rangeEnd(5499).environment("local")
                        .description("Database ports for PostgreSQL instances").build()
        );

        portRangeRepository.saveAll(ranges);
        log.info("Seeded {} port ranges", ranges.size());
    }

    /**
     * Seeds port allocations for all services.
     *
     * @param services map of slug to service entity
     */
    private void seedPortAllocations(Map<String, ServiceRegistration> services) {
        List<PortAllocation> allocations = new ArrayList<>();

        allocations.addAll(buildPorts(services.get("codeops-server"), 8090, 5432, null));
        allocations.addAll(buildPorts(services.get("codeops-registry"), 8096, 5435, null));
        allocations.addAll(buildPorts(services.get("codeops-vault"), 8097, 5436, null));
        allocations.addAll(buildPorts(services.get("codeops-logger"), 8098, 5437, null));
        allocations.addAll(buildPorts(services.get("codeops-courier"), 8099, 5438, null));
        allocations.addAll(buildPorts(services.get("codeops-datalens"), 8100, 5439, null));
        allocations.addAll(buildPorts(services.get("codeops-client"), null, null, 5173));
        allocations.addAll(buildPorts(services.get("codeops-scribe"), null, null, 5173));
        allocations.addAll(buildPorts(services.get("codeops-gateway"), 8080, null, null));

        portAllocationRepository.saveAll(allocations);
        log.info("Seeded {} port allocations", allocations.size());
    }

    /**
     * Seeds API route registrations for direct service access.
     *
     * @param services map of slug to service entity
     */
    private void seedApiRoutes(Map<String, ServiceRegistration> services) {
        List<ApiRouteRegistration> routes = List.of(
                buildRoute(services.get("codeops-server"), "/api/v1/auth", "Authentication and team management"),
                buildRoute(services.get("codeops-registry"), "/api/v1/registry", "Service registry CRUD"),
                buildRoute(services.get("codeops-vault"), "/api/v1/vault", "Secrets management"),
                buildRoute(services.get("codeops-logger"), "/api/v1/logs", "Logging and audit trail"),
                buildRoute(services.get("codeops-courier"), "/api/v1/courier", "Notification delivery"),
                buildRoute(services.get("codeops-datalens"), "/api/v1/datalens", "Analytics and reporting")
        );

        apiRouteRegistrationRepository.saveAll(routes);
        log.info("Seeded {} API routes", routes.size());
    }

    /**
     * Seeds environment configuration for the Registry service as an example.
     *
     * @param services map of slug to service entity
     */
    private void seedEnvironmentConfigs(Map<String, ServiceRegistration> services) {
        ServiceRegistration registry = services.get("codeops-registry");

        List<EnvironmentConfig> configs = List.of(
                buildConfig(registry, "spring.datasource.url",
                        "jdbc:postgresql://localhost:5435/codeops_registry", "JDBC connection URL"),
                buildConfig(registry, "spring.datasource.username", "postgres", "Database username"),
                buildConfig(registry, "spring.datasource.password", "postgres", "Database password"),
                buildConfig(registry, "spring.jpa.hibernate.ddl-auto", "update", "Hibernate schema strategy"),
                buildConfig(registry, "codeops.jwt.secret",
                        "dev-secret-key-minimum-32-characters-long-for-hs256", "JWT shared secret (dev default)")
        );

        environmentConfigRepository.saveAll(configs);
        log.info("Seeded {} environment configs", configs.size());
    }

    /**
     * Seeds 3 workstation profiles with startup order computed from dependencies.
     *
     * @param services map of slug to service entity
     */
    private void seedWorkstationProfiles(Map<String, ServiceRegistration> services) {
        // Startup order: Server first (no deps), then backend services, then frontend
        List<UUID> allIds = List.of(
                services.get("codeops-server").getId(),
                services.get("codeops-gateway").getId(),
                services.get("codeops-registry").getId(),
                services.get("codeops-vault").getId(),
                services.get("codeops-logger").getId(),
                services.get("codeops-courier").getId(),
                services.get("codeops-datalens").getId(),
                services.get("codeops-scribe").getId(),
                services.get("codeops-client").getId()
        );

        List<UUID> backendIds = List.of(
                services.get("codeops-server").getId(),
                services.get("codeops-registry").getId(),
                services.get("codeops-vault").getId(),
                services.get("codeops-logger").getId(),
                services.get("codeops-courier").getId(),
                services.get("codeops-datalens").getId()
        );

        List<UUID> registryDevIds = List.of(
                services.get("codeops-server").getId(),
                services.get("codeops-registry").getId(),
                services.get("codeops-client").getId()
        );

        List<WorkstationProfile> profiles = List.of(
                WorkstationProfile.builder()
                        .teamId(TEAM_ID).name("Full Platform")
                        .description("All 9 services for full platform development")
                        .servicesJson(serializeUuids(allIds))
                        .startupOrder(serializeUuids(allIds))
                        .createdByUserId(USER_ID).isDefault(true).build(),
                WorkstationProfile.builder()
                        .teamId(TEAM_ID).name("Backend Only")
                        .description("Backend services without frontend")
                        .servicesJson(serializeUuids(backendIds))
                        .startupOrder(serializeUuids(backendIds))
                        .createdByUserId(USER_ID).isDefault(false).build(),
                WorkstationProfile.builder()
                        .teamId(TEAM_ID).name("Registry Dev")
                        .description("Minimal setup for Registry development")
                        .servicesJson(serializeUuids(registryDevIds))
                        .startupOrder(serializeUuids(registryDevIds))
                        .createdByUserId(USER_ID).isDefault(false).build()
        );

        workstationProfileRepository.saveAll(profiles);
        log.info("Seeded {} workstation profiles", profiles.size());
    }

    /**
     * Seeds 2 infrastructure resources for the platform.
     *
     * @param services map of slug to service entity
     */
    private void seedInfraResources(Map<String, ServiceRegistration> services) {
        List<InfraResource> resources = List.of(
                InfraResource.builder()
                        .teamId(TEAM_ID).resourceType(InfraResourceType.DOCKER_NETWORK)
                        .resourceName("codeops-network").environment("local")
                        .description("Shared Docker network for inter-service communication")
                        .createdByUserId(USER_ID).build(),
                InfraResource.builder()
                        .teamId(TEAM_ID).service(services.get("codeops-server"))
                        .resourceType(InfraResourceType.DOCKER_VOLUME)
                        .resourceName("codeops-pg-data").environment("local")
                        .description("Persistent volume for PostgreSQL data")
                        .createdByUserId(USER_ID).build()
        );

        infraResourceRepository.saveAll(resources);
        log.info("Seeded {} infrastructure resources", resources.size());
    }

    // ── Builder helpers ──

    private ServiceRegistration buildService(String name, String slug, ServiceType type,
                                             String description, String repoUrl,
                                             String repoFullName, String techStack,
                                             String healthCheckUrl) {
        return ServiceRegistration.builder()
                .teamId(TEAM_ID).name(name).slug(slug).serviceType(type)
                .description(description).repoUrl(repoUrl).repoFullName(repoFullName)
                .techStack(techStack).status(ServiceStatus.ACTIVE)
                .healthCheckUrl(healthCheckUrl).createdByUserId(USER_ID).build();
    }

    private ServiceDependency buildDep(ServiceRegistration source, ServiceRegistration target,
                                       DependencyType type, String endpoint) {
        return ServiceDependency.builder()
                .sourceService(source).targetService(target)
                .dependencyType(type).isRequired(true)
                .description(source.getName() + " depends on " + target.getName())
                .targetEndpoint(endpoint).build();
    }

    private SolutionMember buildMember(Solution solution, ServiceRegistration service,
                                       SolutionMemberRole role, int order) {
        return SolutionMember.builder()
                .solution(solution).service(service)
                .role(role).displayOrder(order).build();
    }

    private List<PortAllocation> buildPorts(ServiceRegistration service,
                                            Integer httpPort, Integer dbPort,
                                            Integer frontendPort) {
        List<PortAllocation> ports = new ArrayList<>();
        if (httpPort != null) {
            ports.add(PortAllocation.builder()
                    .service(service).environment("local").portType(PortType.HTTP_API)
                    .portNumber(httpPort).protocol("TCP").isAutoAllocated(false)
                    .allocatedByUserId(USER_ID).build());
        }
        if (dbPort != null) {
            ports.add(PortAllocation.builder()
                    .service(service).environment("local").portType(PortType.DATABASE)
                    .portNumber(dbPort).protocol("TCP").isAutoAllocated(false)
                    .allocatedByUserId(USER_ID).build());
        }
        if (frontendPort != null) {
            ports.add(PortAllocation.builder()
                    .service(service).environment("local").portType(PortType.FRONTEND_DEV)
                    .portNumber(frontendPort).protocol("TCP").isAutoAllocated(false)
                    .allocatedByUserId(USER_ID).build());
        }
        return ports;
    }

    private ApiRouteRegistration buildRoute(ServiceRegistration service, String prefix,
                                            String description) {
        return ApiRouteRegistration.builder()
                .service(service).routePrefix(prefix)
                .httpMethods("GET,POST,PUT,DELETE,PATCH").environment("local")
                .description(description).build();
    }

    private EnvironmentConfig buildConfig(ServiceRegistration service, String key,
                                          String value, String description) {
        return EnvironmentConfig.builder()
                .service(service).environment("local").configKey(key)
                .configValue(value).configSource(ConfigSource.MANUAL)
                .description(description).build();
    }

    private String serializeUuids(List<UUID> ids) {
        try {
            return MAPPER.writeValueAsString(ids);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize UUIDs", e);
        }
    }
}
