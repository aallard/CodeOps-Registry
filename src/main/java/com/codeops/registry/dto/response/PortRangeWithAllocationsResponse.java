package com.codeops.registry.dto.response;

import com.codeops.registry.entity.enums.PortType;

import java.util.List;

/** A port range with its current allocations. */
public record PortRangeWithAllocationsResponse(
    PortType portType,
    Integer rangeStart,
    Integer rangeEnd,
    int totalCapacity,
    int allocated,
    int available,
    List<PortAllocationResponse> allocations
) {}
