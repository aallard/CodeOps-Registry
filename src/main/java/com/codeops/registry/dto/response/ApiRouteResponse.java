package com.codeops.registry.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Response containing an API route registration. */
public record ApiRouteResponse(
    UUID id,
    UUID serviceId,
    String serviceName,
    String serviceSlug,
    UUID gatewayServiceId,
    String gatewayServiceName,
    String routePrefix,
    String httpMethods,
    String environment,
    String description,
    Instant createdAt
) {}
