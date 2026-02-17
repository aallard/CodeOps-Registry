package com.codeops.registry.dto.response;

import com.codeops.registry.entity.enums.HealthStatus;

import java.util.UUID;

/** Summary of a solution's health for topology overview. */
public record SolutionSummaryResponse(
    UUID solutionId,
    String name,
    String colorHex,
    int memberCount,
    int membersUp,
    int membersDown,
    int membersDegraded,
    HealthStatus aggregatedHealth
) {}
