package com.codeops.registry.dto.response;

import java.util.List;

/** Response indicating whether a route prefix is available or has conflicts. */
public record RouteCheckResponse(
    String routePrefix,
    String environment,
    boolean available,
    List<ApiRouteResponse> conflictingRoutes
) {}
