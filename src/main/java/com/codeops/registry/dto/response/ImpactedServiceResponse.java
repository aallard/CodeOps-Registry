package com.codeops.registry.dto.response;

import com.codeops.registry.entity.enums.DependencyType;

import java.util.UUID;

/** A service impacted by an upstream dependency, with depth and connection metadata. */
public record ImpactedServiceResponse(
    UUID serviceId,
    String serviceName,
    String serviceSlug,
    int depth,
    DependencyType connectionType,
    boolean isRequired
) {}
