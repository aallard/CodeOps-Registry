# CodeOps-Registry — Codebase Audit

**Audit Date:** 2026-02-20T23:02:49Z
**Branch:** main
**Commit:** b92e9d1d9ea3053aaa3884dea999b9edee871fb6 Update
**Auditor:** Claude Code (Automated)
**Purpose:** Zero-context reference for AI-assisted development
**Audit File:** CodeOps-Registry-Audit.md
**Scorecard:** CodeOps-Registry-Scorecard.md
**OpenAPI Spec:** CodeOps-Registry-OpenAPI.yaml

> This audit is the single source of truth for the CodeOps-Registry codebase.
> The OpenAPI spec (CodeOps-Registry-OpenAPI.yaml) is the source of truth for all endpoints, DTOs, and API contracts.
> An AI reading this audit + the OpenAPI spec should be able to generate accurate code
> changes, new features, tests, and fixes without filesystem access.

---

## 1. Project Identity

```
Project Name:           CodeOps Registry
Repository URL:         https://github.com/aallard/CodeOps-Registry.git
Primary Language:       Java 21 / Spring Boot 3.3.0
Build Tool:             Maven (maven-wrapper)
Current Branch:         main
Latest Commit Hash:     b92e9d1d9ea3053aaa3884dea999b9edee871fb6
Latest Commit Message:  Update
Audit Timestamp:        2026-02-20T23:02:49Z
```

---

## 2. Directory Structure

Single-module Maven project. All source under `src/main/java/com/codeops/registry/`. Packages: `config`, `controller`, `dto/request`, `dto/response`, `entity`, `entity/enums`, `exception`, `repository`, `security`, `service`, `util`.

```
.
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── src/main/java/com/codeops/registry/
│   ├── CodeOpsRegistryApplication.java
│   ├── config/          (13 files: AppConstants, AsyncConfig, CorsConfig, DataSeeder, GlobalExceptionHandler, HealthController, JwtProperties, LoggingInterceptor, OpenApiConfig, RequestCorrelationFilter, RestTemplateConfig, ServiceUrlProperties, WebMvcConfig)
│   ├── controller/      (10 files: RegistryController, SolutionController, PortController, DependencyController, RouteController, ConfigController, HealthManagementController, InfraController, TopologyController, WorkstationController)
│   ├── dto/request/     (21 request DTOs — all Java records)
│   ├── dto/response/    (34 response DTOs — all Java records)
│   ├── entity/          (10 entities + BaseEntity)
│   ├── entity/enums/    (11 enums)
│   ├── exception/       (4 exceptions)
│   ├── repository/      (11 repositories)
│   ├── security/        (5 files: SecurityConfig, JwtAuthFilter, JwtTokenValidator, RateLimitFilter, SecurityUtils)
│   ├── service/         (10 services)
│   └── util/            (1 file: SlugUtils)
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   ├── application-prod.yml
│   ├── application-test.yml
│   ├── application-integration.yml
│   └── logback-spring.xml
└── src/test/            (26 unit test files, 1 integration base class)
```

---

## 3. Build & Dependency Manifest

**File:** `pom.xml` — `com.codeops:codeops-registry:0.1.0-SNAPSHOT`, parent `spring-boot-starter-parent:3.3.0`

| Dependency | Version | Purpose |
|---|---|---|
| spring-boot-starter-web | 3.3.0 (parent) | REST API framework |
| spring-boot-starter-data-jpa | 3.3.0 | JPA/Hibernate ORM |
| spring-boot-starter-security | 3.3.0 | Security framework |
| spring-boot-starter-validation | 3.3.0 | Jakarta Bean Validation |
| postgresql | runtime | PostgreSQL JDBC driver |
| jjwt-api / jjwt-impl / jjwt-jackson | 0.12.6 | JWT token validation |
| lombok | 1.18.42 (override) | Boilerplate reduction |
| mapstruct / mapstruct-processor | 1.5.5.Final | DTO mapping (declared, not actively used) |
| springdoc-openapi-starter-webmvc-ui | 2.5.0 | Swagger UI + OpenAPI generation |
| logstash-logback-encoder | 7.4 | Structured JSON logging (prod) |
| spring-boot-starter-test | 3.3.0 | Test framework |
| spring-security-test | 3.3.0 | Security test utilities |
| h2 | test | In-memory test database |
| testcontainers / testcontainers-postgresql | 1.19.8 | Container-based integration tests |
| mockito-core | 5.21.0 (override) | Mocking framework (Java 25 compat) |
| byte-buddy / byte-buddy-agent | 1.18.4 (override) | Mockito runtime (Java 25 compat) |

**Plugins:** `maven-compiler-plugin` (source/target 21, annotation processors: lombok 1.18.42 + mapstruct-processor 1.5.5.Final), `spring-boot-maven-plugin` (exclude lombok), `maven-surefire-plugin` (--add-opens for java.base), `jacoco-maven-plugin` 0.8.14 (prepare-agent + report).

```
Build:   ./mvnw clean package -DskipTests
Test:    ./mvnw test
Run:     ./mvnw spring-boot:run
Package: ./mvnw clean package
```

---

## 4. Configuration & Infrastructure Summary

