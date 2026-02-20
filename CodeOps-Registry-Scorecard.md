# CodeOps-Registry — Quality Scorecard

**Audit Date:** 2026-02-20T23:02:49Z
**Branch:** main
**Commit:** b92e9d1d
**Auditor:** Claude Code (Automated)

---

## Security (10 checks, max 20)

| Check | Score | Notes |
|---|---|---|
| SEC-01 Auth on all mutation endpoints | 2 | 77 @PreAuthorize annotations across 10 controllers; every endpoint (77/77) protected |
| SEC-02 No hardcoded secrets in source | 2 | 0 hardcoded secrets found. Dev JWT secret is profile-gated (dev only) |
| SEC-03 Input validation on all request DTOs | 2 | 87 validation annotations across 21 request DTOs. All string fields constrained |
| SEC-04 CORS not using wildcards | 2 | No wildcard origins. Configured via property with explicit origins |
| SEC-05 Encryption key not hardcoded | 2 | JWT secret from env var in prod. Dev default is profile-gated |
| SEC-06 Security headers configured | 2 | CSP, X-Frame-Options DENY, X-Content-Type-Options, HSTS (4 header configs) |
| SEC-07 Rate limiting present | 2 | RateLimitFilter: 100 req/min per IP on /api/v1/registry/** |
| SEC-08 SSRF protection on outbound URLs | 0 | **No SSRF protection on health check URLs.** RestTemplate calls user-provided URLs without validation |
| SEC-09 Token revocation / logout | 0 | **No token revocation or blacklist.** Stateless JWT only — tokens valid until expiry |
| SEC-10 Password complexity enforcement | 0 | N/A — Registry service does not manage passwords |

**Security Score: 14 / 20 (70%)**

---

## Data Integrity (8 checks, max 16)

| Check | Score | Notes |
|---|---|---|
| DAT-01 All enum fields use @Enumerated(STRING) | 2 | 12 @Enumerated(STRING) annotations. All enum fields properly mapped |
| DAT-02 Database indexes on FK columns | 2 | 18 @Index annotations covering all FK and query columns |
| DAT-03 Nullable constraints on required fields | 2 | 55 nullable=false constraints on entity fields |
| DAT-04 Optimistic locking (@Version) | 0 | **No @Version on any entity.** Concurrent updates could overwrite data |
| DAT-05 No unbounded queries | 1 | 23 unbounded List queries in repositories; most are scoped by teamId/serviceId but no hard size limit |
| DAT-06 No in-memory filtering of DB results | 1 | 1 instance of in-memory stream filtering in services; most filtering done in repository queries |
| DAT-07 Proper relationship mapping | 2 | JPA relationships used throughout. No comma-separated ID patterns |
| DAT-08 Audit timestamps on entities | 2 | BaseEntity with @PrePersist/@PreUpdate across all 10 entities |

**Data Integrity Score: 12 / 16 (75%)**

---

## API Quality (8 checks, max 16)

| Check | Score | Notes |
|---|---|---|
| API-01 Consistent error responses | 2 | GlobalExceptionHandler with 11 exception handlers. Structured ErrorResponse |
| API-02 Error messages sanitized | 1 | 12 uses of ex.getMessage() in handler — some expose business messages (NotFoundException, ValidationException) but 500s are masked |
| API-03 Audit logging on mutations | 1 | No dedicated audit log service, but LoggingInterceptor logs all requests with method/URI/status/duration/correlationId |
| API-04 Pagination on list endpoints | 2 | 6 Pageable references in controllers; list endpoints use PageResponse wrapper |
| API-05 Correct HTTP status codes | 2 | 201 for creates, 204 for deletes, 200 for updates/reads. 20 status code references |
| API-06 OpenAPI / Swagger documented | 2 | springdoc-openapi-starter-webmvc-ui 2.5.0 with OpenApiConfig |
| API-07 Consistent DTO naming | 2 | 53 files following *Request.java/*Response.java convention |
| API-08 File upload validation | 0 | N/A — no file upload endpoints |

**API Quality Score: 12 / 16 (75%)**

---

## Code Quality (10 checks, max 20)

| Check | Score | Notes |
|---|---|---|
| CQ-01 No getReferenceById | 2 | 0 uses. All lookups use findById with proper Optional handling |
| CQ-02 Consistent exception hierarchy | 2 | 4 exceptions: CodeOpsRegistryException (base) → NotFoundException, ValidationException, AuthorizationException |
| CQ-03 No TODO/FIXME/HACK | 2 | 0 found in source |
| CQ-04 Constants centralized | 2 | AppConstants with pagination, port ranges, slugs, health, limits, topology |
| CQ-05 Async exception handling | 2 | AsyncConfig with AsyncUncaughtExceptionHandler (5 references) |
| CQ-06 RestTemplate injected (not new'd) | 2 | 0 instances of `new RestTemplate()`. Bean injection via RestTemplateConfig |
| CQ-07 Logging in services/security | 2 | 33 logger declarations (@Slf4j/LoggerFactory) across services, controllers, security, config |
| CQ-08 No raw exception messages to clients | 2 | 0 uses of ex.getMessage() in controllers (all in GlobalExceptionHandler) |
| CQ-09 Doc comments on classes | 2 | All non-DTO, non-enum, non-entity classes have comprehensive Javadoc |
| CQ-10 Doc comments on public methods | 2 | All public methods in services, controllers, and security classes have Javadoc |

**Code Quality Score: 20 / 20 (100%)**

---

## Test Quality (10 checks, max 20)

| Check | Score | Notes |
|---|---|---|
| TST-01 Unit test files | 2 | 26 unit test files covering all services, controllers, security, config, util |
| TST-02 Integration test files | 1 | 1 base class (BaseIntegrationTest) exists but has 0 @Test methods |
| TST-03 Real database in ITs | 1 | Testcontainers configured (3 references) but no integration tests execute against it |
| TST-04 Source-to-test ratio | 2 | 26 unit tests / 25 source files (controllers + services + security) = 1.04:1 |
| TST-05 Code coverage >= 80% | 1 | Coverage report not generated (app not running). 548 @Test methods suggest good coverage |
| TST-06 Test config exists | 0 | **No application-test.yml in src/test/resources.** Only in src/main/resources |
| TST-07 Security tests | 2 | 171 security test annotations (@WithMockUser, @WithAnonymousUser, Bearer) |
| TST-08 Auth flow e2e | 0 | **No integration tests for auth flow** (0 @Test in IT files) |
| TST-09 DB state verification in ITs | 0 | **No integration tests with DB verification** (no IT @Test methods) |
| TST-10 Total @Test methods | 2 | 548 unit + 0 integration = 548 total |

**Test Quality Score: 11 / 20 (55%)**

---

## Infrastructure (6 checks, max 12)

| Check | Score | Notes |
|---|---|---|
| INF-01 Non-root Dockerfile | 2 | Dockerfile creates appuser:appgroup, runs as non-root |
| INF-02 DB ports localhost only | 2 | docker-compose binds to 127.0.0.1:5435:5432 |
| INF-03 Env vars for prod secrets | 2 | 8 ${...} references in application-prod.yml |
| INF-04 Health check endpoint | 2 | /api/v1/health (public, returns JSON status) |
| INF-05 Structured logging | 2 | LogstashEncoder in logback-spring.xml for prod profile |
| INF-06 CI/CD config | 0 | **No CI/CD pipeline detected** |

**Infrastructure Score: 10 / 12 (83%)**

---

## Scorecard Summary

| Category | Score | Max | % |
|---|---|---|---|
| Security | 14 | 20 | 70% |
| Data Integrity | 12 | 16 | 75% |
| API Quality | 12 | 16 | 75% |
| Code Quality | 20 | 20 | 100% |
| Test Quality | 11 | 20 | 55% |
| Infrastructure | 10 | 12 | 83% |
| **OVERALL** | **79** | **104** | **76%** |

**Grade: B (70-84%)**

---

## Categories Below 60%

### Test Quality (55%) — Failing Checks

- **TST-02** (partial): Integration test base class exists but has no actual test methods
- **TST-06** (0): No test-specific config in `src/test/resources/`
- **TST-08** (0): **BLOCKING** — No end-to-end auth flow tests
- **TST-09** (0): **BLOCKING** — No database state verification in integration tests

### Blocking Issues (Score = 0)

| Check | Category | Impact | Remediation |
|---|---|---|---|
| SEC-08 | Security | Health check URLs accept arbitrary user input → SSRF risk | Add URL validation (block private/loopback ranges) in ServiceRegistryService.checkHealth |
| SEC-09 | Security | Tokens cannot be revoked before expiry | Acceptable for development; add Redis-backed blacklist for production |
| DAT-04 | Data Integrity | No optimistic locking → lost updates under concurrent writes | Add `@Version private Long version` to BaseEntity |
| TST-08 | Test Quality | No integration tests verify auth flow end-to-end | Add IT extending BaseIntegrationTest with real JWT + DB |
| TST-09 | Test Quality | No integration tests verify database state after operations | Add IT with repository assertions |
| INF-06 | Infrastructure | No CI/CD pipeline | Add GitHub Actions workflow for build + test + lint |
