package com.codeops.registry.controller;

import com.codeops.registry.dto.response.ConfigTemplateResponse;
import com.codeops.registry.entity.enums.ConfigTemplateType;
import com.codeops.registry.exception.ValidationException;
import com.codeops.registry.service.ConfigEngineService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for configuration generation, template retrieval, and management.
 *
 * <p>Generates Docker Compose, application.yml, and Claude Code context headers from
 * registry data. All endpoints require JWT authentication. Write operations require the
 * {@code ADMIN} role or the {@code registry:write} authority; read operations require
 * the {@code ADMIN} role or the {@code registry:read} authority; delete operations
 * require the {@code ADMIN} role or the {@code registry:delete} authority.</p>
 *
 * @see ConfigEngineService
 */
@RestController
@RequestMapping("/api/v1/registry")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Config")
public class ConfigController {

    private final ConfigEngineService configEngineService;

    /**
     * Generates a configuration template for a service based on the specified type.
     *
     * <p>Supported types: {@code DOCKER_COMPOSE}, {@code APPLICATION_YML},
     * {@code CLAUDE_CODE_HEADER}. Other types return a 400 validation error.</p>
     *
     * @param serviceId   the service ID
     * @param type        the config template type to generate
     * @param environment the target environment (defaults to "local")
     * @return a 200 response with the generated config template
     */
    @PostMapping("/services/{serviceId}/config/generate")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:write')")
    public ResponseEntity<ConfigTemplateResponse> generateConfig(
            @PathVariable UUID serviceId,
            @RequestParam ConfigTemplateType type,
            @RequestParam(defaultValue = "local") String environment) {
        ConfigTemplateResponse result = switch (type) {
            case DOCKER_COMPOSE -> configEngineService.generateDockerCompose(serviceId, environment);
            case APPLICATION_YML -> configEngineService.generateApplicationYml(serviceId, environment);
            case CLAUDE_CODE_HEADER -> configEngineService.generateClaudeCodeHeader(serviceId, environment);
            default -> throw new ValidationException(
                    "Unsupported config template type for generation: " + type);
        };
        return ResponseEntity.ok(result);
    }

    /**
     * Generates all three config types (Docker Compose, application.yml, Claude Code Header)
     * for a service in one call.
     *
     * @param serviceId   the service ID
     * @param environment the target environment (defaults to "local")
     * @return a 200 response with the list of generated config templates
     */
    @PostMapping("/services/{serviceId}/config/generate-all")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:write')")
    public ResponseEntity<List<ConfigTemplateResponse>> generateAllConfigs(
            @PathVariable UUID serviceId,
            @RequestParam(defaultValue = "local") String environment) {
        return ResponseEntity.ok(configEngineService.generateAllForService(serviceId, environment));
    }

    /**
     * Generates a complete docker-compose.yml for an entire solution (group of services).
     *
     * @param solutionId  the solution ID
     * @param environment the target environment (defaults to "local")
     * @return a 200 response with the generated docker-compose config template
     */
    @PostMapping("/solutions/{solutionId}/config/docker-compose")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:write')")
    public ResponseEntity<ConfigTemplateResponse> generateSolutionDockerCompose(
            @PathVariable UUID solutionId,
            @RequestParam(defaultValue = "local") String environment) {
        return ResponseEntity.ok(configEngineService.generateSolutionDockerCompose(
                solutionId, environment));
    }

    /**
     * Retrieves a previously generated config template.
     *
     * @param serviceId   the service ID
     * @param type        the template type (required)
     * @param environment the environment (required)
     * @return a 200 response with the config template
     */
    @GetMapping("/services/{serviceId}/config")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:read')")
    public ResponseEntity<ConfigTemplateResponse> getTemplate(
            @PathVariable UUID serviceId,
            @RequestParam ConfigTemplateType type,
            @RequestParam String environment) {
        return ResponseEntity.ok(configEngineService.getTemplate(serviceId, type, environment));
    }

    /**
     * Retrieves all templates for a service.
     *
     * @param serviceId the service ID
     * @return a 200 response with the list of config templates
     */
    @GetMapping("/services/{serviceId}/config/all")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:read')")
    public ResponseEntity<List<ConfigTemplateResponse>> getTemplatesForService(
            @PathVariable UUID serviceId) {
        return ResponseEntity.ok(configEngineService.getTemplatesForService(serviceId));
    }

    /**
     * Deletes a config template.
     *
     * @param templateId the template ID
     * @return a 204 response with no content
     */
    @DeleteMapping("/config/{templateId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:delete')")
    public ResponseEntity<Void> deleteTemplate(@PathVariable UUID templateId) {
        configEngineService.deleteTemplate(templateId);
        return ResponseEntity.noContent().build();
    }
}
