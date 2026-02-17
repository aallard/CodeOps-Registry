package com.codeops.registry.dto.response;

import com.codeops.registry.entity.enums.HealthStatus;
import com.codeops.registry.entity.enums.ServiceStatus;
import com.codeops.registry.entity.enums.ServiceType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A node in the topology representing a service with its ports and solution memberships. */
public record TopologyNodeResponse(
    UUID serviceId,
    String name,
    String slug,
    ServiceType serviceType,
    ServiceStatus status,
    HealthStatus healthStatus,
    Instant lastHealthCheckAt,
    String healthCheckUrl,
    List<PortAllocationResponse> ports,
    List<UUID> solutionIds
) {}
