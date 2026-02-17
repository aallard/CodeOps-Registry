package com.codeops.registry.dto.request;

import jakarta.validation.constraints.Size;

/** Request to update an existing service registration. */
public record UpdateServiceRequest(
    @Size(max = 100) String name,
    @Size(max = 5000) String description,
    @Size(max = 500) String repoUrl,
    @Size(max = 200) String repoFullName,
    @Size(max = 50) String defaultBranch,
    @Size(max = 500) String techStack,
    @Size(max = 500) String healthCheckUrl,
    Integer healthCheckIntervalSeconds,
    String environmentsJson,
    String metadataJson
) {}
