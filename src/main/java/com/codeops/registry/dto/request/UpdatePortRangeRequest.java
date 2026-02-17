package com.codeops.registry.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request to update a port range's start, end, or description. */
public record UpdatePortRangeRequest(
    @NotNull Integer rangeStart,
    @NotNull Integer rangeEnd,
    @Size(max = 200) String description
) {}
