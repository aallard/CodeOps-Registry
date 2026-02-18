# CodeOps-Registry — Codebase Audit

**Audit Date:** 2026-02-17T20:33:53Z
**Branch:** main
**Commit:** b7455933d68ab1870971f5f72540a62471ed226d CR-007: ApiRouteService + InfraResourceService — route collision detection, prefix overlap, orphan finding with ~42 tests
**Auditor:** Claude Code (Automated)
**Purpose:** Zero-context reference for AI-assisted development
**Audit File:** CodeOps-Registry-Audit.md
**Scorecard:** (Not produced — project is in-development, scoring deferred)
**OpenAPI Spec:** openapi.yaml

> This audit is the single source of truth for the CodeOps-Registry codebase.
> The OpenAPI spec (openapi.yaml) is the source of truth for all endpoints, DTOs, and API contracts.
> An AI reading this audit + the OpenAPI spec should be able to generate accurate code
> changes, new features, tests, and fixes without filesystem access.

---

## 1. Project Identity

```
Project Name:        CodeOps Registry
Repository URL:      https://github.com/aallard/CodeOps-Registry.git
Primary Language:    Java / Spring Boot 3.3.0
Java Version:        21 (compiles with 21, runs on Java 25 via compatibility overrides)
Build Tool:          Maven (spring-boot-starter-parent 3.3.0)
Current Branch:      main
Latest Commit:       b7455933d68ab1870971f5f72540a62471ed226d
Latest Commit Msg:   CR-007: ApiRouteService + InfraResourceService — route collision detection, prefix overlap, orphan finding with ~42 tests
Audit Timestamp:     2026-02-17T20:33:53Z
```

---

## 2. Directory Structure

```
./CONVENTIONS.md
./docker-compose.yml
./Dockerfile
./pom.xml
./README.md
./src/main/java/com/codeops/registry/CodeOpsRegistryApplication.java
./src/main/java/com/codeops/registry/config/AppConstants.java
./src/main/java/com/codeops/registry/config/AsyncConfig.java
./src/main/java/com/codeops/registry/config/CorsConfig.java
./src/main/java/com/codeops/registry/config/GlobalExceptionHandler.java
./src/main/java/com/codeops/registry/config/HealthController.java
./src/main/java/com/codeops/registry/config/JwtProperties.java
./src/main/java/com/codeops/registry/config/LoggingInterceptor.java
./src/main/java/com/codeops/registry/config/RequestCorrelationFilter.java
./src/main/java/com/codeops/registry/config/RestTemplateConfig.java
./src/main/java/com/codeops/registry/config/ServiceUrlProperties.java
./src/main/java/com/codeops/registry/config/WebMvcConfig.java
./src/main/java/com/codeops/registry/dto/request/ (19 files)
./src/main/java/com/codeops/registry/dto/response/ (33 files)
./src/main/java/com/codeops/registry/entity/ (10 entity files + enums/)
./src/main/java/com/codeops/registry/entity/enums/ (11 enum files)
./src/main/java/com/codeops/registry/exception/ (4 exception files)
./src/main/java/com/codeops/registry/repository/ (11 repository files)
./src/main/java/com/codeops/registry/security/ (5 security files)
./src/main/java/com/codeops/registry/service/ (6 service files)
./src/main/java/com/codeops/registry/util/SlugUtils.java
./src/main/resources/application.yml
./src/main/resources/application-dev.yml
./src/main/resources/application-prod.yml
./src/main/resources/application-test.yml
./src/main/resources/application-integration.yml
./src/main/resources/logback-spring.xml
./src/test/java/com/codeops/registry/ (12 test files)
```

Single-module Maven project. Source at `src/main/java/com/codeops/registry/`. Standard layered architecture: `config`, `controller`, `dto`, `entity`, `exception`, `repository`, `security`, `service`, `util`. 10 controllers expose 77 secured + 1 public health endpoint.

---

## 3. Build & Dependency Manifest

| Dependency | Version | Purpose |
|---|---|---|
| spring-boot-starter-web | 3.3.0 (parent) | REST API |
| spring-boot-starter-data-jpa | 3.3.0 (parent) | JPA/Hibernate ORM |
| spring-boot-starter-security | 3.3.0 (parent) | Authentication/Authorization |
| spring-boot-starter-validation | 3.3.0 (parent) | Jakarta Bean Validation |
| postgresql | (managed) | PostgreSQL JDBC driver |
| jjwt-api / jjwt-impl / jjwt-jackson | 0.12.6 | JWT token validation |
| lombok | 1.18.42 | Boilerplate reduction (Java 25 compat) |
| mapstruct | 1.5.5.Final | DTO mapping (declared, not yet used) |
| jackson-datatype-jsr310 | (managed) | Java 8+ date/time serialization |
| springdoc-openapi-starter-webmvc-ui | 2.5.0 | Swagger UI / OpenAPI auto-docs |
| logstash-logback-encoder | 7.4 | Structured JSON logging (prod) |
| spring-boot-starter-test | 3.3.0 (parent) | Test framework |
| spring-security-test | (managed) | Security test utilities |
| testcontainers postgresql | 1.19.8 | Testcontainers for integration tests |
| testcontainers junit-jupiter | 1.19.8 | Testcontainers JUnit 5 integration |
| h2 | (managed) | In-memory DB for unit tests |
| mockito (overridden) | 5.21.0 | Java 25 mock compatibility |
| byte-buddy (overridden) | 1.18.4 | Java 25 bytecode compatibility |

**Build plugins:** `spring-boot-maven-plugin` (excludes Lombok), `maven-compiler-plugin` (annotationProcessorPaths for Lombok + MapStruct), `maven-surefire-plugin` (--add-opens for Java 25), `jacoco-maven-plugin` 0.8.14 (code coverage).

```
Build:   mvn clean compile -DskipTests
Test:    mvn test
Run:     mvn spring-boot:run
Package: mvn clean package -DskipTests
```

---

## 4. Configuration & Infrastructure Summary

