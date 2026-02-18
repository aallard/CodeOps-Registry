# CodeOps-Registry — Codebase Audit

**Audit Date:** 2026-02-18T01:21:34Z
**Branch:** main
**Commit:** a87e021a0c711b9cd5f8bd59e1bf929b38eb6ee2 CR-FIX-001: Fix RBAC role ARCHITECT → ADMIN across all controllers and tests; update CodeOps-Server URL from 8090 to 8095
**Auditor:** Claude Code (Automated)
**Purpose:** Zero-context reference for AI-assisted development
**Audit File:** CodeOps-Registry-Audit.md
**Scorecard:** CodeOps-Registry-Scorecard.md
**OpenAPI Spec:** openapi.yaml

> This audit is the single source of truth for the CodeOps-Registry codebase.
> The OpenAPI spec (openapi.yaml) is the source of truth for all endpoints, DTOs, and API contracts.
> An AI reading this audit + the OpenAPI spec should be able to generate accurate code
> changes, new features, tests, and fixes without filesystem access.

---

## 1. Project Identity

```
Project Name:            CodeOps Registry
Repository URL:          https://github.com/adamallard/CodeOps-Registry
Primary Language:        Java 21 (Spring Boot 3.3.0)
Java Version:            21 (compile target; runtime Java 25)
Build Tool:              Maven 3.x (Spring Boot parent 3.3.0)
Current Branch:          main
Latest Commit Hash:      a87e021a0c711b9cd5f8bd59e1bf929b38eb6ee2
Latest Commit Message:   CR-FIX-001: Fix RBAC role ARCHITECT → ADMIN across all controllers and tests
Audit Timestamp:         2026-02-18T01:21:34Z
```

---

## 2. Directory Structure

```
CodeOps-Registry/
├── CONVENTIONS.md
├── Dockerfile
├── docker-compose.yml
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/com/codeops/registry/
    │   │   ├── CodeOpsRegistryApplication.java
    │   │   ├── config/
    │   │   │   ├── AppConstants.java
    │   │   │   ├── AsyncConfig.java
    │   │   │   ├── CorsConfig.java
    │   │   │   ├── DataSeeder.java
    │   │   │   ├── GlobalExceptionHandler.java
    │   │   │   ├── HealthController.java
    │   │   │   ├── JwtProperties.java
    │   │   │   ├── LoggingInterceptor.java
    │   │   │   ├── OpenApiConfig.java
    │   │   │   ├── RequestCorrelationFilter.java
    │   │   │   ├── RestTemplateConfig.java
    │   │   │   ├── ServiceUrlProperties.java
    │   │   │   └── WebMvcConfig.java
    │   │   ├── controller/ (10 controllers)
    │   │   │   ├── ConfigController.java
    │   │   │   ├── DependencyController.java
    │   │   │   ├── HealthManagementController.java
    │   │   │   ├── InfraController.java
    │   │   │   ├── PortController.java
    │   │   │   ├── RegistryController.java
    │   │   │   ├── RouteController.java
    │   │   │   ├── SolutionController.java
    │   │   │   ├── TopologyController.java
    │   │   │   └── WorkstationController.java
    │   │   ├── dto/
    │   │   │   ├── request/ (21 request DTOs)
    │   │   │   └── response/ (28 response DTOs)
    │   │   ├── entity/ (12 entities)
    │   │   │   ├── BaseEntity.java
    │   │   │   ├── ApiRouteRegistration.java
    │   │   │   ├── ConfigTemplate.java
    │   │   │   ├── EnvironmentConfig.java
    │   │   │   ├── InfraResource.java
    │   │   │   ├── PortAllocation.java
    │   │   │   ├── PortRange.java
    │   │   │   ├── ServiceDependency.java
    │   │   │   ├── ServiceRegistration.java
    │   │   │   ├── Solution.java
    │   │   │   ├── SolutionMember.java
    │   │   │   ├── WorkstationProfile.java
    │   │   │   └── enums/ (11 enums)
    │   │   ├── exception/ (4 exception classes)
    │   │   ├── repository/ (11 repository interfaces)
    │   │   ├── security/ (5 security classes)
    │   │   │   ├── JwtAuthFilter.java
    │   │   │   ├── JwtTokenValidator.java
    │   │   │   ├── RateLimitFilter.java
    │   │   │   ├── SecurityConfig.java
    │   │   │   └── SecurityUtils.java
    │   │   ├── service/ (10 service classes)
    │   │   └── util/
    │   │       └── SlugUtils.java
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       ├── application-prod.yml
    │       ├── application-test.yml
    │       ├── application-integration.yml
    │       └── logback-spring.xml
    └── test/
        └── java/com/codeops/registry/
            ├── CodeOpsRegistryApplicationTest.java
            ├── config/ (2 test classes)
            ├── controller/ (10 controller test classes)
            ├── integration/ (BaseIntegrationTest.java)
            ├── security/ (2 security test classes)
            ├── service/ (10 service test classes)
            └── util/ (SlugUtilsTest.java)
```

Single-module Spring Boot project. Source code in `src/main/java/com/codeops/registry/`, organized by layer: config, controller, dto, entity, exception, repository, security, service, util.

---

## 3. Build & Dependency Manifest

**Build file:** `pom.xml` (Spring Boot parent 3.3.0)

| Dependency | Version | Purpose |
|---|---|---|
| spring-boot-starter-web | 3.3.0 (parent) | REST API framework |
| spring-boot-starter-data-jpa | 3.3.0 (parent) | JPA/Hibernate ORM |
| spring-boot-starter-security | 3.3.0 (parent) | Authentication/authorization |
| spring-boot-starter-validation | 3.3.0 (parent) | Jakarta Bean Validation |
| postgresql | runtime (parent-managed) | PostgreSQL JDBC driver |
| jjwt-api / jjwt-impl / jjwt-jackson | 0.12.6 | JWT token validation |
| lombok | 1.18.42 (overridden) | Boilerplate reduction |
| mapstruct | 1.5.5.Final | DTO mapping (declared, not yet used) |
| jackson-datatype-jsr310 | parent-managed | Java 8+ date/time serialization |
| springdoc-openapi-starter-webmvc-ui | 2.5.0 | OpenAPI/Swagger UI generation |
| logstash-logback-encoder | 7.4 | JSON structured logging (prod) |
| spring-boot-starter-test | 3.3.0 (parent) | Test framework (JUnit 5, Mockito) |
| spring-security-test | parent-managed | Security test utilities |
| testcontainers-postgresql | 1.19.8 | Integration test containers |
| testcontainers-junit-jupiter | 1.19.8 | Testcontainers JUnit 5 integration |
| h2 | parent-managed | In-memory test database |
| mockito | 5.21.0 (overridden) | Java 25 compatibility |
| byte-buddy | 1.18.4 (overridden) | Java 25 compatibility |

**Build plugins:** spring-boot-maven-plugin, maven-compiler-plugin (Lombok + MapStruct processor paths), maven-surefire-plugin (--add-opens for Java 25), jacoco-maven-plugin 0.8.14.

