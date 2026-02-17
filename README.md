# CodeOps-Registry

Service Registry for the CodeOps AI-First Development Control Plane. Manages service registrations, port allocations, solutions, dependencies, API routes, infrastructure resources, configuration templates, topology/health, and workstation profiles.

## Quick Start

- **Port:** 8096
- **Database:** `codeops_registry` on PostgreSQL (localhost:5435)
- **Prerequisites:** Java 21, Maven, Docker

### Build

```bash
mvn clean compile
```

### Run

```bash
docker compose up -d
mvn spring-boot:run
```

### Test

```bash
mvn test
```

### API Docs

http://localhost:8096/swagger-ui.html
