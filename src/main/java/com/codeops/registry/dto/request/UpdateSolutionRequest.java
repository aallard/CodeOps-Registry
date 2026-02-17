package com.codeops.registry.dto.request;

import com.codeops.registry.entity.enums.SolutionCategory;
import com.codeops.registry.entity.enums.SolutionStatus;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Request to update an existing solution. */
public record UpdateSolutionRequest(
    @Size(max = 200) String name,
    @Size(max = 5000) String description,
    SolutionCategory category,
    SolutionStatus status,
    @Size(max = 50) String iconName,
    @Size(max = 7) String colorHex,
    UUID ownerUserId,
    @Size(max = 500) String repositoryUrl,
    @Size(max = 500) String documentationUrl,
    String metadataJson
) {}
