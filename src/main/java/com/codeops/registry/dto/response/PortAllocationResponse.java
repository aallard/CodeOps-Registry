package com.codeops.registry.dto.response;

import com.codeops.registry.entity.enums.PortType;

import java.time.Instant;
import java.util.UUID;

/** Response containing a port allocation's details. */
public record PortAllocationResponse(
    UUID id,
    UUID serviceId,
    String serviceName,
    String serviceSlug,
    String environment,
    PortType portType,
    Integer portNumber,
    String protocol,
    String description,
    Boolean isAutoAllocated,
    UUID allocatedByUserId,
    Instant createdAt
) {}
