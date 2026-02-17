package com.codeops.registry.dto.request;

import com.codeops.registry.entity.enums.SolutionCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Request to create a new solution (logical grouping of services). */
public record CreateSolutionRequest(
    @NotNull UUID teamId,
    @NotBlank @Size(max = 200) String name,
    String slug,
    @Size(max = 5000) String description,
    @NotNull SolutionCategory category,
    @Size(max = 50) String iconName,
    @Size(max = 7) String colorHex,
    UUID ownerUserId,
    @Size(max = 500) String repositoryUrl,
    @Size(max = 500) String documentationUrl,
    String metadataJson
) {}