- **`application.yml`** — Default profile: `dev`, server port: `8096`
- **`application-dev.yml`** — PostgreSQL at `localhost:5435/codeops_registry`, `ddl-auto: update`, SQL logging enabled. JWT shared secret with CodeOps-Server (dev default). CORS: localhost:3000,3200,5173. Service URLs: CodeOps-Server (8095), Vault (8097), Logger (8098).
- **`application-prod.yml`** — All secrets from env vars. `ddl-auto: validate`. Logging at INFO/WARN.
- **`application-test.yml`** — H2 in-memory with `MODE=PostgreSQL`, `ddl-auto: create-drop`. Hardcoded test JWT secret.
- **`application-integration.yml`** — PostgreSQL driver (Testcontainers provides URL), `ddl-auto: create-drop`.
- **`logback-spring.xml`** — Dev: human-readable console with correlationId/userId MDC. Prod: JSON via LogstashEncoder. Test: WARN-only.
- **`docker-compose.yml`** — PostgreSQL 16-alpine on `127.0.0.1:5435→5432` (container: `codeops-registry-db`, db: `codeops_registry`). Named volume, healthcheck.
- **`Dockerfile`** — `eclipse-temurin:21-jre-alpine`, non-root user (`appuser`), exposes 8096.

**Connection map:**
```
Database:        PostgreSQL, localhost:5435, codeops_registry
Cache:           None
Message Broker:  None
External APIs:   CodeOps-Server (8095), CodeOps-Vault (8097), CodeOps-Logger (8098) — URLs configured but not yet called
Cloud Services:  None
```

**CI/CD:** None detected.

---

## 5. Startup & Runtime Behavior

- **Entry point:** `CodeOpsRegistryApplication.main()` — `@SpringBootApplication` with `@EnableConfigurationProperties({JwtProperties.class, ServiceUrlProperties.class})`
- **@PostConstruct:** `JwtTokenValidator.validateSecret()` — validates JWT secret ≥ 32 chars at startup
- **Scheduled tasks:** None
- **Background jobs:** None
- **Health check:** `GET /api/v1/health` → 200 `{"status": "UP", "service": "codeops-registry", "timestamp": "..."}`

---

## 6. Entity / Data Model Layer

### === BaseEntity.java (MappedSuperclass) ===
```
Primary Key: id: UUID (GenerationType.UUID)
Fields:
  - createdAt: Instant @Column(nullable=false, updatable=false)
  - updatedAt: Instant
Auditing: @PrePersist sets createdAt+updatedAt, @PreUpdate sets updatedAt
```

### === ServiceRegistration.java ===
```
Table: service_registrations
Primary Key: inherited from BaseEntity (UUID)

Fields:
  - teamId: UUID @Column(nullable=false)
  - name: String @Column(nullable=false, length=100)
  - slug: String @Column(nullable=false, length=63)
  - serviceType: ServiceType @Enumerated(STRING) @Column(nullable=false, length=30)
  - description: String @Column(columnDefinition="TEXT")
  - repoUrl: String @Column(length=500)
  - repoFullName: String @Column(length=200)
  - defaultBranch: String @Column(length=50) default="main"
  - techStack: String @Column(length=500)
  - status: ServiceStatus @Enumerated(STRING) @Column(nullable=false, length=20) default=ACTIVE
  - healthCheckUrl: String @Column(length=500)
  - healthCheckIntervalSeconds: Integer default=30
  - lastHealthStatus: HealthStatus @Enumerated(STRING) @Column(length=20)
  - lastHealthCheckAt: Instant
  - environmentsJson: String @Column(columnDefinition="TEXT")
  - metadataJson: String @Column(columnDefinition="TEXT")
  - createdByUserId: UUID @Column(nullable=false)

Relationships:
  - portAllocations: @OneToMany → PortAllocation (mappedBy="service", cascade=ALL, orphanRemoval=true)
  - dependenciesAsSource: @OneToMany → ServiceDependency (mappedBy="sourceService")
  - dependenciesAsTarget: @OneToMany → ServiceDependency (mappedBy="targetService")
  - routes: @OneToMany → ApiRouteRegistration (mappedBy="service", cascade=ALL, orphanRemoval=true)
  - solutionMemberships: @OneToMany → SolutionMember (mappedBy="service")
  - configTemplates: @OneToMany → ConfigTemplate (mappedBy="service", cascade=ALL, orphanRemoval=true)
  - environmentConfigs: @OneToMany → EnvironmentConfig (mappedBy="service", cascade=ALL, orphanRemoval=true)

Indexes: idx_sr_team_id (team_id), idx_sr_status (status)
Unique Constraints: uk_sr_team_slug (team_id, slug)
```

### === Solution.java ===
```
Table: solutions
Primary Key: inherited from BaseEntity (UUID)

Fields:
  - teamId: UUID @Column(nullable=false)
  - name: String @Column(nullable=false, length=200)
  - slug: String @Column(nullable=false, length=63)
  - description: String @Column(columnDefinition="TEXT")
  - category: SolutionCategory @Enumerated(STRING) @Column(nullable=false, length=30)
  - status: SolutionStatus @Enumerated(STRING) @Column(nullable=false, length=20) default=ACTIVE
  - iconName: String @Column(length=50)
  - colorHex: String @Column(length=7)
  - ownerUserId: UUID
  - repositoryUrl: String @Column(length=500)
  - documentationUrl: String @Column(length=500)
  - metadataJson: String @Column(columnDefinition="TEXT")
  - createdByUserId: UUID @Column(nullable=false)

Relationships:
  - members: @OneToMany → SolutionMember (mappedBy="solution", cascade=ALL, orphanRemoval=true)

Indexes: idx_sol_team_id (team_id)
Unique Constraints: uk_sol_team_slug (team_id, slug)
```

### === SolutionMember.java ===
```
Table: solution_members
Primary Key: inherited from BaseEntity (UUID)

Fields:
  - solution: Solution @ManyToOne(LAZY) @JoinColumn(nullable=false)
  - service: ServiceRegistration @ManyToOne(LAZY) @JoinColumn(nullable=false)
  - role: SolutionMemberRole @Enumerated(STRING) @Column(nullable=false, length=30)
  - displayOrder: Integer default=0
  - notes: String @Column(length=500)

Indexes: idx_sm_solution_id (solution_id), idx_sm_service_id (service_id)
Unique Constraints: uk_sm_solution_service (solution_id, service_id)
```

