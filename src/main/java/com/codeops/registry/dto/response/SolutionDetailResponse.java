package com.codeops.registry.dto.response;

import com.codeops.registry.entity.enums.SolutionCategory;
import com.codeops.registry.entity.enums.SolutionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Solution with full member list for detail views. */
public record SolutionDetailResponse(
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
    List<SolutionMemberResponse> members,
    Instant createdAt,
    Instant updatedAt
) {}
