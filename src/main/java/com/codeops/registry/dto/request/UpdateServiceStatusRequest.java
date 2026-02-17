package com.codeops.registry.dto.request;

import com.codeops.registry.entity.enums.ServiceStatus;
import jakarta.validation.constraints.NotNull;

/** Request to update a service's lifecycle status. */
public record UpdateServiceStatusRequest(
    @NotNull ServiceStatus status
) {}