- **`application.yml`** (`src/main/resources/`) — Server port 8096, active profile `dev`, JPA open-in-view disabled.
- **`application-dev.yml`** — PostgreSQL `localhost:5435/codeops_registry`, user `codeops/codeops`, Hibernate `ddl-auto: update`, JWT secret `dev-secret-key-minimum-32-characters-long-for-hs256`, CORS `localhost:3000,3200,5173`, service URLs: server=8095, vault=8097, logger=8098.
- **`application-prod.yml`** — All values from env vars: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, `CODEOPS_SERVER_URL`, `CODEOPS_VAULT_URL`, `CODEOPS_LOGGER_URL`. Hibernate `ddl-auto: validate`.
- **`application-test.yml`** — H2 in-memory, `ddl-auto: create-drop`, JWT test secret.
- **`application-integration.yml`** — PostgreSQL driver, `ddl-auto: create-drop` (for Testcontainers).
- **`logback-spring.xml`** — Dev: human-readable console with MDC (correlationId, userId, teamId). Prod: JSON via LogstashEncoder. Test: minimal WARN.
- **`docker-compose.yml`** — PostgreSQL 16-alpine, container `codeops-registry-db`, port `127.0.0.1:5435:5432`, database `codeops_registry`, user `codeops/codeops`, healthcheck configured.
- **`Dockerfile`** — `eclipse-temurin:21-jre-alpine`, non-root user (`appuser:appgroup`), EXPOSE 8096.

**Connection Map:**
```
Database:        PostgreSQL 16, localhost:5435, database codeops_registry
Cache:           None
Message Broker:  None
External APIs:   CodeOps-Server (JWT auth source), health check URLs (RestTemplate)
Cloud Services:  None
```

**CI/CD:** None detected.

---

## 5. Startup & Runtime Behavior

- **Entry point:** `CodeOpsRegistryApplication.java` — `@SpringBootApplication`, `@EnableConfigurationProperties({JwtProperties.class, ServiceUrlProperties.class})`
- **@PostConstruct:** `JwtTokenValidator.validateSecret()` — fails startup if JWT secret < 32 chars
- **CommandLineRunner:** `DataSeeder` (dev profile only) — idempotent seed of 9 services, 13 dependencies, 3 solutions, port ranges/allocations, API routes, environment configs, workstation profiles, infra resources. Skips if any services exist.
- **Scheduled tasks:** None
- **Health endpoint:** `GET /api/v1/health` (public, no auth) — returns `{"status":"UP","service":"codeops-registry","timestamp":"..."}`

---

## 6. Entity / Data Model Layer

### BaseEntity.java
`@MappedSuperclass`. PK: `UUID id` (`GenerationType.UUID`). Audit: `Instant createdAt` (`@PrePersist`), `Instant updatedAt` (`@PreUpdate`).

### ServiceRegistration.java
Table: `service_registrations`. UK: `(team_id, slug)`. Indexes: `team_id`, `status`.

Fields:
- `teamId`: UUID (nullable=false)
- `name`: String(200) (nullable=false)
- `slug`: String(100) (nullable=false)
- `serviceType`: ServiceType @Enumerated(STRING) (nullable=false)
- `description`: String(2000)
- `repoUrl`: String(500)
- `repoFullName`: String(300)
- `defaultBranch`: String(100) default "main"
- `techStack`: String(500)
- `status`: ServiceStatus @Enumerated(STRING) (nullable=false) default ACTIVE
- `healthCheckUrl`: String(500)
- `healthCheckIntervalSeconds`: Integer default 30
- `lastHealthStatus`: HealthStatus @Enumerated(STRING)
- `lastHealthCheckAt`: Instant
- `environmentsJson`: String(TEXT)
- `metadataJson`: String(TEXT)
- `createdByUserId`: UUID (nullable=false)

Relationships:
- `portAllocations`: @OneToMany → PortAllocation (cascade ALL, orphanRemoval)
- `dependenciesAsSource`: @OneToMany → ServiceDependency (mappedBy sourceService)
- `dependenciesAsTarget`: @OneToMany → ServiceDependency (mappedBy targetService)
- `routes`: @OneToMany → ApiRouteRegistration (cascade ALL, orphanRemoval)
- `solutionMemberships`: @OneToMany → SolutionMember (mappedBy service)
- `configTemplates`: @OneToMany → ConfigTemplate (cascade ALL, orphanRemoval)
- `environmentConfigs`: @OneToMany → EnvironmentConfig (cascade ALL, orphanRemoval)

### Solution.java
Table: `solutions`. UK: `(team_id, slug)`. Index: `team_id`.

Fields: `teamId` (UUID, nullable=false), `name` (String(200), nullable=false), `slug` (String(100), nullable=false), `description` (String(2000)), `category` (SolutionCategory, STRING, nullable=false), `status` (SolutionStatus, STRING, nullable=false, default ACTIVE), `iconName` (String(50)), `colorHex` (String(7)), `ownerUserId` (UUID), `repositoryUrl` (String(500)), `documentationUrl` (String(500)), `metadataJson` (TEXT), `createdByUserId` (UUID, nullable=false).

Relationships: `members`: @OneToMany → SolutionMember (cascade ALL, orphanRemoval).

### SolutionMember.java
Table: `solution_members`. UK: `(solution_id, service_id)`. Indexes: `solution_id`, `service_id`.

Fields: `role` (SolutionMemberRole, STRING), `displayOrder` (int, default 0), `notes` (String(500)).
Relationships: `solution`: @ManyToOne → Solution, `service`: @ManyToOne → ServiceRegistration.

### PortAllocation.java
Table: `port_allocations`. UK: `(service_id, environment, port_number)`. Indexes: `service_id`, `environment`, `port_number`.

Fields: `environment` (String(50), nullable=false), `portType` (PortType, STRING, nullable=false), `portNumber` (Integer, nullable=false), `protocol` (String(10), default "TCP"), `description` (String(200)), `isAutoAllocated` (Boolean, default true), `allocatedByUserId` (UUID).
Relationships: `service`: @ManyToOne → ServiceRegistration (nullable=false).

