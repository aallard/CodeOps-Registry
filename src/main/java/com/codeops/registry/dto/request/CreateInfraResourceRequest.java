package com.codeops.registry.dto.request;

import com.codeops.registry.entity.enums.InfraResourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Request to register an infrastructure resource. */
public record CreateInfraResourceRequest(
    @NotNull UUID teamId,
    UUID serviceId,
    @NotNull InfraResourceType resourceType,
    @NotBlank @Size(max = 300) String resourceName,
    @NotBlank @Size(max = 50) String environment,
    @Size(max = 30) String region,
    @Size(max = 500) String arnOrUrl,
    String metadataJson,
    @Size(max = 500) String description
) {}
