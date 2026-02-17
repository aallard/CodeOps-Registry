package com.codeops.registry.dto.request;

import com.codeops.registry.entity.enums.ConfigSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Request to create or update an environment-specific configuration entry. */
public record UpsertEnvironmentConfigRequest(
    @NotNull UUID serviceId,
    @NotBlank @Size(max = 50) String environment,
    @NotBlank @Size(max = 200) String configKey,
    @NotBlank String configValue,
    @NotNull ConfigSource configSource,
    @Size(max = 500) String description
) {}
