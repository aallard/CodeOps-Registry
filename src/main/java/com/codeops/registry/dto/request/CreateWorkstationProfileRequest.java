package com.codeops.registry.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Request to create a new workstation profile. */
public record CreateWorkstationProfileRequest(
    @NotNull UUID teamId,
    @NotBlank @Size(max = 100) String name,
    String description,
    UUID solutionId,
    List<UUID> serviceIds,
    boolean isDefault
) {}
