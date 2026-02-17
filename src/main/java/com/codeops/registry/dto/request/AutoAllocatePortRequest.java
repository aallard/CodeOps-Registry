package com.codeops.registry.dto.request;

import com.codeops.registry.entity.enums.PortType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Request to auto-allocate the next available port from the team's port range. */
public record AutoAllocatePortRequest(
    @NotNull UUID serviceId,
    @NotBlank @Size(max = 50) String environment,
    @NotNull PortType portType,
    @Size(max = 200) String description
) {}
