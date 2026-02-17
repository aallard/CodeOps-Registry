package com.codeops.registry.dto.response;

import com.codeops.registry.entity.enums.ConfigSource;

import java.time.Instant;
import java.util.UUID;

/** Response containing an environment-specific configuration entry. */
public record EnvironmentConfigResponse(
    UUID id,
    UUID serviceId,
    String environment,
    String configKey,
    String configValue,
    ConfigSource configSource,
    String description,
    Instant createdAt,
    Instant updatedAt
) {}
