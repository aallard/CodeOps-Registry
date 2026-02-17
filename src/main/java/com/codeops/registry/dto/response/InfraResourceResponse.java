package com.codeops.registry.dto.response;

import com.codeops.registry.entity.enums.InfraResourceType;

import java.time.Instant;
import java.util.UUID;

/** Response containing an infrastructure resource's details. */
public record InfraResourceResponse(
    UUID id,
    UUID teamId,
    UUID serviceId,
    String serviceName,
    String serviceSlug,
    InfraResourceType resourceType,
    String resourceName,
    String environment,
    String region,
    String arnOrUrl,
    String metadataJson,
    String description,
    UUID createdByUserId,
    Instant createdAt,
    Instant updatedAt
) {}
