package com.codeops.registry.dto.response;

import com.codeops.registry.entity.enums.HealthStatus;
import com.codeops.registry.entity.enums.ServiceStatus;
import com.codeops.registry.entity.enums.ServiceType;

import java.time.Instant;
import java.util.UUID;

/** Response containing a service registration's full details. */
public record ServiceRegistrationResponse(
    UUID id,
    UUID teamId,
    String name,
    String slug,
    ServiceType serviceType,
    String description,
    String repoUrl,
    String repoFullName,
    String defaultBranch,
    String techStack,
    ServiceStatus status,
    String healthCheckUrl,
    Integer healthCheckIntervalSeconds,
    HealthStatus lastHealthStatus,
    Instant lastHealthCheckAt,
    String environmentsJson,
    String metadataJson,
    UUID createdByUserId,
    int portCount,
    int dependencyCount,
    int solutionCount,
    Instant createdAt,
    Instant updatedAt
) {}
