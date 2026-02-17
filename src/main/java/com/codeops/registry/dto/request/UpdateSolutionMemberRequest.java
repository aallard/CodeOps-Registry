package com.codeops.registry.dto.request;

import com.codeops.registry.entity.enums.SolutionMemberRole;
import jakarta.validation.constraints.Size;

/** Request to update a solution member's role, order, or notes. */
public record UpdateSolutionMemberRequest(
    SolutionMemberRole role,
    Integer displayOrder,
    @Size(max = 500) String notes
) {}
