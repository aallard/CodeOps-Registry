package com.codeops.registry.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Request to register an API route prefix for a service. */
public record CreateRouteRequest(
    @NotNull UUID serviceId,
    UUID gatewayServiceId,
    @NotBlank @Size(max = 200) String routePrefix,
    @Size(max = 100) String httpMethods,
    @NotBlank @Size(max = 50) String environment,
    @Size(max = 500) String description
) {}
