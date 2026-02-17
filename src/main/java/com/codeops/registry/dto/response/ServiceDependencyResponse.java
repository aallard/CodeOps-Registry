package com.codeops.registry.dto.response;

import com.codeops.registry.entity.enums.DependencyType;

import java.time.Instant;
import java.util.UUID;

/** Response containing a service dependency relationship. */
public record ServiceDependencyResponse(
    UUID id,
    UUID sourceServiceId,
    String sourceServiceName,
    String sourceServiceSlug,
    UUID targetServiceId,
    String targetServiceName,
    String targetServiceSlug,
    DependencyType dependencyType,
    String description,
    Boolean isRequired,
    String targetEndpoint,
    Instant createdAt
) {}