**Build commands:**
```
Build:   mvn clean compile -DskipTests
Test:    mvn test
Run:     mvn spring-boot:run
Package: mvn clean package -DskipTests
```

---

## 4. Configuration & Infrastructure Summary

- **`application.yml`** — Default profile `dev`, server port `8096`, app name `codeops-registry`.
- **`application-dev.yml`** — PostgreSQL at `localhost:5435/codeops_registry`, `ddl-auto: update`, show-sql true, JWT secret from `${JWT_SECRET}` with dev default, CORS allows `localhost:3000,3200,5173`, service URLs from env vars with localhost defaults (Server=8095, Vault=8097, Logger=8098), DEBUG logging.
- **`application-prod.yml`** — All values from env vars (no defaults), `ddl-auto: validate`, show-sql false, INFO logging.
- **`application-test.yml`** — H2 in-memory (`MODE=PostgreSQL`), `ddl-auto: create-drop`, hardcoded JWT secret, WARN logging.
- **`application-integration.yml`** — PostgreSQL driver (URL from Testcontainers), `ddl-auto: create-drop`, hardcoded JWT secret, WARN logging.
- **`logback-spring.xml`** — Dev profile: human-readable console with MDC (correlationId, userId). Prod profile: JSON via LogstashEncoder. Test profile: WARN-only minimal output.
- **`docker-compose.yml`** — PostgreSQL 16-alpine on `127.0.0.1:5435:5432`, container `codeops-registry-db`, database `codeops_registry`, with healthcheck and named volume.
- **`Dockerfile`** — `eclipse-temurin:21-jre-alpine`, non-root user `appuser`, exposes 8096.

**Connection map:**
```
Database:        PostgreSQL 16, localhost:5435, database codeops_registry
Cache:           None
Message Broker:  None
External APIs:   CodeOps-Server (http://localhost:8095), CodeOps-Vault (http://localhost:8097), CodeOps-Logger (http://localhost:8098)
Cloud Services:  None
```

**CI/CD:** None detected.

---

## 5. Startup & Runtime Behavior

- **Entry point:** `CodeOpsRegistryApplication.java` — `@SpringBootApplication` + `@EnableConfigurationProperties({JwtProperties.class, ServiceUrlProperties.class})`
- **Startup initialization:**
  - `DataSeeder` (`@Profile("dev")`, `@PostConstruct`) — Idempotent seed: 9 services (CodeOps-Server, Analytics, Client, Registry, Vault, Logger, Gateway, Console, CLI), 13 dependencies, 3 solutions (CodeOps Platform, CodeOps Analytics, CodeOps DevTools), with port allocations and ranges.
  - `PortAllocationService.seedDefaultRanges()` — Called by DataSeeder to populate port ranges per team.
- **Scheduled tasks:** None.
- **Health check:** `GET /health` — Custom `HealthController` returning `{"status":"UP","service":"codeops-registry","timestamp":"..."}` (unauthenticated).

---

## 6. Entity / Data Model Layer

### BaseEntity.java
```
Table: (mapped superclass — no table)
Primary Key: UUID id (GenerationType via JPA provider)

Fields:
  - id: UUID @Id @GeneratedValue
  - createdAt: Instant @Column(updatable = false)
  - updatedAt: Instant

Lifecycle:
  - @PrePersist onCreate(): sets both timestamps to Instant.now()
  - @PreUpdate onUpdate(): updates updatedAt to Instant.now()
```

### ServiceRegistration.java
```
Table: service_registrations
Primary Key: UUID id (inherited from BaseEntity)

Fields:
  - teamId: UUID @Column(nullable = false)
  - name: String @Column(nullable = false, length = 100)
  - slug: String @Column(nullable = false, length = 63)
  - serviceType: ServiceType @Enumerated(STRING) @Column(nullable = false, length = 20)
  - description: String @Column(columnDefinition = "TEXT")
  - repoUrl: String @Column(length = 500)
  - repoFullName: String @Column(length = 200)
  - defaultBranch: String @Column(length = 50, default "main")
  - techStack: String @Column(length = 500)
  - status: ServiceStatus @Enumerated(STRING) @Column(nullable = false, length = 20, default ACTIVE)
  - healthCheckUrl: String @Column(length = 500)
  - healthCheckIntervalSeconds: Integer @Column(default 30)
  - lastHealthStatus: HealthStatus @Enumerated(STRING) @Column(length = 20)
  - lastHealthCheckAt: Instant
  - environmentsJson: String @Column(columnDefinition = "TEXT")
  - metadataJson: String @Column(columnDefinition = "TEXT")
  - createdByUserId: UUID

Relationships:
  - portAllocations: @OneToMany → PortAllocation (mappedBy "service", cascade ALL, orphanRemoval)
  - dependenciesAsSource: @OneToMany → ServiceDependency (mappedBy "sourceService")
  - dependenciesAsTarget: @OneToMany → ServiceDependency (mappedBy "targetService")
  - routes: @OneToMany → ApiRouteRegistration (mappedBy "service", cascade ALL, orphanRemoval)
  - solutionMemberships: @OneToMany → SolutionMember (mappedBy "service")
  - configTemplates: @OneToMany → ConfigTemplate (mappedBy "service", cascade ALL, orphanRemoval)
  - environmentConfigs: @OneToMany → EnvironmentConfig (mappedBy "service", cascade ALL, orphanRemoval)

Indexes: idx_sr_team_id(team_id), idx_sr_status(status)
Unique Constraints: uk_sr_team_slug(team_id, slug)
```

### Solution.java
```
Table: solutions
Primary Key: UUID id (inherited)

Fields:
  - teamId: UUID @Column(nullable = false)
  - name: String @Column(nullable = false, length = 200)
  - slug: String @Column(nullable = false, length = 63)
  - description: String @Column(columnDefinition = "TEXT")
  - category: SolutionCategory @Enumerated(STRING) @Column(nullable = false, length = 30)
  - status: SolutionStatus @Enumerated(STRING) @Column(nullable = false, length = 20, default ACTIVE)
  - iconName: String @Column(length = 50)
  - colorHex: String @Column(length = 7)
  - ownerUserId: UUID
  - repositoryUrl: String @Column(length = 500)
  - documentationUrl: String @Column(length = 500)
  - metadataJson: String @Column(columnDefinition = "TEXT")
  - createdByUserId: UUID

Relationships:
  - members: @OneToMany → SolutionMember (mappedBy "solution", cascade ALL, orphanRemoval)

Indexes: idx_sol_team_id(team_id)
Unique Constraints: uk_sol_team_slug(team_id, slug)
```

### SolutionMember.java
```
Table: solution_members
Primary Key: UUID id (inherited)

Fields:
  - solution: Solution @ManyToOne(fetch = LAZY) @JoinColumn(nullable = false)
  - service: ServiceRegistration @ManyToOne(fetch = LAZY) @JoinColumn(nullable = false)
  - role: SolutionMemberRole @Enumerated(STRING) @Column(nullable = false, length = 30)
  - displayOrder: Integer @Column(default 0)
  - notes: String @Column(length = 500)

Indexes: idx_sm_solution_id(solution_id), idx_sm_service_id(service_id)
Unique Constraints: uk_sm_solution_service(solution_id, service_id)
```