### PortRange.java
Table: `port_ranges`. UK: `(team_id, port_type, environment)`. Index: `team_id`.

Fields: `teamId` (UUID, nullable=false), `portType` (PortType, STRING, nullable=false), `rangeStart` (Integer, nullable=false), `rangeEnd` (Integer, nullable=false), `environment` (String(50), nullable=false), `description` (String(200)).

### ServiceDependency.java
Table: `service_dependencies`. UK: `(source_service_id, target_service_id, dependency_type)`. Indexes: `source_service_id`, `target_service_id`.

Fields: `dependencyType` (DependencyType, STRING, nullable=false), `description` (String(500)), `isRequired` (Boolean, default true), `targetEndpoint` (String(500)).
Relationships: `sourceService`: @ManyToOne → ServiceRegistration, `targetService`: @ManyToOne → ServiceRegistration.

### ApiRouteRegistration.java
Table: `api_route_registrations`. Indexes: `service_id`, `gateway_service_id`.

Fields: `routePrefix` (String(200), nullable=false), `httpMethods` (String(100)), `environment` (String(50), nullable=false), `description` (String(500)).
Relationships: `service`: @ManyToOne → ServiceRegistration (nullable=false), `gatewayService`: @ManyToOne → ServiceRegistration (nullable).

### ConfigTemplate.java
Table: `config_templates`. UK: `(service_id, template_type, environment)`. Index: `service_id`.

Fields: `templateType` (ConfigTemplateType, STRING, nullable=false), `environment` (String(50), nullable=false), `contentText` (TEXT, nullable=false), `isAutoGenerated` (Boolean, default true), `generatedFrom` (String(300)), `version` (Integer, default 1).
Relationships: `service`: @ManyToOne → ServiceRegistration (nullable=false).

### EnvironmentConfig.java
Table: `environment_configs`. UK: `(service_id, environment, config_key)`. Index: `service_id`.

Fields: `environment` (String(50), nullable=false), `configKey` (String(200), nullable=false), `configValue` (TEXT, nullable=false), `configSource` (ConfigSource, STRING, nullable=false), `description` (String(500)).
Relationships: `service`: @ManyToOne → ServiceRegistration (nullable=false).

### InfraResource.java
Table: `infra_resources`. UK: `(team_id, resource_type, resource_name, environment)`. Indexes: `team_id`, `service_id`.

Fields: `teamId` (UUID, nullable=false), `resourceType` (InfraResourceType, STRING, nullable=false), `resourceName` (String(300), nullable=false), `environment` (String(50), nullable=false), `region` (String(30)), `arnOrUrl` (String(500)), `metadataJson` (TEXT), `description` (String(500)), `createdByUserId` (UUID).
Relationships: `service`: @ManyToOne → ServiceRegistration (nullable).

### WorkstationProfile.java
Table: `workstation_profiles`. Index: `team_id`.

Fields: `teamId` (UUID, nullable=false), `name` (String(200), nullable=false), `description` (String(5000)), `solutionId` (UUID), `servicesJson` (TEXT, nullable=false), `startupOrder` (TEXT), `createdByUserId` (UUID, nullable=false), `isDefault` (Boolean, default false).

### Entity Relationship Summary
```
ServiceRegistration --[OneToMany]--> PortAllocation
ServiceRegistration --[OneToMany]--> ServiceDependency (as source)
ServiceRegistration --[OneToMany]--> ServiceDependency (as target)
ServiceRegistration --[OneToMany]--> ApiRouteRegistration
ServiceRegistration --[OneToMany]--> SolutionMember
ServiceRegistration --[OneToMany]--> ConfigTemplate
ServiceRegistration --[OneToMany]--> EnvironmentConfig
ServiceRegistration <--[ManyToOne]-- InfraResource (nullable)
Solution --[OneToMany]--> SolutionMember
SolutionMember --[ManyToOne]--> Solution
SolutionMember --[ManyToOne]--> ServiceRegistration
ApiRouteRegistration --[ManyToOne]--> ServiceRegistration (gateway, nullable)
```

---

## 7. Enum Definitions

| Enum | Values | Used By |
|---|---|---|
| `ConfigSource` | AUTO_GENERATED, MANUAL, INHERITED, REGISTRY_DERIVED | EnvironmentConfig.configSource |
| `ConfigTemplateType` | DOCKER_COMPOSE, APPLICATION_YML, APPLICATION_PROPERTIES, ENV_FILE, TERRAFORM_MODULE, CLAUDE_CODE_HEADER, CONVENTIONS_MD, NGINX_CONF, GITHUB_ACTIONS, DOCKERFILE, MAKEFILE, README_SECTION | ConfigTemplate.templateType |
| `DependencyType` | HTTP_REST, GRPC, KAFKA_TOPIC, DATABASE_SHARED, REDIS_SHARED, LIBRARY, GATEWAY_ROUTE, WEBSOCKET, FILE_SYSTEM, OTHER | ServiceDependency.dependencyType |
| `HealthStatus` | UP, DOWN, DEGRADED, UNKNOWN | ServiceRegistration.lastHealthStatus |
| `InfraResourceType` | S3_BUCKET, SQS_QUEUE, SNS_TOPIC, CLOUDWATCH_LOG_GROUP, IAM_ROLE, SECRETS_MANAGER_PATH, SSM_PARAMETER, RDS_INSTANCE, ELASTICACHE_CLUSTER, ECR_REPOSITORY, CLOUD_MAP_NAMESPACE, ROUTE53_RECORD, ACM_CERTIFICATE, ALB_TARGET_GROUP, ECS_SERVICE, LAMBDA_FUNCTION, DYNAMODB_TABLE, DOCKER_NETWORK, DOCKER_VOLUME, OTHER | InfraResource.resourceType |
| `PortType` | HTTP_API, FRONTEND_DEV, DATABASE, REDIS, KAFKA, KAFKA_INTERNAL, ZOOKEEPER, GRPC, WEBSOCKET, DEBUG, ACTUATOR, CUSTOM | PortAllocation.portType, PortRange.portType |
| `ServiceStatus` | ACTIVE, INACTIVE, DEPRECATED, ARCHIVED | ServiceRegistration.status |
| `ServiceType` | SPRING_BOOT_API, FLUTTER_WEB, FLUTTER_DESKTOP, FLUTTER_MOBILE, REACT_SPA, VUE_SPA, NEXT_JS, EXPRESS_API, FASTAPI, DOTNET_API, GO_API, LIBRARY, WORKER, GATEWAY, DATABASE_SERVICE, MESSAGE_BROKER, CACHE_SERVICE, MCP_SERVER, CLI_TOOL, OTHER | ServiceRegistration.serviceType |
| `SolutionCategory` | PLATFORM, APPLICATION, LIBRARY_SUITE, INFRASTRUCTURE, TOOLING, OTHER | Solution.category |
| `SolutionMemberRole` | CORE, SUPPORTING, INFRASTRUCTURE, EXTERNAL_DEPENDENCY | SolutionMember.role |
| `SolutionStatus` | ACTIVE, IN_DEVELOPMENT, DEPRECATED, ARCHIVED | Solution.status |

