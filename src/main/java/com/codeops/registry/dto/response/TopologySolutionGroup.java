package com.codeops.registry.dto.response;

import com.codeops.registry.entity.enums.SolutionStatus;

import java.util.List;
import java.util.UUID;

/** Solution metadata for grouping nodes in the topology. */
public record TopologySolutionGroup(
    UUID solutionId,
    String name,
    String slug,
    SolutionStatus status,
    int memberCount,
    List<UUID> serviceIds
) {}