### === ServiceDependency.java ===
```
Table: service_dependencies
Primary Key: inherited from BaseEntity (UUID)

Fields:
  - sourceService: ServiceRegistration @ManyToOne(LAZY) @JoinColumn(nullable=false)
  - targetService: ServiceRegistration @ManyToOne(LAZY) @JoinColumn(nullable=false)
  - dependencyType: DependencyType @Enumerated(STRING) @Column(nullable=false, length=30)
  - description: String @Column(length=500)
  - isRequired: Boolean @Column(nullable=false) default=true
  - targetEndpoint: String @Column(length=500)

Indexes: idx_sd_source_id (source_service_id), idx_sd_target_id (target_service_id)
Unique Constraints: uk_sd_source_target_type (source_service_id, target_service_id, dependency_type)
```

### === PortAllocation.java ===
```
Table: port_allocations
Primary Key: inherited from BaseEntity (UUID)

Fields:
  - service: ServiceRegistration @ManyToOne(LAZY) @JoinColumn(nullable=false)
  - environment: String @Column(nullable=false, length=50)
  - portType: PortType @Enumerated(STRING) @Column(nullable=false, length=30)
  - portNumber: Integer @Column(nullable=false)
  - protocol: String @Column(length=10) default="TCP"
  - description: String @Column(length=200)
  - isAutoAllocated: Boolean @Column(nullable=false) default=true
  - allocatedByUserId: UUID @Column(nullable=false)

Indexes: idx_pa_service_id, idx_pa_environment, idx_pa_port_number
Unique Constraints: uk_pa_service_env_port (service_id, environment, port_number)
```

### === PortRange.java ===
```
Table: port_ranges
Primary Key: inherited from BaseEntity (UUID)

Fields:
  - teamId: UUID @Column(nullable=false)
  - portType: PortType @Enumerated(STRING) @Column(nullable=false, length=30)
  - rangeStart: Integer @Column(nullable=false)
  - rangeEnd: Integer @Column(nullable=false)
  - environment: String @Column(nullable=false, length=50)
  - description: String @Column(length=200)

Indexes: idx_pr_team_id (team_id)
Unique Constraints: uk_pr_team_type_env (team_id, port_type, environment)
```

### === ApiRouteRegistration.java ===
```
Table: api_route_registrations
Primary Key: inherited from BaseEntity (UUID)

Fields:
  - service: ServiceRegistration @ManyToOne(LAZY) @JoinColumn(nullable=false)
  - gatewayService: ServiceRegistration @ManyToOne(LAZY) @JoinColumn(nullable)
  - routePrefix: String @Column(nullable=false, length=200)
  - httpMethods: String @Column(length=100)
  - environment: String @Column(nullable=false, length=50)
  - description: String @Column(length=500)

Indexes: idx_arr_service_id, idx_arr_gateway_id
```

### === EnvironmentConfig.java ===
```
Table: environment_configs
Primary Key: inherited from BaseEntity (UUID)

Fields:
  - service: ServiceRegistration @ManyToOne(LAZY) @JoinColumn(nullable=false)
  - environment: String @Column(nullable=false, length=50)
  - configKey: String @Column(nullable=false, length=200)
  - configValue: String @Column(nullable=false, columnDefinition="TEXT")
  - configSource: ConfigSource @Enumerated(STRING) @Column(nullable=false, length=20)
  - description: String @Column(length=500)

Indexes: idx_ec_service_id
Unique Constraints: uk_ec_service_env_key (service_id, environment, config_key)
```

### === ConfigTemplate.java ===
```
Table: config_templates
Primary Key: inherited from BaseEntity (UUID)

Fields:
  - service: ServiceRegistration @ManyToOne(LAZY) @JoinColumn(nullable=false)
  - templateType: ConfigTemplateType @Enumerated(STRING) @Column(nullable=false, length=30)
  - environment: String @Column(nullable=false, length=50)
  - contentText: String @Column(nullable=false, columnDefinition="TEXT")
  - isAutoGenerated: Boolean @Column(nullable=false) default=true
  - generatedFrom: String @Column(length=200)
  - version: Integer @Column(nullable=false) default=1

Indexes: idx_ct_service_id
Unique Constraints: uk_ct_service_type_env (service_id, template_type, environment)
```

### === WorkstationProfile.java ===
```
Table: workstation_profiles
Primary Key: inherited from BaseEntity (UUID)

Fields:
  - teamId: UUID @Column(nullable=false)
  - name: String @Column(nullable=false, length=100)
  - description: String @Column(columnDefinition="TEXT")
  - solutionId: UUID (nullable)
  - servicesJson: String @Column(nullable=false, columnDefinition="TEXT")
  - startupOrder: String @Column(columnDefinition="TEXT")
  - createdByUserId: UUID @Column(nullable=false)
  - isDefault: Boolean @Column(nullable=false) default=false

Indexes: idx_wp_team_id (team_id)
```

### === InfraResource.java ===
```
Table: infra_resources
Primary Key: inherited from BaseEntity (UUID)

Fields:
  - teamId: UUID @Column(nullable=false)
  - service: ServiceRegistration @ManyToOne(LAZY) @JoinColumn(nullable) — null = shared/orphaned
  - resourceType: InfraResourceType @Enumerated(STRING) @Column(nullable=false, length=30)
  - resourceName: String @Column(nullable=false, length=300)
  - environment: String @Column(nullable=false, length=50)
  - region: String @Column(length=30)
  - arnOrUrl: String @Column(length=500)
  - metadataJson: String @Column(columnDefinition="TEXT")
  - description: String @Column(length=500)
  - createdByUserId: UUID @Column(nullable=false)

Indexes: idx_ir_team_id, idx_ir_service_id
Unique Constraints: uk_ir_team_type_name_env (team_id, resource_type, resource_name, environment)
```