---

## 8. Repository Layer

All extend `JpaRepository<Entity, UUID>`.

### ServiceRegistrationRepository
- `List<ServiceRegistration> findByTeamId(UUID teamId)`
- `List<ServiceRegistration> findByTeamIdAndStatus(UUID teamId, ServiceStatus status)`
- `Optional<ServiceRegistration> findByTeamIdAndSlug(UUID teamId, String slug)`
- `Page<ServiceRegistration> findByTeamId(UUID teamId, Pageable pageable)`
- `Page<ServiceRegistration> findByTeamIdAndStatus(UUID teamId, ServiceStatus status, Pageable pageable)`
- `Page<ServiceRegistration> findByTeamIdAndServiceType(UUID teamId, ServiceType type, Pageable pageable)`
- `Page<ServiceRegistration> findByTeamIdAndStatusAndServiceType(UUID teamId, ServiceStatus status, ServiceType type, Pageable pageable)`
- `Page<ServiceRegistration> findByTeamIdAndNameContainingIgnoreCase(UUID teamId, String name, Pageable pageable)`
- `boolean existsByTeamIdAndSlug(UUID teamId, String slug)`
- `long countByTeamId(UUID teamId)`

### SolutionRepository
- `List<Solution> findByTeamId(UUID teamId)`
- `Optional<Solution> findByTeamIdAndSlug(UUID teamId, String slug)`
- `boolean existsByTeamIdAndSlug(UUID teamId, String slug)`
- `Page<Solution> findByTeamId(UUID teamId, Pageable pageable)`
- `Page<Solution> findByTeamIdAndStatus(UUID teamId, SolutionStatus status, Pageable pageable)`
- `Page<Solution> findByTeamIdAndCategory(UUID teamId, SolutionCategory category, Pageable pageable)`
- `Page<Solution> findByTeamIdAndStatusAndCategory(UUID teamId, SolutionStatus status, SolutionCategory category, Pageable pageable)`
- `long countByTeamId(UUID teamId)`

### SolutionMemberRepository
- `List<SolutionMember> findBySolutionId(UUID solutionId)`
- `List<SolutionMember> findBySolutionIdOrderByDisplayOrderAsc(UUID solutionId)`
- `Optional<SolutionMember> findBySolutionIdAndServiceId(UUID solutionId, UUID serviceId)`
- `boolean existsBySolutionIdAndServiceId(UUID solutionId, UUID serviceId)`
- `int countBySolutionId(UUID solutionId)`
- `List<SolutionMember> findByServiceId(UUID serviceId)`

### PortAllocationRepository
- `List<PortAllocation> findByServiceId(UUID serviceId)`
- `List<PortAllocation> findByServiceIdAndEnvironment(UUID serviceId, String environment)`
- `List<PortAllocation> findByServiceTeamIdAndEnvironment(UUID teamId, String environment)`
- `Optional<PortAllocation> findByEnvironmentAndPortNumber(String environment, int portNumber)`
- `boolean existsByEnvironmentAndPortNumber(String environment, int portNumber)`
- `@Query` `List<PortAllocation> findConflictingPorts(UUID teamId)` — finds ports with same portNumber+environment allocated to multiple services

### PortRangeRepository
- `List<PortRange> findByTeamId(UUID teamId)`
- `Optional<PortRange> findByTeamIdAndPortTypeAndEnvironment(UUID teamId, PortType portType, String environment)`
- `boolean existsByTeamIdAndPortTypeAndEnvironment(UUID teamId, PortType portType, String environment)`

### ServiceDependencyRepository
- `List<ServiceDependency> findBySourceServiceId(UUID sourceId)`
- `List<ServiceDependency> findByTargetServiceId(UUID targetId)`
- `List<ServiceDependency> findBySourceServiceTeamId(UUID teamId)`
- `boolean existsBySourceServiceIdAndTargetServiceIdAndDependencyType(UUID sourceId, UUID targetId, DependencyType type)`

