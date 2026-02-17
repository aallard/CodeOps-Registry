package com.codeops.registry.dto.response;

import java.util.List;
import java.util.UUID;

/** Full dependency graph for visualization — nodes and edges. */
public record DependencyGraphResponse(
    UUID teamId,
    List<DependencyNodeResponse> nodes,
    List<DependencyEdgeResponse> edges
) {}
