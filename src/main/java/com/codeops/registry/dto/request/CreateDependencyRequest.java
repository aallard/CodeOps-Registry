package com.codeops.registry.dto.request;

import com.codeops.registry.entity.enums.DependencyType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Request to declare a dependency between two services. */
public record CreateDependencyRequest(
    @NotNull UUID sourceServiceId,
    @NotNull UUID targetServiceId,
    @NotNull DependencyType dependencyType,
    @Size(max = 500) String description,
    Boolean isRequired,
    @Size(max = 500) String targetEndpoint
) {}