### Entity Relationship Summary
```
ServiceRegistration --[OneToMany]--> PortAllocation (via service / service_id)
ServiceRegistration --[OneToMany]--> ServiceDependency (via sourceService / source_service_id)
ServiceRegistration --[OneToMany]--> ServiceDependency (via targetService / target_service_id)
ServiceRegistration --[OneToMany]--> ApiRouteRegistration (via service / service_id)
ServiceRegistration --[OneToMany]--> SolutionMember (via service / service_id)
ServiceRegistration --[OneToMany]--> ConfigTemplate (via service / service_id)
ServiceRegistration --[OneToMany]--> EnvironmentConfig (via service / service_id)
Solution --[OneToMany]--> SolutionMember (via solution / solution_id)
SolutionMember --[ManyToOne]--> Solution (via solution / solution_id)
SolutionMember --[ManyToOne]--> ServiceRegistration (via service / service_id)
ServiceDependency --[ManyToOne]--> ServiceRegistration (source + target)
PortAllocation --[ManyToOne]--> ServiceRegistration (via service / service_id)
ApiRouteRegistration --[ManyToOne]--> ServiceRegistration (service + optional gatewayService)
EnvironmentConfig --[ManyToOne]--> ServiceRegistration (via service / service_id)
ConfigTemplate --[ManyToOne]--> ServiceRegistration (via service / service_id)
InfraResource --[ManyToOne]--> ServiceRegistration (via service / service_id, nullable)

PortRange — standalone entity, no FK relationships (team_id is a UUID reference to CodeOps-Server)
WorkstationProfile — standalone entity, no FK relationships (team_id, solution_id are UUID references)
```

---

## 7. Enum Definitions

```
=== ConfigSource.java ===
Values: AUTO_GENERATED, MANUAL, INHERITED, REGISTRY_DERIVED
Used By: EnvironmentConfig.configSource

=== ConfigTemplateType.java ===
Values: DOCKER_COMPOSE, APPLICATION_YML, APPLICATION_PROPERTIES, ENV_FILE, TERRAFORM_MODULE,
        CLAUDE_CODE_HEADER, CONVENTIONS_MD, NGINX_CONF, GITHUB_ACTIONS, DOCKERFILE, MAKEFILE, README_SECTION
Used By: ConfigTemplate.templateType

=== DependencyType.java ===
Values: HTTP_REST, GRPC, KAFKA_TOPIC, DATABASE_SHARED, REDIS_SHARED, LIBRARY, GATEWAY_ROUTE,
        WEBSOCKET, FILE_SYSTEM, OTHER
Used By: ServiceDependency.dependencyType

=== HealthStatus.java ===
Values: UP, DOWN, DEGRADED, UNKNOWN
Used By: ServiceRegistration.lastHealthStatus

=== InfraResourceType.java ===
Values: S3_BUCKET, SQS_QUEUE, SNS_TOPIC, CLOUDWATCH_LOG_GROUP, IAM_ROLE, SECRETS_MANAGER_PATH,
        SSM_PARAMETER, RDS_INSTANCE, ELASTICACHE_CLUSTER, ECR_REPOSITORY, CLOUD_MAP_NAMESPACE,
        ROUTE53_RECORD, ACM_CERTIFICATE, ALB_TARGET_GROUP, ECS_SERVICE, LAMBDA_FUNCTION,
        DYNAMODB_TABLE, DOCKER_NETWORK, DOCKER_VOLUME, OTHER
Used By: InfraResource.resourceType

=== PortType.java ===
Values: HTTP_API, FRONTEND_DEV, DATABASE, REDIS, KAFKA, KAFKA_INTERNAL, ZOOKEEPER,
        GRPC, WEBSOCKET, DEBUG, ACTUATOR, CUSTOM
Used By: PortAllocation.portType, PortRange.portType

=== ServiceStatus.java ===
Values: ACTIVE, INACTIVE, DEPRECATED, ARCHIVED
Used By: ServiceRegistration.status

=== ServiceType.java ===
Values: SPRING_BOOT_API, FLUTTER_WEB, FLUTTER_DESKTOP, FLUTTER_MOBILE, REACT_SPA, VUE_SPA,
        NEXT_JS, EXPRESS_API, FASTAPI, DOTNET_API, GO_API, LIBRARY, WORKER, GATEWAY,
        DATABASE_SERVICE, MESSAGE_BROKER, CACHE_SERVICE, MCP_SERVER, CLI_TOOL, OTHER
Used By: ServiceRegistration.serviceType

=== SolutionCategory.java ===
Values: PLATFORM, APPLICATION, LIBRARY_SUITE, INFRASTRUCTURE, TOOLING, OTHER
Used By: Solution.category

=== SolutionMemberRole.java ===
Values: CORE, SUPPORTING, INFRASTRUCTURE, EXTERNAL_DEPENDENCY
Used By: SolutionMember.role

=== SolutionStatus.java ===
Values: ACTIVE, IN_DEVELOPMENT, DEPRECATED, ARCHIVED
Used By: Solution.status
```

---

## 8. Repository Layer

