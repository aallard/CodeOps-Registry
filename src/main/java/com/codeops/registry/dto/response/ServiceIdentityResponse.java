package com.codeops.registry.dto.response;

import java.util.List;

/** Complete service identity in a single payload — ports, dependencies, routes, infra, and configs. */
public record ServiceIdentityResponse(
    ServiceRegistrationResponse service,
    List<PortAllocationResponse> ports,
    List<ServiceDependencyResponse> upstreamDependencies,
    List<ServiceDependencyResponse> downstreamDependencies,
    List<ApiRouteResponse> routes,
    List<InfraResourceResponse> infraResources,
    List<EnvironmentConfigResponse> environmentConfigs
) {}