### ServiceDependency.java
```
Table: service_dependencies
Primary Key: UUID id (inherited)

Fields:
  - sourceService: ServiceRegistration @ManyToOne(fetch = LAZY) @JoinColumn(nullable = false)
  - targetService: ServiceRegistration @ManyToOne(fetch = LAZY) @JoinColumn(nullable = false)
  - dependencyType: DependencyType @Enumerated(STRING) @Column(nullable = false, length = 30)
  - description: String @Column(length = 500)
  - isRequired: Boolean @Column(default true)
  - targetEndpoint: String @Column(length = 500)

Indexes: idx_sd_source_id(source_service_id), idx_sd_target_id(target_service_id)
Unique Constraints: uk_sd_source_target_type(source_service_id, target_service_id, dependency_type)
```

### PortAllocation.java
```
Table: port_allocations
Primary Key: UUID id (inherited)

Fields:
  - service: ServiceRegistration @ManyToOne(fetch = LAZY) @JoinColumn(nullable = false)
  - environment: String @Column(nullable = false, length = 50)
  - portType: PortType @Enumerated(STRING) @Column(nullable = false)
  - portNumber: Integer @Column(nullable = false)
  - protocol: String @Column(length = 10, default "TCP")
  - description: String @Column(length = 200)
  - isAutoAllocated: Boolean @Column(default true)
  - allocatedByUserId: UUID

Indexes: idx_pa_service_id(service_id), idx_pa_environment(environment), idx_pa_port_number(port_number)
Unique Constraints: uk_pa_service_env_port(service_id, environment, port_number)
```

### PortRange.java
```
Table: port_ranges
Primary Key: UUID id (inherited)

Fields:
  - teamId: UUID @Column(nullable = false)
  - portType: PortType @Enumerated(STRING) @Column(nullable = false, length = 30)
  - rangeStart: Integer @Column(nullable = false)
  - rangeEnd: Integer @Column(nullable = false)
  - environment: String @Column(nullable = false, length = 50)
  - description: String @Column(length = 200)

Indexes: idx_pr_team_id(team_id)
Unique Constraints: uk_pr_team_type_env(team_id, port_type, environment)
```

### ApiRouteRegistration.java
```
Table: api_route_registrations
Primary Key: UUID id (inherited)

Fields:
  - service: ServiceRegistration @ManyToOne(fetch = LAZY) @JoinColumn(nullable = false)
  - gatewayService: ServiceRegistration @ManyToOne(fetch = LAZY) @JoinColumn (nullable)
  - routePrefix: String @Column(nullable = false, length = 200)
  - httpMethods: String @Column(length = 100)
  - environment: String @Column(nullable = false, length = 50)
  - description: String @Column(length = 500)

Indexes: idx_arr_service_id(service_id), idx_arr_gateway_id(gateway_service_id)
```

### InfraResource.java
```
Table: infra_resources
Primary Key: UUID id (inherited)

Fields:
  - teamId: UUID @Column(nullable = false)
  - service: ServiceRegistration @ManyToOne(fetch = LAZY) @JoinColumn (nullable — for team-shared resources)
  - resourceType: InfraResourceType @Enumerated(STRING) @Column(nullable = false, length = 30)
  - resourceName: String @Column(nullable = false, length = 300)
  - environment: String @Column(nullable = false, length = 50)
  - region: String @Column(length = 30)
  - arnOrUrl: String @Column(length = 500)
  - metadataJson: String @Column(columnDefinition = "TEXT")
  - description: String @Column(length = 500)
  - createdByUserId: UUID

Indexes: idx_ir_team_id(team_id), idx_ir_service_id(service_id)
Unique Constraints: uk_ir_team_type_name_env(team_id, resource_type, resource_name, environment)
```

### ConfigTemplate.java
```
Table: config_templates
Primary Key: UUID id (inherited)

Fields:
  - service: ServiceRegistration @ManyToOne(fetch = LAZY) @JoinColumn(nullable = false)
  - templateType: ConfigTemplateType @Enumerated(STRING) @Column(nullable = false, length = 30)
  - environment: String @Column(nullable = false, length = 50)
  - contentText: String @Column(nullable = false, columnDefinition = "TEXT")
  - isAutoGenerated: Boolean @Column(default true)
  - generatedFrom: String @Column(length = 200)
  - version: Integer @Column(default 1)

Indexes: idx_ct_service_id(service_id)
Unique Constraints: uk_ct_service_type_env(service_id, template_type, environment)
```

### EnvironmentConfig.java
```
Table: environment_configs
Primary Key: UUID id (inherited)

Fields:
  - service: ServiceRegistration @ManyToOne(fetch = LAZY) @JoinColumn(nullable = false)
  - environment: String @Column(nullable = false, length = 50)
  - configKey: String @Column(nullable = false, length = 200)
  - configValue: String @Column(nullable = false, columnDefinition = "TEXT")
  - configSource: ConfigSource @Enumerated(STRING) @Column(length = 20)
  - description: String @Column(length = 500)

Indexes: idx_ec_service_id(service_id)
Unique Constraints: uk_ec_service_env_key(service_id, environment, config_key)
```

### WorkstationProfile.java
```
Table: workstation_profiles
Primary Key: UUID id (inherited)

Fields:
  - teamId: UUID @Column(nullable = false)
  - name: String @Column(nullable = false, length = 100)
  - description: String @Column(columnDefinition = "TEXT")
  - solutionId: UUID
  - servicesJson: String @Column(nullable = false, columnDefinition = "TEXT")
  - startupOrder: String @Column(columnDefinition = "TEXT")
  - createdByUserId: UUID
  - isDefault: Boolean @Column(default false)

Indexes: idx_wp_team_id(team_id)
```

### Entity Relationship Summary

```
ServiceRegistration --[OneToMany]--> PortAllocation (via service_id)
ServiceRegistration --[OneToMany]--> ServiceDependency (via source_service_id, target_service_id)
ServiceRegistration --[OneToMany]--> ApiRouteRegistration (via service_id)
ServiceRegistration --[OneToMany]--> SolutionMember (via service_id)
ServiceRegistration --[OneToMany]--> ConfigTemplate (via service_id)
ServiceRegistration --[OneToMany]--> EnvironmentConfig (via service_id)
ServiceRegistration --[OneToMany]--> InfraResource (via service_id, nullable)
Solution --[OneToMany]--> SolutionMember (via solution_id)
SolutionMember --[ManyToOne]--> Solution (via solution_id)
SolutionMember --[ManyToOne]--> ServiceRegistration (via service_id)
ServiceDependency --[ManyToOne]--> ServiceRegistration (source, target)
ApiRouteRegistration --[ManyToOne]--> ServiceRegistration (service, gateway nullable)
```