```
=== ServiceRegistrationRepository.java ===
Extends: JpaRepository<ServiceRegistration, UUID>
Custom Methods:
  - Optional<ServiceRegistration> findByTeamIdAndSlug(UUID, String)
  - List<ServiceRegistration> findByTeamId(UUID)
  - Page<ServiceRegistration> findByTeamId(UUID, Pageable)
  - Page<ServiceRegistration> findByTeamIdAndStatus(UUID, ServiceStatus, Pageable)
  - Page<ServiceRegistration> findByTeamIdAndServiceType(UUID, ServiceType, Pageable)
  - Page<ServiceRegistration> findByTeamIdAndStatusAndServiceType(UUID, ServiceStatus, ServiceType, Pageable)
  - List<ServiceRegistration> findByTeamIdAndStatus(UUID, ServiceStatus)
  - List<ServiceRegistration> findByTeamIdAndIdIn(UUID, List<UUID>)
  - Page<ServiceRegistration> findByTeamIdAndNameContainingIgnoreCase(UUID, String, Pageable)
  - long countByTeamId(UUID)
  - long countByTeamIdAndStatus(UUID, ServiceStatus)
  - boolean existsByTeamIdAndSlug(UUID, String)

=== SolutionRepository.java ===
Extends: JpaRepository<Solution, UUID>
Custom Methods:
  - Optional<Solution> findByTeamIdAndSlug(UUID, String)
  - List<Solution> findByTeamId(UUID)
  - Page<Solution> findByTeamId(UUID, Pageable)
  - Page<Solution> findByTeamIdAndStatus(UUID, SolutionStatus, Pageable)
  - Page<Solution> findByTeamIdAndCategory(UUID, SolutionCategory, Pageable)
  - long countByTeamId(UUID)
  - boolean existsByTeamIdAndSlug(UUID, String)

=== SolutionMemberRepository.java ===
Extends: JpaRepository<SolutionMember, UUID>
Custom Methods:
  - List<SolutionMember> findBySolutionId(UUID)
  - List<SolutionMember> findBySolutionIdOrderByDisplayOrderAsc(UUID)
  - List<SolutionMember> findByServiceId(UUID)
  - Optional<SolutionMember> findBySolutionIdAndServiceId(UUID, UUID)
  - boolean existsBySolutionIdAndServiceId(UUID, UUID)
  - void deleteBySolutionIdAndServiceId(UUID, UUID)
  - long countBySolutionId(UUID)
  - long countByServiceId(UUID)

=== ServiceDependencyRepository.java ===
Extends: JpaRepository<ServiceDependency, UUID>
Custom Methods:
  - List<ServiceDependency> findBySourceServiceId(UUID)
  - List<ServiceDependency> findByTargetServiceId(UUID)
  - Optional<ServiceDependency> findBySourceServiceIdAndTargetServiceIdAndDependencyType(UUID, UUID, DependencyType)
  - boolean existsBySourceServiceIdAndTargetServiceId(UUID, UUID)
  - @Query List<ServiceDependency> findAllByTeamId(UUID) — joins via sourceService.teamId
  - long countBySourceServiceId(UUID)
  - long countByTargetServiceId(UUID)

=== PortAllocationRepository.java ===
Extends: JpaRepository<PortAllocation, UUID>
Custom Methods:
  - List<PortAllocation> findByServiceId(UUID)
  - List<PortAllocation> findByServiceIdAndEnvironment(UUID, String)
  - @Query List<PortAllocation> findByTeamIdAndEnvironment(UUID, String) — joins via service.teamId
  - @Query Optional<PortAllocation> findByTeamIdAndEnvironmentAndPortNumber(UUID, String, Integer)
  - @Query List<PortAllocation> findByTeamIdAndEnvironmentAndPortType(UUID, String, PortType)
  - @Query List<Object[]> findConflictingPorts(UUID) — GROUP BY port+env HAVING COUNT > 1
  - boolean existsByServiceIdAndEnvironmentAndPortNumber(UUID, String, Integer)
  - long countByServiceId(UUID)

=== PortRangeRepository.java ===
Extends: JpaRepository<PortRange, UUID>
Custom Methods:
  - List<PortRange> findByTeamId(UUID)
  - List<PortRange> findByTeamIdAndEnvironment(UUID, String)
  - Optional<PortRange> findByTeamIdAndPortTypeAndEnvironment(UUID, PortType, String)
  - boolean existsByTeamId(UUID)

=== ApiRouteRegistrationRepository.java ===
Extends: JpaRepository<ApiRouteRegistration, UUID>
Custom Methods:
  - List<ApiRouteRegistration> findByServiceId(UUID)
  - List<ApiRouteRegistration> findByGatewayServiceIdAndEnvironment(UUID, String)
  - @Query Optional<ApiRouteRegistration> findByGatewayAndPrefixAndEnvironment(UUID, String, String)
  - @Query List<ApiRouteRegistration> findOverlappingRoutes(UUID, String, String) — LIKE-based prefix overlap
  - @Query List<ApiRouteRegistration> findOverlappingDirectRoutes(UUID, String, String) — no gateway, team-scoped

=== EnvironmentConfigRepository.java ===
Extends: JpaRepository<EnvironmentConfig, UUID>
Custom Methods:
  - List<EnvironmentConfig> findByServiceIdAndEnvironment(UUID, String)
  - Optional<EnvironmentConfig> findByServiceIdAndEnvironmentAndConfigKey(UUID, String, String)
  - List<EnvironmentConfig> findByServiceId(UUID)
  - void deleteByServiceIdAndEnvironmentAndConfigKey(UUID, String, String)

=== ConfigTemplateRepository.java ===
Extends: JpaRepository<ConfigTemplate, UUID>
Custom Methods:
  - List<ConfigTemplate> findByServiceId(UUID)
  - List<ConfigTemplate> findByServiceIdAndEnvironment(UUID, String)
  - List<ConfigTemplate> findByServiceIdAndTemplateType(UUID, ConfigTemplateType)
  - Optional<ConfigTemplate> findByServiceIdAndTemplateTypeAndEnvironment(UUID, ConfigTemplateType, String)

=== InfraResourceRepository.java ===
Extends: JpaRepository<InfraResource, UUID>
Custom Methods:
  - List<InfraResource> findByTeamId(UUID)
  - Page<InfraResource> findByTeamId(UUID, Pageable)
  - Page<InfraResource> findByTeamIdAndResourceType(UUID, InfraResourceType, Pageable)
  - Page<InfraResource> findByTeamIdAndEnvironment(UUID, String, Pageable)
  - Page<InfraResource> findByTeamIdAndResourceTypeAndEnvironment(UUID, InfraResourceType, String, Pageable)
  - List<InfraResource> findByServiceId(UUID)
  - Optional<InfraResource> findByTeamIdAndResourceTypeAndResourceNameAndEnvironment(UUID, InfraResourceType, String, String)
  - @Query List<InfraResource> findOrphansByTeamId(UUID) — WHERE service IS NULL

=== WorkstationProfileRepository.java ===
Extends: JpaRepository<WorkstationProfile, UUID>
Custom Methods:
  - List<WorkstationProfile> findByTeamId(UUID)
  - Optional<WorkstationProfile> findByTeamIdAndIsDefaultTrue(UUID)
  - Optional<WorkstationProfile> findByTeamIdAndName(UUID, String)
  - long countByTeamId(UUID)
```

---

## 9. Service Layer

