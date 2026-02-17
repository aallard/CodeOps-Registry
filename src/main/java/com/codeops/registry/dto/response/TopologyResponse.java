package com.codeops.registry.dto.response;

import java.util.List;
import java.util.UUID;

/** Full topology for visualization — nodes, edges, and solution groupings. */
public record TopologyResponse(
    UUID teamId,
    String environment,
    List<TopologyNodeResponse> nodes,
    List<TopologyEdgeResponse> edges,
    List<TopologySolutionGroupResponse> solutionGroups
) {}
