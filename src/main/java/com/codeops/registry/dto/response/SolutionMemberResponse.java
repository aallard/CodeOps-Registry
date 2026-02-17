package com.codeops.registry.dto.response;

import com.codeops.registry.entity.enums.HealthStatus;
import com.codeops.registry.entity.enums.ServiceStatus;
import com.codeops.registry.entity.enums.ServiceType;
import com.codeops.registry.entity.enums.SolutionMemberRole;

import java.util.UUID;

/** Response representing a service's membership in a solution. */
public record SolutionMemberResponse(
    UUID id,
    UUID solutionId,
    UUID serviceId,
    String serviceName,
    String serviceSlug,
    ServiceType serviceType,
    ServiceStatus serviceStatus,
    HealthStatus serviceHealthStatus,
    SolutionMemberRole role,
    Integer displayOrder,
    String notes
) {}
