package com.codeops.registry.dto.response;

import java.util.List;
import java.util.UUID;

/** Visual port map showing ranges and their allocations for a team environment. */
public record PortMapResponse(
    UUID teamId,
    String environment,
    List<PortRangeWithAllocationsResponse> ranges,
    int totalAllocated,
    int totalAvailable
) {}