### ApiRouteRegistrationRepository
- `List<ApiRouteRegistration> findByServiceId(UUID serviceId)`
- `List<ApiRouteRegistration> findByGatewayServiceIdAndEnvironment(UUID gatewayId, String environment)`
- `List<ApiRouteRegistration> findByGatewayServiceIdAndEnvironmentAndRoutePrefixStartingWith(UUID gatewayId, String environment, String prefix)`

### ConfigTemplateRepository
- `List<ConfigTemplate> findByServiceId(UUID serviceId)`
- `Optional<ConfigTemplate> findByServiceIdAndTemplateTypeAndEnvironment(UUID serviceId, ConfigTemplateType type, String environment)`

### EnvironmentConfigRepository
- `List<EnvironmentConfig> findByServiceIdAndEnvironment(UUID serviceId, String environment)`
- `Optional<EnvironmentConfig> findByServiceIdAndEnvironmentAndConfigKey(UUID serviceId, String environment, String configKey)`

### InfraResourceRepository
- `List<InfraResource> findByTeamId(UUID teamId)`
- `Page<InfraResource> findByTeamId(UUID teamId, Pageable pageable)`
- `Page<InfraResource> findByTeamIdAndResourceType(UUID teamId, InfraResourceType type, Pageable pageable)`
- `Page<InfraResource> findByTeamIdAndEnvironment(UUID teamId, String environment, Pageable pageable)`
- `Page<InfraResource> findByTeamIdAndResourceTypeAndEnvironment(UUID teamId, InfraResourceType type, String environment, Pageable pageable)`
- `List<InfraResource> findByServiceId(UUID serviceId)`
- `List<InfraResource> findByTeamIdAndServiceIsNull(UUID teamId)`
- `long countByTeamId(UUID teamId)`

### WorkstationProfileRepository
- `List<WorkstationProfile> findByTeamId(UUID teamId)`
- `Optional<WorkstationProfile> findByTeamIdAndIsDefaultTrue(UUID teamId)`
- `long countByTeamId(UUID teamId)`

---

## 9. Service Layer

### ServiceRegistryService
**Dependencies:** ServiceRegistrationRepository, PortAllocationRepository, ServiceDependencyRepository, ApiRouteRegistrationRepository, InfraResourceRepository, EnvironmentConfigRepository, SolutionMemberRepository, RestTemplate

- `createService(CreateServiceRequest, UUID userId) → ServiceRegistrationResponse` — validates team limit (200), generates slug, persists. Throws: ValidationException (limit/duplicate slug).
- `getServicesForTeam(UUID teamId, ServiceStatus, ServiceType, String search, Pageable) → PageResponse<ServiceRegistrationResponse>` — filtered, paginated listing.
- `getService(UUID serviceId) → ServiceRegistrationResponse` — single service with derived counts. Throws: NotFoundException.
- `updateService(UUID serviceId, UpdateServiceRequest) → ServiceRegistrationResponse` — partial update (non-null fields). Throws: NotFoundException, ValidationException (slug conflict).
- `deleteService(UUID serviceId)` — deletes entity (cascades). Throws: NotFoundException.
- `updateServiceStatus(UUID serviceId, ServiceStatus) → ServiceRegistrationResponse` — Throws: NotFoundException.
- `cloneService(UUID serviceId, CloneServiceRequest, UUID userId) → ServiceRegistrationResponse` — copies all fields except id, slug, health state. Throws: NotFoundException.
- `getServiceIdentity(UUID serviceId, String environment) → ServiceIdentityResponse` — assembles ports, deps, routes, infra, configs. Throws: NotFoundException.
- `checkHealth(UUID serviceId) → ServiceHealthResponse` — HTTP GET to healthCheckUrl, updates entity. Throws: NotFoundException.
- `checkAllHealth(UUID teamId) → List<ServiceHealthResponse>` — parallel health checks via CompletableFuture.
- `getServiceBySlug(UUID teamId, String slug) → ServiceRegistrationResponse` — Throws: NotFoundException.

### SolutionService
**Dependencies:** SolutionRepository, SolutionMemberRepository, ServiceRegistrationRepository, HealthCheckService

- `createSolution(CreateSolutionRequest, UUID userId) → SolutionResponse` — validates team limit (50), generates slug. Throws: ValidationException.
- `getSolutionsForTeam(UUID teamId, SolutionStatus, SolutionCategory, Pageable) → PageResponse<SolutionResponse>` — filtered, paginated.
- `getSolution(UUID solutionId) → SolutionResponse` — with member count. Throws: NotFoundException.
- `updateSolution(UUID solutionId, UpdateSolutionRequest) → SolutionResponse` — partial update. Throws: NotFoundException, ValidationException.
- `deleteSolution(UUID solutionId)` — Throws: NotFoundException.
- `getSolutionDetail(UUID solutionId) → SolutionDetailResponse` — includes enriched member list. Throws: NotFoundException.
- `addMember(UUID solutionId, AddSolutionMemberRequest) → SolutionMemberResponse` — Throws: NotFoundException, ValidationException (duplicate).
- `updateMember(UUID solutionId, UUID serviceId, UpdateSolutionMemberRequest) → SolutionMemberResponse` — Throws: NotFoundException.
- `removeMember(UUID solutionId, UUID serviceId)` — Throws: NotFoundException.
- `reorderMembers(UUID solutionId, List<UUID> orderedServiceIds) → List<SolutionMemberResponse>` — assigns sequential displayOrder.
- `getSolutionHealth(UUID solutionId) → SolutionHealthResponse` — delegates to HealthCheckService.

### PortAllocationService
**Dependencies:** PortAllocationRepository, PortRangeRepository, ServiceRegistrationRepository

