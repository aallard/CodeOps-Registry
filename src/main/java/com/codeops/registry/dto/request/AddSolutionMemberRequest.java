package com.codeops.registry.dto.request;

import com.codeops.registry.entity.enums.SolutionMemberRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Request to add a service as a member of a solution. */
public record AddSolutionMemberRequest(
    @NotNull UUID serviceId,
    @NotNull SolutionMemberRole role,
    Integer displayOrder,
    @Size(max = 500) String notes
) {}
