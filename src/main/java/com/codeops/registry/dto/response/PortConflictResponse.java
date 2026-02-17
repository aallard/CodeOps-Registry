package com.codeops.registry.dto.response;

import java.util.List;

/** Response listing conflicting port allocations for a given port number. */
public record PortConflictResponse(
    Integer portNumber,
    String environment,
    List<PortAllocationResponse> conflictingAllocations
) {}