- `autoAllocate(UUID serviceId, String environment, PortType, UUID userId) → PortAllocationResponse` — finds next available port in range. Throws: NotFoundException, ValidationException (no range, ports exhausted, limit 20/service).
- `manualAllocate(AllocatePortRequest, UUID userId) → PortAllocationResponse` — Throws: NotFoundException, ValidationException (duplicate port).
- `releasePort(UUID allocationId)` — Throws: NotFoundException.
- `getPortsForService(UUID serviceId, String environment) → List<PortAllocationResponse>`
- `getPortsForTeam(UUID teamId, String environment) → List<PortAllocationResponse>`
- `getPortMap(UUID teamId, String environment) → PortMapResponse` — ranges with allocations, capacity stats.
- `checkAvailability(UUID teamId, int portNumber, String environment) → PortCheckResponse`
- `detectConflicts(UUID teamId) → List<PortConflictResponse>` — uses custom repository query.
- `getPortRanges(UUID teamId) → List<PortRangeResponse>`
- `seedDefaultRanges(UUID teamId, String environment) → List<PortRangeResponse>` — creates default ranges from AppConstants.
- `updatePortRange(UUID rangeId, UpdatePortRangeRequest) → PortRangeResponse` — Throws: NotFoundException, ValidationException (start > end).

### DependencyGraphService
**Dependencies:** ServiceDependencyRepository, ServiceRegistrationRepository

- `createDependency(CreateDependencyRequest) → ServiceDependencyResponse` — validates no self-dep, no duplicate, checks cycles (DFS). Throws: NotFoundException, ValidationException (limit 50/service, self-dep, duplicate, cycle).
- `removeDependency(UUID dependencyId)` — Throws: NotFoundException.
- `getDependencyGraph(UUID teamId) → DependencyGraphResponse` — nodes + edges for visualization.
- `getImpactAnalysis(UUID serviceId) → ImpactAnalysisResponse` — BFS from source, traverses reverse edges. Throws: NotFoundException.
- `getStartupOrder(UUID teamId) → List<DependencyNodeResponse>` — Kahn's algorithm topological sort.
- `detectCycles(UUID teamId) → List<UUID>` — DFS three-color algorithm.

### ApiRouteService
**Dependencies:** ApiRouteRegistrationRepository, ServiceRegistrationRepository

- `createRoute(CreateRouteRequest, UUID userId) → ApiRouteResponse` — Throws: NotFoundException, ValidationException (duplicate prefix+env on same gateway).
- `deleteRoute(UUID routeId)` — Throws: NotFoundException.
- `getRoutesForService(UUID serviceId) → List<ApiRouteResponse>`
- `getRoutesForGateway(UUID gatewayServiceId, String environment) → List<ApiRouteResponse>`
- `checkRouteAvailability(UUID gatewayServiceId, String environment, String routePrefix) → RouteCheckResponse`

### ConfigEngineService
**Dependencies:** ConfigTemplateRepository, ServiceRegistrationRepository, PortAllocationRepository, ServiceDependencyRepository, EnvironmentConfigRepository, SolutionRepository, SolutionMemberRepository

- `generateDockerCompose(UUID serviceId, String environment) → ConfigTemplateResponse` — generates docker-compose.yml from registry data (ports, env configs). Saves as template.
- `generateApplicationYml(UUID serviceId, String environment) → ConfigTemplateResponse` — generates application.yml from env configs + port data.
- `generateClaudeCodeHeader(UUID serviceId, String environment) → ConfigTemplateResponse` — generates context header for Claude Code.
- `generateAllForService(UUID serviceId, String environment) → List<ConfigTemplateResponse>` — all three types.
- `generateSolutionDockerCompose(UUID solutionId, String environment) → ConfigTemplateResponse` — multi-service compose for entire solution.
- `getTemplate(UUID serviceId, ConfigTemplateType, String environment) → ConfigTemplateResponse` — Throws: NotFoundException.
- `getTemplatesForService(UUID serviceId) → List<ConfigTemplateResponse>`
- `deleteTemplate(UUID templateId)` — Throws: NotFoundException.

### HealthCheckService
**Dependencies:** ServiceRegistrationRepository, SolutionMemberRepository, SolutionRepository, ServiceRegistryService

- `getTeamHealthSummary(UUID teamId) → TeamHealthSummaryResponse` — cached data, no live checks.
- `checkTeamHealth(UUID teamId) → TeamHealthSummaryResponse` — triggers live checks then summarizes.
- `getUnhealthyServices(UUID teamId) → List<ServiceHealthResponse>` — DOWN or DEGRADED only.
- `getServicesNeverChecked(UUID teamId) → List<ServiceHealthResponse>` — has URL but never checked.
- `checkSolutionHealth(UUID solutionId) → SolutionHealthResponse` — parallel live checks on solution members. Throws: NotFoundException.
- `getServiceHealthHistory(UUID serviceId) → ServiceHealthResponse` — cached. Throws: NotFoundException.

### InfraResourceService
**Dependencies:** InfraResourceRepository, ServiceRegistrationRepository

- `createResource(CreateInfraResourceRequest, UUID userId) → InfraResourceResponse` — validates team limit. Throws: NotFoundException, ValidationException.
- `getResourcesForTeam(UUID teamId, InfraResourceType, String environment, Pageable) → PageResponse<InfraResourceResponse>` — filtered, paginated.
- `updateResource(UUID resourceId, UpdateInfraResourceRequest) → InfraResourceResponse` — partial update. Throws: NotFoundException.
- `deleteResource(UUID resourceId)` — Throws: NotFoundException.
- `getResourcesForService(UUID serviceId) → List<InfraResourceResponse>`
- `findOrphanedResources(UUID teamId) → List<InfraResourceResponse>` — resources with null service.
- `reassignResource(UUID resourceId, UUID newServiceId) → InfraResourceResponse` — Throws: NotFoundException.
- `orphanResource(UUID resourceId) → InfraResourceResponse` — sets service to null. Throws: NotFoundException.

