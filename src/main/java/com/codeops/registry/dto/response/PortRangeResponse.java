package com.codeops.registry.dto.response;

import com.codeops.registry.entity.enums.PortType;

import java.util.UUID;

/** Response containing a port range definition. */
public record PortRangeResponse(
    UUID id,
    UUID teamId,
    PortType portType,
    Integer rangeStart,
    Integer rangeEnd,
    String environment,
    String description
) {}
