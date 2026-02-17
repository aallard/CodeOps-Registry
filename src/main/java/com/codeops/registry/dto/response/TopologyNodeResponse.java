package com.codeops.registry.dto.response;

import com.codeops.registry.entity.enums.HealthStatus;
import com.codeops.registry.entity.enums.ServiceStatus;
import com.codeops.registry.entity.enums.ServiceType;

import java.util.List;
import java.util.UUID;

/** A node in the topology representing a service with dependency counts and layer classification. */
public record TopologyNodeResponse(
    UUID serviceId,
    String name,
    String slug,
    ServiceType serviceType,
    ServiceStatus status,
    HealthStatus healthStatus,
    int portCount,
    int upstreamDependencyCount,
    int downstreamDependencyCount,
    List<UUID> solutionIds,
    String layer
) {}