---

## 7. Enum Definitions

```
=== ServiceType.java ===
Values: SPRING_BOOT_API, FLUTTER_WEB, FLUTTER_DESKTOP, FLUTTER_MOBILE, REACT_SPA, VUE_SPA, NEXT_JS, EXPRESS_API, FASTAPI, DOTNET_API, GO_API, LIBRARY, WORKER, GATEWAY, DATABASE_SERVICE, MESSAGE_BROKER, CACHE_SERVICE, MCP_SERVER, CLI_TOOL, OTHER
Used By: ServiceRegistration.serviceType

=== ServiceStatus.java ===
Values: ACTIVE, INACTIVE, DEPRECATED, ARCHIVED
Used By: ServiceRegistration.status

=== HealthStatus.java ===
Values: UP, DOWN, DEGRADED, UNKNOWN
Used By: ServiceRegistration.lastHealthStatus

=== DependencyType.java ===
Values: HTTP_REST, GRPC, KAFKA_TOPIC, DATABASE_SHARED, REDIS_SHARED, LIBRARY, GATEWAY_ROUTE, WEBSOCKET, FILE_SYSTEM, OTHER
Used By: ServiceDependency.dependencyType

=== PortType.java ===
Values: HTTP_API, FRONTEND_DEV, DATABASE, REDIS, KAFKA, KAFKA_INTERNAL, ZOOKEEPER, GRPC, WEBSOCKET, DEBUG, ACTUATOR, CUSTOM
Used By: PortAllocation.portType, PortRange.portType

=== SolutionCategory.java ===
Values: PLATFORM, APPLICATION, LIBRARY_SUITE, INFRASTRUCTURE, TOOLING, OTHER
Used By: Solution.category

=== SolutionStatus.java ===
Values: ACTIVE, IN_DEVELOPMENT, DEPRECATED, ARCHIVED
Used By: Solution.status

=== SolutionMemberRole.java ===
Values: CORE, SUPPORTING, INFRASTRUCTURE, EXTERNAL_DEPENDENCY
Used By: SolutionMember.role

=== InfraResourceType.java ===
Values: S3_BUCKET, SQS_QUEUE, SNS_TOPIC, CLOUDWATCH_LOG_GROUP, IAM_ROLE, SECRETS_MANAGER_PATH, SSM_PARAMETER, RDS_INSTANCE, ELASTICACHE_CLUSTER, ECR_REPOSITORY, CLOUD_MAP_NAMESPACE, ROUTE53_RECORD, ACM_CERTIFICATE, ALB_TARGET_GROUP, ECS_SERVICE, LAMBDA_FUNCTION, DYNAMODB_TABLE, DOCKER_NETWORK, DOCKER_VOLUME, OTHER
Used By: InfraResource.resourceType

=== ConfigTemplateType.java ===
Values: DOCKER_COMPOSE, APPLICATION_YML, APPLICATION_PROPERTIES, ENV_FILE, TERRAFORM_MODULE, CLAUDE_CODE_HEADER, CONVENTIONS_MD, NGINX_CONF, GITHUB_ACTIONS, DOCKERFILE, MAKEFILE, README_SECTION
Used By: ConfigTemplate.templateType

=== ConfigSource.java ===
Values: AUTO_GENERATED, MANUAL, INHERITED, REGISTRY_DERIVED
Used By: EnvironmentConfig.configSource
```

---

## 8. Repository Layer

### ServiceRegistrationRepository.java
```
Extends: JpaRepository<ServiceRegistration, UUID>

Custom Methods:
  - Optional<ServiceRegistration> findByTeamIdAndSlug(UUID teamId, String slug)
  - List<ServiceRegistration> findByTeamId(UUID teamId)
  - Page<ServiceRegistration> findByTeamId(UUID teamId, Pageable pageable)
  - List<ServiceRegistration> findByTeamIdAndStatus(UUID teamId, ServiceStatus status)
  - Page<ServiceRegistration> findByTeamIdAndStatus(UUID teamId, ServiceStatus status, Pageable pageable)
  - Page<ServiceRegistration> findByTeamIdAndServiceType(UUID teamId, ServiceType type, Pageable pageable)
  - Page<ServiceRegistration> findByTeamIdAndStatusAndServiceType(UUID teamId, ServiceStatus status, ServiceType type, Pageable pageable)
  - List<ServiceRegistration> findByTeamIdAndIdIn(UUID teamId, List<UUID> ids)
  - Page<ServiceRegistration> findByTeamIdAndNameContainingIgnoreCase(UUID teamId, String name, Pageable pageable)
  - long countByTeamId(UUID teamId)
  - long countByTeamIdAndStatus(UUID teamId, ServiceStatus status)
  - boolean existsByTeamIdAndSlug(UUID teamId, String slug)
```

### SolutionRepository.java
```
Extends: JpaRepository<Solution, UUID>

Custom Methods:
  - Optional<Solution> findByTeamIdAndSlug(UUID teamId, String slug)
  - List<Solution> findByTeamId(UUID teamId)
  - Page<Solution> findByTeamId(UUID teamId, Pageable pageable)
  - Page<Solution> findByTeamIdAndStatus(UUID teamId, SolutionStatus status, Pageable pageable)
  - Page<Solution> findByTeamIdAndCategory(UUID teamId, SolutionCategory category, Pageable pageable)
  - long countByTeamId(UUID teamId)
  - boolean existsByTeamIdAndSlug(UUID teamId, String slug)
```

### SolutionMemberRepository.java
```
Extends: JpaRepository<SolutionMember, UUID>

Custom Methods:
  - List<SolutionMember> findBySolutionId(UUID solutionId)
  - List<SolutionMember> findBySolutionIdOrderByDisplayOrderAsc(UUID solutionId)
  - List<SolutionMember> findByServiceId(UUID serviceId)
  - Optional<SolutionMember> findBySolutionIdAndServiceId(UUID solutionId, UUID serviceId)
  - boolean existsBySolutionIdAndServiceId(UUID solutionId, UUID serviceId)
  - void deleteBySolutionIdAndServiceId(UUID solutionId, UUID serviceId)
  - long countBySolutionId(UUID solutionId)
  - long countByServiceId(UUID serviceId)
```

### ServiceDependencyRepository.java
```
Extends: JpaRepository<ServiceDependency, UUID>

Custom Methods:
  - List<ServiceDependency> findBySourceServiceId(UUID sourceId)
  - List<ServiceDependency> findByTargetServiceId(UUID targetId)
  - Optional<ServiceDependency> findBySourceServiceIdAndTargetServiceIdAndDependencyType(UUID sourceId, UUID targetId, DependencyType type)
  - boolean existsBySourceServiceIdAndTargetServiceId(UUID sourceId, UUID targetId)
  - @Query List<ServiceDependency> findAllByTeamId(@Param UUID teamId) — joins sourceService.teamId
  - long countBySourceServiceId(UUID sourceId)
  - long countByTargetServiceId(UUID targetId)
```

