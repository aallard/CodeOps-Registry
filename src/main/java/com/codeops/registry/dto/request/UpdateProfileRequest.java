package com.codeops.registry.dto.request;

import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Request to update an existing workstation profile. */
public record UpdateProfileRequest(
    @Size(max = 100) String name,
    @Size(max = 5000) String description,
    UUID solutionId,
    String servicesJson,
    String startupOrder,
    Boolean isDefault
) {}
