package com.codeops.registry.dto.response;

import com.codeops.registry.entity.enums.DependencyType;

import java.util.UUID;

/** An edge in the topology representing a dependency between two services. */
public record TopologyEdgeResponse(
    UUID sourceServiceId,
    UUID targetServiceId,
    DependencyType dependencyType,
    Boolean isRequired,
    String targetEndpoint
) {}
