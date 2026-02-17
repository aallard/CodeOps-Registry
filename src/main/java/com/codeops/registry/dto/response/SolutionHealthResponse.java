package com.codeops.registry.dto.response;

import com.codeops.registry.entity.enums.HealthStatus;

import java.util.List;
import java.util.UUID;

/** Aggregated health status across all services in a solution. */
public record SolutionHealthResponse(
    UUID solutionId,
    String solutionName,
    int totalServices,
    int servicesUp,
    int servicesDown,
    int servicesDegraded,
    int servicesUnknown,
    HealthStatus aggregatedHealth,
    List<ServiceHealthResponse> serviceHealths
) {}
