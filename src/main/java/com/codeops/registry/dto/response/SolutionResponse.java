package com.codeops.registry.dto.response;

import com.codeops.registry.entity.enums.SolutionCategory;
import com.codeops.registry.entity.enums.SolutionStatus;

import java.time.Instant;
import java.util.UUID;

/** Response containing a solution's summary details. */
public record SolutionResponse(
    UUID id,
    UUID teamId,
    String name,
    String slug,
    String description,
    SolutionCategory category,
    SolutionStatus status,
    String iconName,
    String colorHex,
    UUID ownerUserId,
    String repositoryUrl,
    String documentationUrl,
    String metadataJson,
    UUID createdByUserId,
    int memberCount,
    Instant createdAt,
    Instant updatedAt
) {}