### PortAllocationRepository.java
```
Extends: JpaRepository<PortAllocation, UUID>

Custom Methods:
  - List<PortAllocation> findByServiceId(UUID serviceId)
  - List<PortAllocation> findByServiceIdAndEnvironment(UUID serviceId, String env)
  - @Query List<PortAllocation> findByTeamIdAndEnvironment(@Param UUID teamId, @Param String env)
  - @Query Optional<PortAllocation> findByTeamIdAndEnvironmentAndPortNumber(@Param UUID teamId, @Param String env, @Param int portNumber)
  - @Query List<PortAllocation> findByTeamIdAndEnvironmentAndPortType(@Param UUID teamId, @Param String env, @Param PortType type) — ordered by portNumber ASC
  - @Query List<Object[]> findConflictingPorts(@Param UUID teamId) — GROUP BY having COUNT > 1
  - boolean existsByServiceIdAndEnvironmentAndPortNumber(UUID serviceId, String env, int port)
  - long countByServiceId(UUID serviceId)
```

### PortRangeRepository.java
```
Extends: JpaRepository<PortRange, UUID>

Custom Methods:
  - List<PortRange> findByTeamId(UUID teamId)
  - List<PortRange> findByTeamIdAndEnvironment(UUID teamId, String env)
  - Optional<PortRange> findByTeamIdAndPortTypeAndEnvironment(UUID teamId, PortType type, String env)
  - boolean existsByTeamId(UUID teamId)
```

### ApiRouteRegistrationRepository.java
```
Extends: JpaRepository<ApiRouteRegistration, UUID>

Custom Methods:
  - List<ApiRouteRegistration> findByServiceId(UUID serviceId)
  - List<ApiRouteRegistration> findByGatewayServiceIdAndEnvironment(UUID gatewayId, String env)
  - @Query Optional<ApiRouteRegistration> findByGatewayAndPrefixAndEnvironment(@Param UUID gatewayId, @Param String env, @Param String prefix)
  - @Query List<ApiRouteRegistration> findOverlappingRoutes(@Param UUID gatewayId, @Param String env, @Param String prefix) — LIKE CONCAT for hierarchical overlap
  - @Query List<ApiRouteRegistration> findOverlappingDirectRoutes(@Param UUID teamId, @Param String env, @Param String prefix) — no gateway, LIKE CONCAT
```

### InfraResourceRepository.java
```
Extends: JpaRepository<InfraResource, UUID>

Custom Methods:
  - List<InfraResource> findByTeamId(UUID teamId)
  - Page<InfraResource> findByTeamId(UUID teamId, Pageable pageable)
  - Page<InfraResource> findByTeamIdAndResourceType(UUID teamId, InfraResourceType type, Pageable pageable)
  - Page<InfraResource> findByTeamIdAndEnvironment(UUID teamId, String env, Pageable pageable)
  - Page<InfraResource> findByTeamIdAndResourceTypeAndEnvironment(UUID teamId, InfraResourceType type, String env, Pageable pageable)
  - List<InfraResource> findByServiceId(UUID serviceId)
  - Optional<InfraResource> findByTeamIdAndResourceTypeAndResourceNameAndEnvironment(UUID teamId, InfraResourceType type, String name, String env)
  - @Query List<InfraResource> findOrphansByTeamId(@Param UUID teamId) — WHERE service IS NULL
```

### ConfigTemplateRepository.java
```
Extends: JpaRepository<ConfigTemplate, UUID>

Custom Methods:
  - List<ConfigTemplate> findByServiceId(UUID serviceId)
  - List<ConfigTemplate> findByServiceIdAndEnvironment(UUID serviceId, String env)
  - List<ConfigTemplate> findByServiceIdAndTemplateType(UUID serviceId, ConfigTemplateType type)
  - Optional<ConfigTemplate> findByServiceIdAndTemplateTypeAndEnvironment(UUID serviceId, ConfigTemplateType type, String env)
```

### EnvironmentConfigRepository.java
```
Extends: JpaRepository<EnvironmentConfig, UUID>

Custom Methods:
  - List<EnvironmentConfig> findByServiceIdAndEnvironment(UUID serviceId, String env)
  - Optional<EnvironmentConfig> findByServiceIdAndEnvironmentAndConfigKey(UUID serviceId, String env, String key)
  - List<EnvironmentConfig> findByServiceId(UUID serviceId)
  - void deleteByServiceIdAndEnvironmentAndConfigKey(UUID serviceId, String env, String key)
```

### WorkstationProfileRepository.java
```
Extends: JpaRepository<WorkstationProfile, UUID>

Custom Methods:
  - List<WorkstationProfile> findByTeamId(UUID teamId)
  - Optional<WorkstationProfile> findByTeamIdAndIsDefaultTrue(UUID teamId)
  - Optional<WorkstationProfile> findByTeamIdAndName(UUID teamId, String name)
  - long countByTeamId(UUID teamId)
```

---

## 9. Service Layer

### ServiceRegistryService.java (495 lines)
```
Injected Dependencies: ServiceRegistrationRepository, PortAllocationRepository, ServiceDependencyRepository, ApiRouteRegistrationRepository, InfraResourceRepository, SolutionMemberRepository, ConfigTemplateRepository, EnvironmentConfigRepository, PortAllocationService, RestTemplate

Methods:
  ─── registerService(CreateServiceRequest, UUID teamId, UUID userId) → ServiceRegistrationResponse
      Purpose: Creates new service with slug generation and auto-port allocation
      Authorization: None (controller-level)
      Logic: 1. Generate slug via SlugUtils 2. Make unique 3. Build entity 4. Save 5. Auto-allocate HTTP_API port
      Throws: None (slug validation in SlugUtils)

  ─── getServiceById(UUID serviceId) → ServiceRegistrationResponse
      Purpose: Retrieves service by ID
      Logic: Find or throw NotFoundException

  ─── getServiceBySlug(UUID teamId, String slug) → ServiceRegistrationResponse
      Purpose: Retrieves service by team + slug
      Logic: Find or throw NotFoundException

  ─── getServicesForTeam(UUID teamId, ServiceStatus status, ServiceType type, Pageable) → PageResponse<ServiceRegistrationResponse>
      Purpose: Lists services with optional filtering
      Logic: Branch on filter combinations → paginated query
      Paginated: Yes

  ─── searchServices(UUID teamId, String query, Pageable) → PageResponse<ServiceRegistrationResponse>
      Purpose: Name search (case-insensitive)
      Paginated: Yes

  ─── updateService(UUID serviceId, UpdateServiceRequest) → ServiceRegistrationResponse
      Purpose: Partial update — applies non-null fields
      Logic: Fetch, apply updates, save

  ─── updateServiceStatus(UUID serviceId, UpdateServiceStatusRequest) → ServiceRegistrationResponse
      Purpose: Updates only the status field

  ─── deleteService(UUID serviceId) → void
      Purpose: Deletes service and all cascading entities
      Logic: Remove solution memberships, dependencies (source+target), then delete

  ─── getServiceIdentity(UUID serviceId) → ServiceIdentityResponse
      Purpose: Assembles full identity: ports, deps, routes, infra, config keys

  ─── cloneService(UUID sourceServiceId, CloneServiceRequest, UUID teamId, UUID userId) → ServiceRegistrationResponse
      Purpose: Deep copies service with new name/slug, including ports, routes, infra, env configs

  ─── checkHealth(UUID serviceId) → ServiceHealthResponse
      Purpose: Executes HTTP GET to service's healthCheckUrl, updates entity
      Logic: RestTemplate.getForEntity → update lastHealthStatus/lastHealthCheckAt

  ─── checkAllHealth(UUID teamId) → List<ServiceHealthResponse>
      Purpose: Checks all active services with health URLs

  ─── getServiceCount(UUID teamId) → long
      Purpose: Team service count

  ─── getActiveServiceCount(UUID teamId) → long
      Purpose: Active service count
```

