package com.codeops.registry.dto.response;

import java.util.List;
import java.util.UUID;

/** Quick summary statistics for a team's topology. */
public record TopologySummaryResponse(
    UUID teamId,
    int totalServices,
    int servicesUp,
    int servicesDown,
    int servicesDegraded,
    int servicesUnknown,
    int totalSolutions,
    List<SolutionSummaryResponse> solutions
) {}
