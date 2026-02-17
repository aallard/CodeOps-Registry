package com.codeops.registry.dto.request;

import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Request to update an existing infrastructure resource. */
public record UpdateInfraResourceRequest(
    UUID serviceId,
    @Size(max = 300) String resourceName,
    @Size(max = 30) String region,
    @Size(max = 500) String arnOrUrl,
    String metadataJson,
    @Size(max = 500) String description
) {}