### SolutionService.java (367 lines)
```
Injected Dependencies: SolutionRepository, SolutionMemberRepository, ServiceRegistrationRepository

Methods:
  ─── createSolution(CreateSolutionRequest, UUID teamId, UUID userId) → SolutionResponse
      Purpose: Creates solution with slug generation
      Logic: Generate slug → make unique → build entity → save

  ─── getSolutionById(UUID) → SolutionResponse
  ─── getSolutionBySlug(UUID teamId, String slug) → SolutionResponse
  ─── getSolutionDetail(UUID) → SolutionDetailResponse (includes ordered members)
  ─── getSolutionsForTeam(UUID, SolutionStatus, SolutionCategory, Pageable) → PageResponse<SolutionResponse>
  ─── updateSolution(UUID, UpdateSolutionRequest) → SolutionResponse
  ─── deleteSolution(UUID) → void (cascade via orphanRemoval)

  ─── addMember(UUID solutionId, AddSolutionMemberRequest) → SolutionMemberResponse
      Logic: Validate not already member → compute max displayOrder + 1 → create

  ─── updateMember(UUID solutionId, UUID serviceId, UpdateSolutionMemberRequest) → SolutionMemberResponse
  ─── removeMember(UUID solutionId, UUID serviceId) → void

  ─── reorderMembers(UUID solutionId, List<UUID> orderedServiceIds) → List<SolutionMemberResponse>
      Logic: Validate all IDs are members → assign sequential displayOrder → save all

  ─── getSolutionHealth(UUID) → SolutionHealthResponse
      Purpose: Aggregates health from cached service data (no live HTTP calls)
      Logic: Count UP/DOWN/DEGRADED/UNKNOWN → compute worst-case overall status
```

### DependencyGraphService.java (439 lines)
```
Injected Dependencies: ServiceDependencyRepository, ServiceRegistrationRepository

Methods:
  ─── createDependency(CreateDependencyRequest) → ServiceDependencyResponse
      Logic: 1. Validate source ≠ target 2. Same team 3. No duplicate 4. Per-service limit check 5. Cycle detection via hasPath(BFS) 6. Create
      Throws: ValidationException (self-ref, diff team, duplicate, limit exceeded, cycle detected)

  ─── removeDependency(UUID) → void
  ─── getDependencyGraph(UUID teamId) → DependencyGraphResponse (nodes + edges)

  ─── getImpactAnalysis(UUID serviceId) → ImpactAnalysisResponse
      Purpose: BFS reverse traversal — what breaks if this service goes down
      Logic: Build reverse adjacency → BFS from service → track depth and connection type

  ─── getStartupOrder(UUID teamId) → List<DependencyNodeResponse>
      Purpose: Topological sort via Kahn's algorithm (inverted edges for startup order)

  ─── detectCycles(UUID teamId) → List<UUID>
      Purpose: DFS three-color marking — WHITE/GRAY/BLACK cycle detection
```

### PortAllocationService.java (447 lines)
```
Injected Dependencies: PortAllocationRepository, PortRangeRepository, ServiceRegistrationRepository

Methods:
  ─── autoAllocate(UUID serviceId, String env, PortType, UUID userId) → PortAllocationResponse
      Logic: Find range (fallback to "local") → scan for first free port in range → create allocation

  ─── autoAllocateAll(UUID serviceId, String env, List<PortType>, UUID userId) → List<PortAllocationResponse>
  ─── manualAllocate(AllocatePortRequest, UUID userId) → PortAllocationResponse
  ─── releasePort(UUID allocationId) → void
  ─── checkAvailability(UUID teamId, int port, String env) → PortCheckResponse
  ─── getPortsForService(UUID serviceId, String env) → List<PortAllocationResponse>
  ─── getPortsForTeam(UUID teamId, String env) → List<PortAllocationResponse>

  ─── getPortMap(UUID teamId, String env) → PortMapResponse
      Purpose: Structured map: ranges with their allocations, capacity/allocated/available counts

  ─── detectConflicts(UUID teamId) → List<PortConflictResponse>
      Purpose: Finds ports allocated to multiple services

  ─── getPortRanges(UUID teamId) → List<PortRangeResponse>
  ─── updatePortRange(UUID rangeId, UpdatePortRangeRequest) → PortRangeResponse
  ─── seedDefaultRanges(UUID teamId, String env) → List<PortRangeResponse>
```

### ApiRouteService.java (225 lines)
```
Injected Dependencies: ApiRouteRegistrationRepository, ServiceRegistrationRepository

Methods:
  ─── createRoute(CreateRouteRequest, UUID userId) → ApiRouteResponse
      Logic: Normalize prefix → check overlapping routes → create
      Collision: Hierarchical prefix overlap detection via LIKE CONCAT

  ─── deleteRoute(UUID) → void
  ─── getRoutesForService(UUID serviceId) → List<ApiRouteResponse>
  ─── getRoutesForGateway(UUID gatewayId, String env) → List<ApiRouteResponse>
  ─── checkRouteAvailability(UUID gatewayId, String env, String prefix) → RouteCheckResponse
  ─── normalizePrefix(String) → String — lowercase, leading slash, no trailing slash, validate pattern
```

### InfraResourceService.java (270 lines)
```
Injected Dependencies: InfraResourceRepository, ServiceRegistrationRepository

Methods:
  ─── createResource(CreateInfraResourceRequest, UUID userId) → InfraResourceResponse
  ─── updateResource(UUID, UpdateInfraResourceRequest) → InfraResourceResponse
  ─── deleteResource(UUID) → void
  ─── getResourcesForTeam(UUID, InfraResourceType, String env, Pageable) → PageResponse<InfraResourceResponse>
  ─── getResourcesForService(UUID serviceId) → List<InfraResourceResponse>
  ─── findOrphanedResources(UUID teamId) → List<InfraResourceResponse>
  ─── reassignResource(UUID resourceId, UUID newServiceId) → InfraResourceResponse
  ─── orphanResource(UUID resourceId) → InfraResourceResponse
```

