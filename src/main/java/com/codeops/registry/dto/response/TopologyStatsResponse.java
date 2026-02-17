package com.codeops.registry.dto.response;

/** Aggregate statistics for a team's service topology. */
public record TopologyStatsResponse(
    int totalServices,
    int totalDependencies,
    int totalSolutions,
    int servicesWithNoDependencies,
    int servicesWithNoConsumers,
    int orphanedServices,
    int maxDependencyDepth
) {}
