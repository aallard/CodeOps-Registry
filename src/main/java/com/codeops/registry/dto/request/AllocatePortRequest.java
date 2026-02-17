package com.codeops.registry.dto.request;

import com.codeops.registry.entity.enums.PortType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Request to manually allocate a specific port number to a service. */
public record AllocatePortRequest(
    @NotNull UUID serviceId,
    @NotBlank @Size(max = 50) String environment,
    @NotNull PortType portType,
    @NotNull Integer portNumber,
    @Size(max = 10) String protocol,
    @Size(max = 200) String description
) {}
