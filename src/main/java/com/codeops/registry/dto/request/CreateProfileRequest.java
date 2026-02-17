package com.codeops.registry.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Request to create a workstation profile for local development. */
public record CreateProfileRequest(
    @NotNull UUID teamId,
    @NotBlank @Size(max = 100) String name,
    @Size(max = 5000) String description,
    UUID solutionId,
    @NotBlank String servicesJson,
    String startupOrder,
    Boolean isDefault
) {}
