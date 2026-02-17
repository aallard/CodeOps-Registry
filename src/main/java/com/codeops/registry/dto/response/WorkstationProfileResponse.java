package com.codeops.registry.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Response containing a workstation profile's details. */
public record WorkstationProfileResponse(
    UUID id,
    UUID teamId,
    String name,
    String description,
    UUID solutionId,
    String solutionName,
    String servicesJson,
    String startupOrder,
    Boolean isDefault,
    UUID createdByUserId,
    Instant createdAt,
    Instant updatedAt
) {}
