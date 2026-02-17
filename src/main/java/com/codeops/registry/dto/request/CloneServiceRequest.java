package com.codeops.registry.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request to clone an existing service registration under a new name. */
public record CloneServiceRequest(
    @NotBlank @Size(max = 100) String newName,
    String newSlug
) {}
