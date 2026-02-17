package com.codeops.registry.dto.response;

import com.codeops.registry.entity.enums.HealthStatus;
import com.codeops.registry.entity.enums.ServiceStatus;
import com.codeops.registry.entity.enums.ServiceType;

import java.util.UUID;

/** A node in the dependency graph representing a service. */
public record DependencyNodeResponse(
    UUID serviceId,
    String name,
    String slug,
    ServiceType serviceType,
    ServiceStatus status,
    HealthStatus healthStatus
) {}
