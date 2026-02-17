package com.codeops.registry.dto.request;

import com.codeops.registry.entity.enums.ConfigTemplateType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Request to generate configuration templates for a service. */
public record GenerateConfigRequest(
    @NotNull List<ConfigTemplateType> types,
    @NotBlank @Size(max = 50) String environment
) {}
