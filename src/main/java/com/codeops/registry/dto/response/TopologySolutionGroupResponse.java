package com.codeops.registry.dto.response;

import java.util.List;
import java.util.UUID;

/** Solution metadata for coloring and grouping nodes in the topology. */
public record TopologySolutionGroupResponse(
    UUID solutionId,
    String name,
    String colorHex,
    String iconName,
    List<UUID> memberServiceIds
) {}
