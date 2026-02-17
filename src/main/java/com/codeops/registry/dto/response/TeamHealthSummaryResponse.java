package com.codeops.registry.dto.response;

import com.codeops.registry.entity.enums.HealthStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Aggregated health summary for all services in a team. */
public record TeamHealthSummaryResponse(
    UUID teamId,
    int totalServices,
    int activeServices,
    int servicesUp,
    int servicesDown,
    int servicesDegraded,
    int servicesUnknown,
    int servicesNeverChecked,
    HealthStatus overallHealth,
    List<ServiceHealthResponse> unhealthyServices,
    Instant checkedAt
) {}