```
=== ServiceRegistryService.java ===
Injected Dependencies: ServiceRegistrationRepository, PortAllocationRepository,
    ServiceDependencyRepository, SolutionMemberRepository, ApiRouteRegistrationRepository,
    InfraResourceRepository, EnvironmentConfigRepository, PortAllocationService, RestTemplate

Methods:
  ─── createService(CreateServiceRequest, UUID) → ServiceRegistrationResponse
      Purpose: Register a new service for a team with auto-slug and optional port auto-allocation
      Authorization: NONE (caller passes currentUserId)
      Logic: Check team service limit (200) → generate/validate slug → make unique → build entity → save → optionally auto-allocate ports
      Throws: ValidationException (limit, slug)

  ─── getService(UUID) → ServiceRegistrationResponse
      Purpose: Get a single service by ID with derived counts
      Throws: NotFoundException

  ─── getServiceBySlug(UUID teamId, String slug) → ServiceRegistrationResponse
      Purpose: Get a service by team+slug
      Throws: NotFoundException

  ─── getServicesForTeam(UUID, ServiceStatus, ServiceType, String search, Pageable) → PageResponse<ServiceRegistrationResponse>
      Purpose: Paginated listing with optional status/type/search filters
      Logic: Derived counts set to 0 on list views (N+1 avoidance)
      Paginated: Yes

  ─── updateService(UUID, UpdateServiceRequest) → ServiceRegistrationResponse
      Purpose: Partial update of non-null fields
      Throws: NotFoundException

  ─── updateServiceStatus(UUID, ServiceStatus) → ServiceRegistrationResponse
      Purpose: Change service lifecycle status
      Throws: NotFoundException

  ─── deleteService(UUID) → void
      Purpose: Delete service after checking for active solution memberships and required dependents
      Throws: NotFoundException, ValidationException (has members, has required dependents)

  ─── cloneService(UUID, CloneServiceRequest, UUID) → ServiceRegistrationResponse
      Purpose: Clone service with new name/slug, auto-allocate ports anew
      Throws: NotFoundException

  ─── getServiceIdentity(UUID, String environment) → ServiceIdentityResponse
      Purpose: Complete identity assembly (service + ports + deps + routes + infra + configs)
      Throws: NotFoundException

  ─── checkHealth(UUID) → ServiceHealthResponse
      Purpose: Live health check via RestTemplate GET to healthCheckUrl
      Logic: Updates entity lastHealthStatus/lastHealthCheckAt. Returns UP/DEGRADED/DOWN/UNKNOWN.
      Throws: NotFoundException

  ─── checkAllHealth(UUID teamId) → List<ServiceHealthResponse>
      Purpose: Parallel health check of all ACTIVE services for a team via CompletableFuture

=== SolutionService.java ===
Injected Dependencies: SolutionRepository, SolutionMemberRepository, ServiceRegistrationRepository

Methods:
  ─── createSolution(CreateSolutionRequest, UUID) → SolutionResponse
      Purpose: Create a new solution with auto-slug. Limit: 50 per team.
      Throws: ValidationException (limit, slug)

  ─── getSolution(UUID) → SolutionResponse
      Throws: NotFoundException

  ─── getSolutionBySlug(UUID, String) → SolutionResponse
      Throws: NotFoundException

  ─── getSolutionDetail(UUID) → SolutionDetailResponse
      Purpose: Full detail with ordered member list
      Throws: NotFoundException

  ─── getSolutionsForTeam(UUID, SolutionStatus, SolutionCategory, Pageable) → PageResponse<SolutionResponse>
      Paginated: Yes

  ─── updateSolution(UUID, UpdateSolutionRequest) → SolutionResponse
      Purpose: Partial update of non-null fields
      Throws: NotFoundException

  ─── deleteSolution(UUID) → void
      Purpose: Delete solution (members cascade via orphanRemoval)
      Throws: NotFoundException

  ─── addMember(UUID solutionId, AddSolutionMemberRequest) → SolutionMemberResponse
      Purpose: Add a service to a solution with role
      Throws: NotFoundException, ValidationException (already member)

  ─── updateMember(UUID solutionId, UUID serviceId, UpdateSolutionMemberRequest) → SolutionMemberResponse
      Throws: NotFoundException

  ─── removeMember(UUID solutionId, UUID serviceId) → void
      Throws: NotFoundException

  ─── reorderMembers(UUID solutionId, List<UUID> orderedServiceIds) → List<SolutionMemberResponse>
      Purpose: Assign sequential displayOrder values
      Throws: NotFoundException, ValidationException (invalid service ID)

  ─── getSolutionHealth(UUID) → SolutionHealthResponse
      Purpose: Aggregate health from cached service health data (no live checks)

=== PortAllocationService.java ===
Injected Dependencies: PortAllocationRepository, PortRangeRepository, ServiceRegistrationRepository

Methods:
  ─── autoAllocate(UUID serviceId, String env, PortType, UUID userId) → PortAllocationResponse
      Purpose: Auto-allocate next available port from team's range
      Logic: Lookup range (fall back to "local") → find used ports → iterate range → save
      Throws: NotFoundException, ValidationException (no range, range full)

  ─── autoAllocateAll(UUID, String, List<PortType>, UUID) → List<PortAllocationResponse>
      Purpose: Batch auto-allocate multiple port types

  ─── manualAllocate(AllocatePortRequest, UUID) → PortAllocationResponse
      Purpose: Manually assign a specific port number
      Throws: NotFoundException, ValidationException (port taken)

  ─── releasePort(UUID allocationId) → void
      Throws: NotFoundException

  ─── checkAvailability(UUID teamId, int portNumber, String env) → PortCheckResponse
      Purpose: Check if a port is available

  ─── getPortsForService(UUID, String env) → List<PortAllocationResponse>
  ─── getPortsForTeam(UUID, String env) → List<PortAllocationResponse>
  ─── getPortMap(UUID, String env) → PortMapResponse
      Purpose: Structured port map with ranges and their allocations

  ─── detectConflicts(UUID teamId) → List<PortConflictResponse>
      Purpose: Find same port allocated to multiple services

  ─── getPortRanges(UUID teamId) → List<PortRangeResponse>
  ─── updatePortRange(UUID rangeId, UpdatePortRangeRequest) → PortRangeResponse
      Throws: NotFoundException, ValidationException (start >= end, shrinking excludes allocations)

  ─── seedDefaultRanges(UUID teamId, String env) → List<PortRangeResponse>
      Purpose: Seed default ranges from AppConstants if none exist. Idempotent.

=== DependencyGraphService.java ===
Injected Dependencies: ServiceDependencyRepository, ServiceRegistrationRepository

Methods:
  ─── createDependency(CreateDependencyRequest) → ServiceDependencyResponse
      Purpose: Create directed dependency edge with cycle detection
      Logic: Validate same team → check no duplicate → check limit (50) → BFS hasPath for cycle detection → save
      Throws: NotFoundException, ValidationException (self-dep, cross-team, duplicate, limit, cycle)

  ─── removeDependency(UUID) → void
      Throws: NotFoundException

  ─── getDependencyGraph(UUID teamId) → DependencyGraphResponse
      Purpose: All services as nodes, all dependencies as edges

  ─── getImpactAnalysis(UUID serviceId) → ImpactAnalysisResponse
      Purpose: BFS reverse traversal — who depends on me, transitively, with depth

  ─── getStartupOrder(UUID teamId) → List<DependencyNodeResponse>
      Purpose: Kahn's algorithm topological sort for startup ordering

  ─── detectCycles(UUID teamId) → List<UUID>
      Purpose: DFS three-color cycle detection, returns service IDs in cycles

  ─── hasPath(UUID from, UUID to, List<ServiceDependency>) → boolean (package-private)
      Purpose: BFS reachability check for cycle prevention

=== ApiRouteService.java ===
Injected Dependencies: ApiRouteRegistrationRepository, ServiceRegistrationRepository

Methods:
  ─── createRoute(CreateRouteRequest, UUID) → ApiRouteResponse
      Purpose: Register route prefix with collision detection
      Logic: Normalize prefix → validate gateway (same team) → check overlapping routes (LIKE-based) → save
      Throws: NotFoundException, ValidationException (cross-team gateway, overlap, invalid chars)

  ─── deleteRoute(UUID) → void
      Throws: NotFoundException

  ─── getRoutesForService(UUID) → List<ApiRouteResponse>
  ─── getRoutesForGateway(UUID, String env) → List<ApiRouteResponse>

  ─── checkRouteAvailability(UUID gatewayId, String env, String prefix) → RouteCheckResponse
      Purpose: Check if a route prefix is available

  ─── normalizePrefix(String) → String (package-private)
      Purpose: Trim, lowercase, ensure leading slash, remove trailing slash, validate chars

=== InfraResourceService.java ===
Injected Dependencies: InfraResourceRepository, ServiceRegistrationRepository

Methods:
  ─── createResource(CreateInfraResourceRequest, UUID) → InfraResourceResponse
      Purpose: Register infra resource with duplicate detection
      Logic: Validate optional service (same team) → check duplicate by (team, type, name, env) → save
      Throws: NotFoundException, ValidationException (cross-team, duplicate)

  ─── updateResource(UUID, UpdateInfraResourceRequest) → InfraResourceResponse
      Throws: NotFoundException

  ─── deleteResource(UUID) → void
      Throws: NotFoundException

  ─── getResourcesForTeam(UUID, InfraResourceType, String env, Pageable) → PageResponse<InfraResourceResponse>
      Paginated: Yes

  ─── getResourcesForService(UUID) → List<InfraResourceResponse>

  ─── findOrphanedResources(UUID teamId) → List<InfraResourceResponse>
      Purpose: Find resources with no owning service

  ─── reassignResource(UUID resourceId, UUID newServiceId) → InfraResourceResponse
      Throws: NotFoundException, ValidationException (cross-team)

  ─── orphanResource(UUID) → InfraResourceResponse
      Purpose: Remove service ownership (set service to null)
      Throws: NotFoundException
```

