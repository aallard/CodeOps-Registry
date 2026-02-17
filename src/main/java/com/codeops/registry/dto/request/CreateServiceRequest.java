package com.codeops.registry.dto.request;

import com.codeops.registry.entity.enums.PortType;
import com.codeops.registry.entity.enums.ServiceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Request to register a new service. */
public record CreateServiceRequest(
    @NotNull UUID teamId,
    @NotBlank @Size(max = 100) String name,
    String slug,
    @NotNull ServiceType serviceType,
    @Size(max = 5000) String description,
    @Size(max = 500) String repoUrl,
    @Size(max = 200) String repoFullName,
    @Size(max = 50) String defaultBranch,
    @Size(max = 500) String techStack,
    @Size(max = 500) String healthCheckUrl,
    Integer healthCheckIntervalSeconds,
    String environmentsJson,
    String metadataJson,
    List<PortType> autoAllocatePortTypes,
    String autoAllocateEnvironment
) {}