### TopologyService.java (543 lines)
```
Injected Dependencies: ServiceRegistrationRepository, ServiceDependencyRepository, SolutionRepository, SolutionMemberRepository, PortAllocationRepository

Methods:
  ─── getTopology(UUID teamId) → TopologyResponse
      Purpose: Full ecosystem view — nodes, edges, solution groups, layers, stats

  ─── getTopologyForSolution(UUID solutionId) → TopologyResponse
      Purpose: Solution-scoped — members + internal dependencies only

  ─── getServiceNeighborhood(UUID serviceId, int depth) → TopologyResponse
      Purpose: Ego-network — service + bidirectional neighbors within depth hops (max 3)

  ─── getEcosystemStats(UUID teamId) → TopologyStatsResponse
      Purpose: Quick aggregate stats without full graph build

  Layer Classification:
    - Infrastructure: DATABASE_SERVICE, MESSAGE_BROKER, CACHE_SERVICE
    - Backend: SPRING_BOOT_API, EXPRESS_API, FASTAPI, DOTNET_API, GO_API, WORKER, MCP_SERVER
    - Frontend: FLUTTER_WEB, FLUTTER_DESKTOP, FLUTTER_MOBILE, REACT_SPA, VUE_SPA, NEXT_JS
    - Gateway: GATEWAY
    - Standalone: LIBRARY, CLI_TOOL, OTHER
```

### HealthCheckService.java (251 lines)
```
Injected Dependencies: ServiceRegistrationRepository, SolutionMemberRepository, SolutionRepository, ServiceRegistryService

Methods:
  ─── getTeamHealthSummary(UUID teamId) → TeamHealthSummaryResponse (cached data)
  ─── checkTeamHealth(UUID teamId) → TeamHealthSummaryResponse (live checks then summary)
  ─── getUnhealthyServices(UUID teamId) → List<ServiceHealthResponse>
  ─── getServicesNeverChecked(UUID teamId) → List<ServiceHealthResponse>
  ─── checkSolutionHealth(UUID solutionId) → SolutionHealthResponse (parallel CompletableFuture)
  ─── getServiceHealthHistory(UUID serviceId) → ServiceHealthResponse
  ─── computeOverallHealth(boolean, int, int, int) → HealthStatus — DOWN > DEGRADED > UNKNOWN > UP
```

### ConfigEngineService.java (694 lines)
```
Injected Dependencies: ServiceRegistrationRepository, PortAllocationRepository, ServiceDependencyRepository, EnvironmentConfigRepository, ConfigTemplateRepository, ApiRouteRegistrationRepository, InfraResourceRepository, SolutionRepository, SolutionMemberRepository, DependencyGraphService

Methods:
  ─── generateDockerCompose(UUID serviceId, String env) → ConfigTemplateResponse
  ─── generateApplicationYml(UUID serviceId, String env) → ConfigTemplateResponse
  ─── generateClaudeCodeHeader(UUID serviceId, String env) → ConfigTemplateResponse
  ─── generateAllForService(UUID serviceId, String env) → List<ConfigTemplateResponse>
  ─── generateSolutionDockerCompose(UUID solutionId, String env) → ConfigTemplateResponse
      Purpose: Docker Compose for entire solution with dependency-ordered startup
  ─── getTemplate(UUID serviceId, ConfigTemplateType, String env) → ConfigTemplateResponse
  ─── getTemplatesForService(UUID serviceId) → List<ConfigTemplateResponse>
  ─── deleteTemplate(UUID templateId) → void

  Upsert semantics: Templates updated on regeneration with version increment.
  SnakeYAML used for YAML serialization.
```

### WorkstationProfileService.java (497 lines)
```
Injected Dependencies: WorkstationProfileRepository, ServiceRegistrationRepository, SolutionMemberRepository, SolutionRepository, DependencyGraphService, ObjectMapper

Methods:
  ─── createProfile(CreateWorkstationProfileRequest, UUID userId) → WorkstationProfileResponse
      Logic: Check team limit → validate name unique → resolve service IDs → validate IDs → compute startup order → create

  ─── getProfile(UUID) → WorkstationProfileResponse (enriched with service details)
  ─── getProfilesForTeam(UUID teamId) → List<WorkstationProfileResponse> (lightweight)
  ─── getDefaultProfile(UUID teamId) → WorkstationProfileResponse (enriched)
  ─── updateProfile(UUID, UpdateWorkstationProfileRequest) → WorkstationProfileResponse
  ─── deleteProfile(UUID) → void
  ─── setDefault(UUID profileId) → WorkstationProfileResponse (mutual exclusion)
  ─── createFromSolution(UUID solutionId, UUID teamId, UUID userId) → WorkstationProfileResponse
  ─── refreshStartupOrder(UUID profileId) → WorkstationProfileResponse

  JSON serialization: Service IDs and startup order stored as JSON arrays in TEXT columns.
  Startup order computed from team's dependency graph via DependencyGraphService.getStartupOrder().
```

---

## 10. Security Architecture

**Authentication Flow:**
- JWT validation only (no token generation — Registry is a consuming service)
- Algorithm: HMAC-SHA256 (shared secret with CodeOps-Server)
- Claims extracted: `sub` (userId), `teamId`, `roles` (List<String>), `permissions` (List<String>)
- Token validated by `JwtTokenValidator.validateToken(String token)` using jjwt library
- On validation failure: 401 Unauthorized (no body)

**Authorization Model:**
- Roles: `ADMIN` (only role checked in code)
- Permissions/authorities: `registry:read`, `registry:write`, `registry:delete`
- Pattern on controllers: `@PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:read|write|delete')")`
- 77 `@PreAuthorize` annotations across 10 controllers
- All read endpoints require `ADMIN` role or `registry:read`
- All write endpoints require `ADMIN` role or `registry:write`
- All delete endpoints require `ADMIN` role or `registry:delete`

**Security Filter Chain (SecurityConfig.java):**
1. `RequestCorrelationFilter` (Ordered.HIGHEST_PRECEDENCE) — MDC correlation ID
2. `RateLimitFilter` (before JwtAuthFilter) — IP-based rate limiting
3. `JwtAuthFilter` (before UsernamePasswordAuthenticationFilter) — JWT extraction/validation
4. Spring Security chain

**Public paths (permitAll):**
- `GET /health`
- `GET /swagger-ui/**`
- `GET /v3/api-docs/**`
- `GET /swagger-ui.html`

**All other paths:** Authenticated (`/api/v1/registry/**`)

**CORS Configuration (CorsConfig.java):**
- Origins: Configurable via `codeops.cors.allowed-origins` (comma-separated)
- Methods: GET, POST, PUT, DELETE, PATCH, OPTIONS
- Headers: Authorization, Content-Type, X-Correlation-ID
- Credentials: Allowed
- Max age: 3600s

**Rate Limiting (RateLimitFilter.java):**
- Scope: IP-based on `/api/v1/registry/**`
- Strategy: In-memory ConcurrentHashMap with time-windowed counters
- Limit: 100 requests per minute per IP
- Response on violation: 429 Too Many Requests

