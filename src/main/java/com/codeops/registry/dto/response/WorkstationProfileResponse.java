package com.codeops.registry.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response containing a workstation profile's full details. */
public record WorkstationProfileResponse(
    UUID id,
    UUID teamId,
    String name,
    String description,
    UUID solutionId,
    List<UUID> serviceIds,
    List<WorkstationServiceEntry> services,
    List<UUID> startupOrder,
    boolean isDefault,
    UUID createdByUserId,
    Instant createdAt,
    Instant updatedAt
) {}
