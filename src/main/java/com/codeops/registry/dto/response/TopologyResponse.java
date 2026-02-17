package com.codeops.registry.dto.response;

import java.util.List;
import java.util.UUID;

/** Full topology for visualization — nodes, edges, solution groupings, layers, and stats. */
public record TopologyResponse(
    UUID teamId,
    List<TopologyNodeResponse> nodes,
    List<DependencyEdgeResponse> edges,
    List<TopologySolutionGroup> solutionGroups,
    List<TopologyLayerResponse> layers,
    TopologyStatsResponse stats
) {}
