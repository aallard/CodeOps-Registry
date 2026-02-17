package com.codeops.registry.dto.response;

import java.util.List;
import java.util.UUID;

/** Result of an impact analysis showing all services affected by a source service failure. */
public record ImpactAnalysisResponse(
    UUID sourceServiceId,
    String sourceServiceName,
    List<ImpactedServiceResponse> impactedServices,
    int totalAffected
) {}