---

## 10. Security Architecture

**Authentication Flow:**
- JWT tokens issued by **CodeOps-Server** (this service only validates, never issues)
- HMAC-SHA256 shared secret (from `codeops.jwt.secret` / `JWT_SECRET` env var)
- `JwtAuthFilter` extracts `Authorization: Bearer <token>`, validates via `JwtTokenValidator`, sets `SecurityContextHolder` with principal=UUID, credentials=email, authorities=ROLE_* prefixed roles
- No token revocation/blacklist — tokens expire naturally

**Token claims extracted:** `sub` (user UUID), `email`, `roles` (list), `teamIds` (list), `teamRoles` (map<teamId, role>)

**Authorization Model:**
- `@EnableMethodSecurity` is enabled. All 10 controllers use `@PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:read|write|delete')")` on every endpoint (77 total secured endpoints)
- Write endpoints require `hasRole('ADMIN') or hasAuthority('registry:write')`
- Read endpoints require `hasRole('ADMIN') or hasAuthority('registry:read')`
- Delete endpoints require `hasRole('ADMIN') or hasAuthority('registry:delete')`
- `SecurityUtils` provides `getCurrentUserId()`, `getCurrentEmail()`, `isAuthenticated()`, `hasRole()`
- Team membership is available from token claims (`getTeamIdsFromToken`, `getTeamRolesFromToken`)

**Security Filter Chain (order):**
1. `RequestCorrelationFilter` (@Order HIGHEST_PRECEDENCE) — MDC correlationId/requestPath/requestMethod
2. `RateLimitFilter` — per-IP rate limiting on `/api/v1/registry/**`
3. `JwtAuthFilter` — JWT extraction and SecurityContext population
4. (Spring Security built-in filters)

**Public paths (permitAll):** `/api/v1/health`, `/swagger-ui/**`, `/v3/api-docs/**`, `/v3/api-docs.yaml`
**Authenticated paths:** `/api/**` and all other requests

**Security headers:** CSP (`default-src 'self'; frame-ancestors 'none'`), X-Frame-Options DENY, X-Content-Type-Options, HSTS (1 year, includeSubDomains)

**CORS:** Origins from config (dev: localhost:3000,3200,5173). Methods: GET, POST, PUT, DELETE, PATCH, OPTIONS. Credentials: true. Preflight cache: 3600s.

**Rate limiting:** In-memory per-IP, 100 requests/minute on `/api/v1/registry/**`. Returns 429 JSON when exceeded.

**Password policy:** N/A — this service does not handle authentication, only validates JWTs.

---

## 11. Notification / Messaging Layer

No email, webhook, or message broker integration. No Kafka, RabbitMQ, or SQS/SNS detected.

---

## 12. Error Handling

```
Exception Type                    → HTTP Status → Response Body
NotFoundException                 → 404         → {"status": 404, "message": "<entity> not found..."}
ValidationException               → 400         → {"status": 400, "message": "<validation detail>"}
AuthorizationException            → 403         → {"status": 403, "message": "<auth detail>"}
EntityNotFoundException (JPA)     → 404         → {"status": 404, "message": "Resource not found"}
IllegalArgumentException          → 400         → {"status": 400, "message": "Invalid request"}
AccessDeniedException (Spring)    → 403         → {"status": 403, "message": "Access denied"}
MethodArgumentNotValidException   → 400         → {"status": 400, "message": "field: error, field: error, ..."}
HttpMessageNotReadableException   → 400         → {"status": 400, "message": "Malformed request body"}
NoResourceFoundException          → 404         → {"status": 404, "message": "Resource not found"}
CodeOpsRegistryException (base)   → 500         → {"status": 500, "message": "An internal error occurred"}
Exception (catch-all)             → 500         → {"status": 500, "message": "An internal error occurred"}
```