### TopologyService
**Dependencies:** ServiceRegistrationRepository, ServiceDependencyRepository, SolutionRepository, SolutionMemberRepository, PortAllocationRepository

- `getTopology(UUID teamId) → TopologyResponse` — full ecosystem: nodes with layer classification, edges, solution groups, layers, stats.
- `getTopologyForSolution(UUID solutionId) → TopologyResponse` — scoped to solution members. Throws: NotFoundException.
- `getServiceNeighborhood(UUID serviceId, int depth) → TopologyResponse` — BFS neighborhood (depth capped at 3). Throws: NotFoundException.
- `getEcosystemStats(UUID teamId) → TopologyStatsResponse` — aggregate counts.

Layer classification: GATEWAY (ServiceType.GATEWAY), FRONTEND (REACT_SPA/VUE_SPA/NEXT_JS/FLUTTER_*), INFRASTRUCTURE (DATABASE_SERVICE/MESSAGE_BROKER/CACHE_SERVICE), BACKEND (SPRING_BOOT_API/EXPRESS_API/FASTAPI/DOTNET_API/GO_API), STANDALONE (all others).

### WorkstationProfileService
**Dependencies:** WorkstationProfileRepository, ServiceRegistrationRepository, SolutionMemberRepository, DependencyGraphService, ObjectMapper

- `createProfile(CreateWorkstationProfileRequest, UUID userId) → WorkstationProfileResponse` — validates team limit (20). If solutionId provided, resolves member services. Computes startup order from dependency graph. Throws: ValidationException.
- `getProfilesForTeam(UUID teamId) → List<WorkstationProfileResponse>`
- `getProfile(UUID profileId) → WorkstationProfileResponse` — enriched with service details. Throws: NotFoundException.
- `updateProfile(UUID profileId, UpdateWorkstationProfileRequest) → WorkstationProfileResponse` — partial update, recomputes startup order. Throws: NotFoundException.
- `deleteProfile(UUID profileId)` — Throws: NotFoundException.
- `getDefaultProfile(UUID teamId) → WorkstationProfileResponse` — Throws: NotFoundException.
- `setDefault(UUID profileId) → WorkstationProfileResponse` — clears previous default. Throws: NotFoundException.
- `createFromSolution(UUID solutionId, UUID teamId, UUID userId) → WorkstationProfileResponse` — creates profile from solution members with computed startup order.
- `refreshStartupOrder(UUID profileId) → WorkstationProfileResponse` — recomputes from current dependency graph. Throws: NotFoundException.

---

## 10. Security Architecture

**Authentication:** Stateless JWT (Bearer tokens). Tokens are issued by CodeOps-Server and validated here using a shared HMAC secret (`codeops.jwt.secret`). Registry never issues tokens.

**Token Validation (JwtTokenValidator):** HMAC signature verification using `jjwt 0.12.6`. Extracts: `sub` (userId), `email`, `roles` (List), `teamIds` (List), `teamRoles` (Map). Minimum secret length: 32 chars (enforced at startup).

**Authorization:** `@PreAuthorize` on every controller method (77 total annotations across 10 controllers). Roles: `ADMIN`. Authorities: `registry:read`, `registry:write`, `registry:delete`. Pattern: `hasRole('ADMIN') or hasAuthority('registry:read|write|delete')`.

**Security Filter Chain (order):**
1. `RequestCorrelationFilter` (HIGHEST_PRECEDENCE) — MDC: correlationId, requestPath, requestMethod
2. `RateLimitFilter` — per-IP rate limiting on `/api/v1/registry/**`
3. `JwtAuthFilter` — extracts/validates JWT, sets SecurityContext
4. Spring Security default chain

**Public Paths:** `/api/v1/health`, `/swagger-ui/**`, `/v3/api-docs/**`, `/v3/api-docs.yaml`

**Security Headers:** CSP (`default-src 'self'; frame-ancestors 'none'`), X-Frame-Options DENY, X-Content-Type-Options nosniff, HSTS (1 year, includeSubDomains).

**CORS:** Origins from `codeops.cors.allowed-origins` (default `http://localhost:3000`). Methods: GET, POST, PUT, DELETE, PATCH, OPTIONS. Credentials enabled. Preflight max-age 3600s.

**Rate Limiting:** In-memory sliding window per IP, 100 requests/minute on `/api/v1/registry/**`. Returns 429 JSON on violation.

**Encryption:** None (no at-rest encryption, no password hashing — this is a registry service, not an auth service).

**Password Policy:** N/A (no user management).

**Token Revocation:** None (stateless JWT, no blacklist).

---

## 11. Notification / Messaging Layer

No message broker (Kafka, RabbitMQ, SQS/SNS) detected in this project. No email or webhook services.

---

## 12. Error Handling

**GlobalExceptionHandler** (`@RestControllerAdvice`):

