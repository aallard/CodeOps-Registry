package com.codeops.registry.dto.request;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Request to partially update a workstation profile. */
public record UpdateWorkstationProfileRequest(
    @Size(max = 100) String name,
    String description,
    List<UUID> serviceIds,
    Boolean isDefault
) {}