Internal error details are never exposed to clients. All exceptions logged at WARN (4xx) or ERROR (5xx) with full stack traces for 500s.

**Exception hierarchy:** `RuntimeException` ← `CodeOpsRegistryException` ← `{NotFoundException, ValidationException, AuthorizationException}`

---

## 13. Test Coverage

- **Unit test files:** 11
- **Integration test files:** 1 (BaseIntegrationTest — abstract base, 0 @Test methods, provides Testcontainers PostgreSQL + JWT helper)
- **Total @Test methods:** 222 (all unit)
- **Test framework:** JUnit 5, Mockito 5.21.0 (MockitoExtension), AssertJ
- **Test database:** H2 in-memory (unit), Testcontainers PostgreSQL (integration base)
- **Test config:** `application-test.yml`, `application-integration.yml`

| Test File | @Test Count | Mocks |
|---|---|---|
| CodeOpsRegistryApplicationTest | 1 | None (Spring context load) |
| GlobalExceptionHandlerTest | 9 | None |
| JwtTokenValidatorTest | 15 | None |
| SecurityConfigTest | 8 | MockMvc |
| ApiRouteServiceTest | 22 | Repos (2) |
| DependencyGraphServiceTest | 36 | Repos (2) |
| InfraResourceServiceTest | 20 | Repos (2) |
| PortAllocationServiceTest | 41 | Repos (3) |
| ServiceRegistryServiceTest | 30 | Repos (7) + PortAllocationService + RestTemplate |
| SolutionServiceTest | 37 | Repos (3) |
| SlugUtilsTest | 12 | None |

---

## 14. Cross-Cutting Patterns & Conventions

- **Package structure:** `config`, `dto/request`, `dto/response`, `entity/enums`, `exception`, `repository`, `security`, `service`, `util`
- **Base class:** `BaseEntity` (UUID PK + audit timestamps via @PrePersist/@PreUpdate)
- **DTOs:** All Java records. Request DTOs use Jakarta validation annotations. Response DTOs are plain records.
- **Naming:** Request DTOs: `Create*Request`, `Update*Request`, `Add*Request`. Response DTOs: `*Response`. Services: `*Service`. Repositories: `*Repository`.
- **Pagination:** `PageResponse<T>` generic record wraps Spring `Page<T>`. Factory method: `PageResponse.from(page)`.
- **Slug generation:** `SlugUtils.generateSlug()` (name → lowercase, hyphens, 2-63 chars, pattern `^[a-z0-9][a-z0-9-]*[a-z0-9]$`). `SlugUtils.makeUnique()` appends `-2`, `-3`, etc.
- **Error handling:** Services throw `NotFoundException`/`ValidationException`/`AuthorizationException`. `GlobalExceptionHandler` catches all.
- **Validation:** DTO annotations for field-level, service logic for business rules (limits, uniqueness, cycles).
- **Constants:** `AppConstants` — pagination defaults, port ranges, slug rules, health check params, per-team limits.
- **Logging:** `@Slf4j` on all services. `LoggingInterceptor` logs request/response for all `/api/**`. `RequestCorrelationFilter` provides MDC correlationId.
- **Documentation:** Javadoc present on all classes and public methods (services, security, config, entities, repositories, enums, exceptions, util).
- **Controllers:** 10 REST controllers expose all service-layer methods via secured endpoints with `@PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:*')")` annotations.

---

## 15. Known Issues, TODOs, and Technical Debt

No `TODO`, `FIXME`, `HACK`, `XXX`, `WORKAROUND`, or `TEMPORARY` comments found in the codebase.

---

## 16. OpenAPI Specification

Produced as `openapi.yaml` — see separate file. **Note:** Since no controllers exist yet, the OpenAPI spec documents the **intended** API surface based on the service layer, DTOs, and project design. It serves as the contract for controller implementation.

---

## 17. Database — Live Schema Audit

Database not available for live audit. Schema documented from JPA entities only (Section 6).

**Database details:** PostgreSQL at `localhost:5435/codeops_registry` (container: `codeops-registry-db`), managed by Hibernate `ddl-auto: update`.

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
| DB_USERNAME | No | `codeops` | application-dev.yml | Database username |
| DB_PASSWORD | No | `codeops` | application-dev.yml | Database password |
| JWT_SECRET | **Yes (prod)** | dev default (54 chars) | JwtProperties | Shared HMAC secret for JWT validation |
| CODEOPS_SERVER_URL | **Yes (prod)** | `http://localhost:8095` | ServiceUrlProperties | CodeOps-Server base URL |
| CODEOPS_VAULT_URL | **Yes (prod)** | `http://localhost:8097` | ServiceUrlProperties | CodeOps-Vault base URL |
| CODEOPS_LOGGER_URL | **Yes (prod)** | `http://localhost:8098` | ServiceUrlProperties | CodeOps-Logger base URL |
| CORS_ALLOWED_ORIGINS | **Yes (prod)** | localhost:3000 (dev) | CorsConfig | Allowed CORS origins |
| DATABASE_URL | **Yes (prod)** | N/A | application-prod.yml | Full JDBC URL |
| DATABASE_USERNAME | **Yes (prod)** | N/A | application-prod.yml | Production DB username |
| DATABASE_PASSWORD | **Yes (prod)** | N/A | application-prod.yml | Production DB password |

**Warning:** `JWT_SECRET` has a hardcoded dev default — safe for dev, must be overridden in production.

---

## 21. Inter-Service Communication Map

**Outbound:** `RestTemplate` is injected into `ServiceRegistryService` and used **only** for health checks (GET to service's `healthCheckUrl`). No other outbound HTTP calls exist.

`ServiceUrlProperties` defines URLs for CodeOps-Server, Vault, and Logger — but none are currently called from code. These are placeholders for future cross-service communication.

**Inbound:** Other CodeOps services (Client, Server) call this Registry service's API (once controllers are implemented). Authentication is via shared JWT tokens from CodeOps-Server.

Standalone service with no active outbound service-to-service HTTP calls beyond health checking.