| Exception | HTTP Status | Response | Client Details |
|---|---|---|---|
| `EntityNotFoundException` | 404 | `ErrorResponse(404, "Resource not found")` | Generic message |
| `IllegalArgumentException` | 400 | `ErrorResponse(400, "Invalid request")` | Generic message |
| `AccessDeniedException` | 403 | `ErrorResponse(403, "Access denied")` | Generic message |
| `MethodArgumentNotValidException` | 400 | `ErrorResponse(400, "field: message, ...")` | Field-level details |
| `NotFoundException` | 404 | `ErrorResponse(404, ex.getMessage())` | Entity + ID |
| `ValidationException` | 400 | `ErrorResponse(400, ex.getMessage())` | Business rule details |
| `AuthorizationException` | 403 | `ErrorResponse(403, ex.getMessage())` | Auth failure details |
| `MissingServletRequestParameterException` | 400 | `ErrorResponse(400, "Missing required parameter: X")` | Param name |
| `HttpMessageNotReadableException` | 400 | `ErrorResponse(400, "Malformed request body")` | Generic message |
| `NoResourceFoundException` | 404 | `ErrorResponse(404, "Resource not found")` | Generic message |
| `CodeOpsRegistryException` | 500 | `ErrorResponse(500, "An internal error occurred")` | Hidden |
| `Exception` (catch-all) | 500 | `ErrorResponse(500, "An internal error occurred")` | Hidden |

Internal details logged server-side, never exposed to clients for 500s.

**Exception hierarchy:** `CodeOpsRegistryException` (base) → `NotFoundException`, `ValidationException`, `AuthorizationException`.

---

## 13. Test Coverage

- **Unit test files:** 26 (all services, all controllers, security, config, util)
- **Integration test files:** 1 (`BaseIntegrationTest` — base class, 0 @Test methods)
- **Total @Test methods:** 548 unit, 0 integration
- **Framework:** JUnit 5, Mockito 5.21.0, Spring Security Test (`@WithMockUser`)
- **Test database:** H2 in-memory (`application-test.yml`)
- **Integration infrastructure:** Testcontainers + PostgreSQL (base class exists, no tests use it yet)
- **Security tests:** 171 occurrences of `@WithMockUser` / `@WithAnonymousUser` / `Authorization.*Bearer`

---

## 14. Cross-Cutting Patterns & Conventions

- **Package structure:** `config`, `controller`, `dto/request`, `dto/response`, `entity`, `entity/enums`, `exception`, `repository`, `security`, `service`, `util`
- **Base class:** `BaseEntity` (UUID PK, createdAt/updatedAt audit fields via @PrePersist/@PreUpdate)
- **DTO pattern:** All DTOs are Java records. Request DTOs have Jakarta validation annotations. Response DTOs are pure value objects.
- **Controller pattern:** All controllers under `/api/v1/registry`, one per domain. Each method annotated with `@PreAuthorize`. Returns `ResponseEntity<T>`.
- **Service pattern:** Constructor injection via Lombok `@RequiredArgsConstructor`. Throw `NotFoundException`/`ValidationException` for business errors.
- **Error handling:** Services throw, GlobalExceptionHandler catches. No try/catch in controllers.
- **Pagination:** Spring Data `Pageable` + custom `PageResponse<T>` wrapper (not raw `Page<T>`).
- **Validation:** DTO annotations for input shape, service-layer logic for business rules (limits, uniqueness, cycles).
- **Slug generation:** `SlugUtils.generateSlug(name)` → lowercase, hyphenated. `SlugUtils.makeUnique(slug, existsCheck)` appends `-1`, `-2`, etc.
- **Constants:** `AppConstants` — pagination defaults, port ranges, slug rules, health check params, per-team limits, config engine constants, topology layer names.
- **Audit logging:** Request logging via `LoggingInterceptor` (method, URI, status, duration, correlationId). No dedicated audit log service.
- **Documentation:** All non-DTO, non-enum, non-entity classes have comprehensive Javadoc on classes and public methods.

---

## 15. Known Issues, TODOs, and Technical Debt

No TODO, FIXME, HACK, or XXX found in source code.

---

## 17. Database — Live Schema Audit

Database not available for live audit. Schema documented from JPA entities only (Section 6).

---

## 18. Kafka / Message Broker

No message broker (Kafka, RabbitMQ, SQS/SNS) detected in this project.

---

## 19. Redis / Cache Layer

No Redis or caching layer detected in this project.

---

## 20. Environment Variable Inventory

| Variable | Required | Default | Used By | Purpose |
|---|---|---|---|---|
| `DATABASE_URL` | Prod | (none) | application-prod.yml | JDBC connection URL |
| `DATABASE_USERNAME` | Prod | (none) | application-prod.yml | Database user |
| `DATABASE_PASSWORD` | Prod | (none) | application-prod.yml | Database password |
| `JWT_SECRET` | Prod | dev default (32+ chars) | application-prod.yml | JWT shared HMAC secret |
| `CORS_ALLOWED_ORIGINS` | Prod | `http://localhost:3000` | application-prod.yml | CORS allowed origins |
| `CODEOPS_SERVER_URL` | Prod | `http://localhost:8095` | application-prod.yml | CodeOps-Server base URL |
| `CODEOPS_VAULT_URL` | Prod | `http://localhost:8097` | application-prod.yml | CodeOps-Vault base URL |
| `CODEOPS_LOGGER_URL` | Prod | `http://localhost:8098` | application-prod.yml | CodeOps-Logger base URL |

**Warning:** Dev profile has a hardcoded JWT secret in `application-dev.yml`. Acceptable for local development only.

---

## 21. Inter-Service Communication Map

**Outbound:**
- `RestTemplate` → health check URLs (configured per service in `ServiceRegistration.healthCheckUrl`). Timeout: 5s connect, 10s read. Error handling: catches all exceptions, sets health status to DOWN.

**Inbound (documented, not enforced by code):**
- CodeOps-Client → this service (REST API)
- CodeOps-Gateway → this service (REST API)

**Service URL properties** (configured but not actively called in current code): `codeops.services.server-url`, `codeops.services.vault-url`, `codeops.services.logger-url`.
