package com.codeops.registry.dto.response;

import com.codeops.registry.entity.enums.PortType;

import java.util.UUID;

/** Response indicating whether a specific port is available or in use. */
public record PortCheckResponse(
    Integer portNumber,
    String environment,
    boolean available,
    UUID currentOwnerServiceId,
    String currentOwnerServiceName,
    PortType currentOwnerPortType
) {}
