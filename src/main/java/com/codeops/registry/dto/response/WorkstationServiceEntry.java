package com.codeops.registry.dto.response;

import com.codeops.registry.entity.enums.HealthStatus;
import com.codeops.registry.entity.enums.ServiceStatus;
import com.codeops.registry.entity.enums.ServiceType;

import java.util.UUID;

/** Enriched service details within a workstation profile. */
public record WorkstationServiceEntry(
    UUID serviceId,
    String name,
    String slug,
    ServiceType serviceType,
    ServiceStatus status,
    HealthStatus healthStatus,
    int startupPosition
) {}