**SecurityUtils.java:**
- `getCurrentUserId()` → UUID from SecurityContext (JWT `sub` claim)
- `getCurrentTeamId()` → UUID from SecurityContext (JWT `teamId` claim)
- `hasRole(String role)` → boolean
- `hasPermission(String permission)` → boolean

---

## 11. Notification / Messaging Layer

No notification or messaging layer detected in this project.

---

## 12. Error Handling

**GlobalExceptionHandler.java** — `@RestControllerAdvice`

```
Exception Type                       → HTTP Status → Response Body
NotFoundException                    → 404         → {"message": "...", "timestamp": "..."}
ValidationException                  → 400         → {"message": "...", "timestamp": "..."}
AuthorizationException               → 403         → {"message": "...", "timestamp": "..."}
AccessDeniedException                → 403         → {"message": "Access denied", "timestamp": "..."}
MethodArgumentNotValidException      → 400         → {"message": "Validation failed: field: msg", "timestamp": "..."}
HttpMessageNotReadableException      → 400         → {"message": "Malformed request body", "timestamp": "..."}
NoResourceFoundException             → 404         → {"message": "Resource not found", "timestamp": "..."}
Exception (catch-all)                → 500         → {"message": "An unexpected error occurred", "timestamp": "..."}
```

**Exception hierarchy:**
- `CodeOpsRegistryException` (base) extends `RuntimeException`
  - `NotFoundException` — 404
  - `ValidationException` — 400
  - `AuthorizationException` — 403

Internal error details (stack traces, exception messages) are logged server-side but NOT exposed to clients in the catch-all handler.

---

## 13. Test Coverage

```
Unit Test Files:         26
Integration Test Files:  1 (BaseIntegrationTest.java — base class, 0 @Test methods)
Total @Test Methods:     548 (unit: 548, integration: 0)
```

- **Framework:** JUnit 5 + Mockito 5.21.0 (overridden for Java 25)
- **Unit tests:** H2 in-memory database (`application-test.yml`, `MODE=PostgreSQL`)
- **Controller tests:** `@WebMvcTest` with `MockMvc`, JWT tokens built manually via JJWT
- **Service tests:** `@ExtendWith(MockitoExtension.class)` with `@Mock` / `@InjectMocks`
- **Integration base:** `BaseIntegrationTest.java` configured for Testcontainers PostgreSQL (no concrete tests yet)
- **Test config:** `application-test.yml` (H2), `application-integration.yml` (Testcontainers)

---

## 14. Cross-Cutting Patterns & Conventions

- **Naming:** Controllers use REST verbs (`registerService`, `getServiceById`, `updateService`, `deleteService`). Services mirror controller names. DTOs use `*Request` / `*Response` suffixes.
- **Package structure:** Layer-based — config, controller, dto (request/response), entity (enums), exception, repository, security, service, util.
- **Base classes:** `BaseEntity` (UUID PK + audit timestamps) extended by all entities.
- **Error handling:** Services throw `NotFoundException`/`ValidationException`/`AuthorizationException` → caught by `GlobalExceptionHandler`.
- **Pagination:** `Pageable` parameter in controller methods → `PageResponse<T>` wrapper DTO.
- **Validation:** Jakarta Bean Validation annotations on request DTOs (`@NotBlank`, `@NotNull`, `@Size`). Business rule validation in services.
- **Constants:** `AppConstants.java` — slug pattern, slug min/max length, max dependencies per service, max workstation profiles per team, default port ranges per PortType.
- **Logging:** `@Slf4j` on all services and security classes. `LoggingInterceptor` logs method/URI/status/duration. `RequestCorrelationFilter` sets MDC correlationId/requestPath/requestMethod.
- **Documentation comments:** Javadoc present on ~19/55 non-DTO/entity classes. ~43/111 public methods in services/controllers/security have Javadoc.
- **Transactional:** `@Transactional(readOnly = true)` at class level on services; `@Transactional` on mutation methods.

---

## 15. Known Issues, TODOs, and Technical Debt

No TODO, FIXME, HACK, XXX, WORKAROUND, or TEMPORARY comments found in `src/`.

---

## 16. OpenAPI Specification

The OpenAPI spec is generated by SpringDoc (`springdoc-openapi-starter-webmvc-ui` 2.5.0) from the running application. It is fetched from `http://localhost:8096/v3/api-docs.yaml` and saved as `openapi.yaml` in the project root.

See `openapi.yaml` for all endpoints, request/response DTOs, field types, and validation constraints.

---

## 17. Database — Live Schema Audit

Database not available for live audit. Schema documented from JPA entities only (Section 6).

Note: The PostgreSQL container runs on `127.0.0.1:5435`, database `codeops_registry`, user `codeops`. Connect via: `docker exec -it codeops-registry-db psql -U codeops -d codeops_registry`

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
| `DATABASE_URL` | Yes (prod) | None | application-prod.yml | PostgreSQL JDBC URL |
| `DATABASE_USERNAME` | Yes (prod) | None | application-prod.yml | Database username |
| `DATABASE_PASSWORD` | Yes (prod) | None | application-prod.yml | Database password |
| `DB_USERNAME` | No | `codeops` | application-dev.yml | Dev database username |
| `DB_PASSWORD` | No | `codeops` | application-dev.yml | Dev database password |
| `JWT_SECRET` | Yes (prod) | dev default (dev) | application-dev/prod.yml | HMAC-SHA256 JWT signing key |
| `CORS_ALLOWED_ORIGINS` | Yes (prod) | localhost (dev) | application-dev/prod.yml | Allowed CORS origins |
| `CODEOPS_SERVER_URL` | Yes (prod) | `http://localhost:8095` | application-dev/prod.yml | CodeOps-Server base URL |
| `CODEOPS_VAULT_URL` | Yes (prod) | `http://localhost:8097` | application-dev/prod.yml | CodeOps-Vault base URL |
| `CODEOPS_LOGGER_URL` | Yes (prod) | `http://localhost:8098` | application-dev/prod.yml | CodeOps-Logger base URL |

**Production warning:** All production variables are required with no defaults — deployment will fail if not set.

---

## 21. Inter-Service Communication Map

**Outbound HTTP calls:**
- `ServiceRegistryService` uses `RestTemplate` to call `service.getHealthCheckUrl()` for live health checks (arbitrary service URLs configured per service registration).

**Configured service URLs (not yet used in code beyond DataSeeder):**
- `ServiceUrlProperties.serverUrl` → CodeOps-Server (`http://localhost:8095`)
- `ServiceUrlProperties.vaultUrl` → CodeOps-Vault (`http://localhost:8097`)
- `ServiceUrlProperties.loggerUrl` → CodeOps-Logger (`http://localhost:8098`)

**Inbound dependencies:**
- CodeOps-Client (Flutter desktop app) calls Registry API endpoints.
- Other CodeOps services may consume Registry data.

**RestTemplate configuration:** `RestTemplateConfig.java` — 5s connect timeout, 10s read timeout.
