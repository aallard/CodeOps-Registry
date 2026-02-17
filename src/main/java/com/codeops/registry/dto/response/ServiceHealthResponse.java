package com.codeops.registry.dto.response;

import com.codeops.registry.entity.enums.HealthStatus;

import java.time.Instant;
import java.util.UUID;

/** Health check result for a single service. */
public record ServiceHealthResponse(
    UUID serviceId,
    String name,
    String slug,
    HealthStatus healthStatus,
    Instant lastCheckAt,
    String healthCheckUrl,
    Integer responseTimeMs,
    String errorMessage
) {}
