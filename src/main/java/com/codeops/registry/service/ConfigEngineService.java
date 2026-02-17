package com.codeops.registry.service;

import com.codeops.registry.config.AppConstants;
import com.codeops.registry.dto.response.ConfigTemplateResponse;
import com.codeops.registry.dto.response.DependencyNodeResponse;
import com.codeops.registry.entity.*;
import com.codeops.registry.entity.enums.ConfigTemplateType;
import com.codeops.registry.entity.enums.InfraResourceType;
import com.codeops.registry.entity.enums.PortType;
import com.codeops.registry.exception.NotFoundException;
import com.codeops.registry.exception.ValidationException;
import com.codeops.registry.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Configuration engine that generates configuration files from registry data.
 *
 * <p>Turns structured registry metadata (port allocations, dependencies, environment configs,
 * infrastructure resources, API routes) into ready-to-use configuration files for development
 * environments. Supports Docker Compose, Spring Boot application.yml, and Claude Code context
 * headers. Generated templates are stored as {@link ConfigTemplate} entities with upsert
 * semantics and version tracking.</p>
 *
 * @see ConfigTemplate
 * @see ConfigTemplateType
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ConfigEngineService {

    private final ServiceRegistrationRepository serviceRepository;
    private final PortAllocationRepository portAllocationRepository;
    private final ServiceDependencyRepository dependencyRepository;
    private final EnvironmentConfigRepository environmentConfigRepository;
    private final ConfigTemplateRepository configTemplateRepository;
    private final ApiRouteRegistrationRepository routeRepository;
    private final InfraResourceRepository infraResourceRepository;
    private final SolutionRepository solutionRepository;
    private final SolutionMemberRepository solutionMemberRepository;
    private final DependencyGraphService dependencyGraphService;

    /**
     * Generates a {@code docker-compose.yml} fragment for a single service based on its registry data.
     *
     * <p>Builds a compose service block including ports, environment variables, dependencies,
     * volumes from infrastructure resources, labels, and health check configuration. The result
     * is stored as a {@link ConfigTemplate} with upsert semantics (updates version if existing).</p>
     *
     * @param serviceId   the service ID
     * @param environment the target environment
     * @return the generated config template response
     * @throws NotFoundException if the service does not exist
     */
    @Transactional
    public ConfigTemplateResponse generateDockerCompose(UUID serviceId, String environment) {
        log.info("Generating DOCKER_COMPOSE for service {} environment {}", serviceId, environment);

        ServiceRegistration service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new NotFoundException("ServiceRegistration", serviceId));

        Map<String, Object> serviceBlock = buildServiceDockerComposeBlock(service, environment);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", AppConstants.DOCKER_COMPOSE_VERSION);

        Map<String, Object> servicesMap = new LinkedHashMap<>();
        servicesMap.put(service.getSlug(), serviceBlock);
        root.put("services", servicesMap);

        Map<String, Object> networkConfig = new LinkedHashMap<>();
        networkConfig.put("driver", "bridge");
        Map<String, Object> networksMap = new LinkedHashMap<>();
        networksMap.put(AppConstants.DEFAULT_DOCKER_NETWORK, networkConfig);
        root.put("networks", networksMap);

        String content = buildYamlContent(root);
        ConfigTemplate template = upsertTemplate(service, ConfigTemplateType.DOCKER_COMPOSE,
                environment, content, "registry-data");

        log.info("Generated DOCKER_COMPOSE for service {} — {} chars, version {}",
                service.getSlug(), content.length(), template.getVersion());

        return mapToResponse(template);
    }

    /**
     * Generates a Spring Boot {@code application.yml} fragment for a service.
     *
     * <p>Maps HTTP_API port to {@code server.port}, environment configs to their dotted key paths,
     * and upstream dependencies to service URL properties. The result is stored as a
     * {@link ConfigTemplate} with upsert semantics.</p>
     *
     * @param serviceId   the service ID
     * @param environment the target environment
     * @return the generated config template response
     * @throws NotFoundException if the service does not exist
     */
    @Transactional
    public ConfigTemplateResponse generateApplicationYml(UUID serviceId, String environment) {
        log.info("Generating APPLICATION_YML for service {} environment {}", serviceId, environment);

        ServiceRegistration service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new NotFoundException("ServiceRegistration", serviceId));

        Map<String, Object> root = new LinkedHashMap<>();

        // server.port from HTTP_API allocation
        Integer httpPort = resolveServicePort(serviceId, environment, PortType.HTTP_API);
        if (httpPort != null) {
            putNestedKey(root, "server.port", String.valueOf(httpPort));
        }

        // spring.application.name
        putNestedKey(root, "spring.application.name", service.getSlug());

        // Environment configs mapped to their dotted key paths
        List<EnvironmentConfig> configs = environmentConfigRepository
                .findByServiceIdAndEnvironment(serviceId, environment);
        for (EnvironmentConfig config : configs) {
            putNestedKey(root, config.getConfigKey(), config.getConfigValue());
        }

        // Upstream dependency service URLs
        List<ServiceDependency> upstreamDeps = dependencyRepository.findBySourceServiceId(serviceId);
        for (ServiceDependency dep : upstreamDeps) {
            ServiceRegistration target = dep.getTargetService();
            Integer targetPort = resolveServicePort(target.getId(), environment, PortType.HTTP_API);
            if (targetPort != null) {
                putNestedKey(root, "codeops.services." + target.getSlug() + ".url",
                        "http://localhost:" + targetPort);
            }
        }

        String content = buildYamlContent(root);
        ConfigTemplate template = upsertTemplate(service, ConfigTemplateType.APPLICATION_YML,
                environment, content, "registry-data");

        log.info("Generated APPLICATION_YML for service {} — {} chars, version {}",
                service.getSlug(), content.length(), template.getVersion());

        return mapToResponse(template);
    }

    /**
     * Generates a Claude Code context header — a structured text block for AI agent context.
     *
     * <p>Includes service identity, ports, upstream/downstream dependencies, API routes,
     * infrastructure resources, and environment config keys. The result is stored as a
     * {@link ConfigTemplate} with upsert semantics.</p>
     *
     * @param serviceId   the service ID
     * @param environment the target environment
     * @return the generated config template response
     * @throws NotFoundException if the service does not exist
     */
    @Transactional
    public ConfigTemplateResponse generateClaudeCodeHeader(UUID serviceId, String environment) {
        log.info("Generating CLAUDE_CODE_HEADER for service {} environment {}", serviceId, environment);

        ServiceRegistration service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new NotFoundException("ServiceRegistration", serviceId));

        List<PortAllocation> ports = portAllocationRepository
                .findByServiceIdAndEnvironment(serviceId, environment);
        List<ServiceDependency> upstream = dependencyRepository.findBySourceServiceId(serviceId);
        List<ServiceDependency> downstream = dependencyRepository.findByTargetServiceId(serviceId);
        List<ApiRouteRegistration> routes = routeRepository.findByServiceId(serviceId);
        List<InfraResource> infraResources = infraResourceRepository.findByServiceId(serviceId);
        List<EnvironmentConfig> configs = environmentConfigRepository
                .findByServiceIdAndEnvironment(serviceId, environment);

        StringBuilder sb = new StringBuilder();
        sb.append("# Service: ").append(service.getName())
                .append(" (").append(service.getSlug()).append(")\n");
        sb.append("# Type: ").append(service.getServiceType()).append("\n");
        sb.append("# Team: ").append(service.getTeamId()).append("\n");
        sb.append("# Repo: ").append(service.getRepoUrl() != null ? service.getRepoUrl() : "N/A")
                .append(" (branch: ").append(service.getDefaultBranch()).append(")\n");
        sb.append("# Tech Stack: ")
                .append(service.getTechStack() != null ? service.getTechStack() : "N/A").append("\n");
        sb.append("#\n");

        // Ports
        sb.append("# Ports (").append(environment).append("):\n");
        if (ports.isEmpty()) {
            sb.append("#   None\n");
        } else {
            for (PortAllocation port : ports) {
                sb.append("#   - ").append(port.getPortType()).append(": ")
                        .append(port.getPortNumber()).append("\n");
            }
        }
        sb.append("#\n");

        // Upstream dependencies
        sb.append("# Dependencies (upstream — I depend on):\n");
        if (upstream.isEmpty()) {
            sb.append("#   None\n");
        } else {
            for (ServiceDependency dep : upstream) {
                ServiceRegistration target = dep.getTargetService();
                Integer targetPort = resolveServicePort(target.getId(), environment, PortType.HTTP_API);
                sb.append("#   - ").append(target.getName())
                        .append(" (").append(target.getSlug()).append(")");
                if (targetPort != null) {
                    sb.append(" at http://localhost:").append(targetPort);
                }
                sb.append(" [").append(dep.getDependencyType()).append("]\n");
            }
        }
        sb.append("#\n");

        // Downstream dependencies
        sb.append("# Dependents (downstream — depend on me):\n");
        if (downstream.isEmpty()) {
            sb.append("#   None\n");
        } else {
            for (ServiceDependency dep : downstream) {
                ServiceRegistration source = dep.getSourceService();
                sb.append("#   - ").append(source.getName())
                        .append(" (").append(source.getSlug()).append(")")
                        .append(" [").append(dep.getDependencyType()).append("]\n");
            }
        }
        sb.append("#\n");

        // API Routes
        sb.append("# API Routes:\n");
        if (routes.isEmpty()) {
            sb.append("#   None\n");
        } else {
            for (ApiRouteRegistration route : routes) {
                sb.append("#   - ");
                if (route.getHttpMethods() != null) {
                    sb.append(route.getHttpMethods()).append(" ");
                }
                sb.append(route.getRoutePrefix());
                sb.append(" (env: ").append(route.getEnvironment()).append(")\n");
            }
        }
        sb.append("#\n");

        // Infrastructure
        sb.append("# Infrastructure:\n");
        if (infraResources.isEmpty()) {
            sb.append("#   None\n");
        } else {
            for (InfraResource ir : infraResources) {
                sb.append("#   - ").append(ir.getResourceType()).append(": ")
                        .append(ir.getResourceName())
                        .append(" (").append(ir.getEnvironment()).append(")");
                if (ir.getArnOrUrl() != null) {
                    sb.append(" ").append(ir.getArnOrUrl());
                }
                sb.append("\n");
            }
        }
        sb.append("#\n");

        // Environment Config Keys
        sb.append("# Environment Config Keys:\n");
        if (configs.isEmpty()) {
            sb.append("#   None\n");
        } else {
            for (EnvironmentConfig config : configs) {
                sb.append("#   - ").append(config.getConfigKey())
                        .append(" = ").append(config.getConfigValue())
                        .append(" [").append(config.getConfigSource()).append("]\n");
            }
        }

        String content = sb.toString();
        ConfigTemplate template = upsertTemplate(service, ConfigTemplateType.CLAUDE_CODE_HEADER,
                environment, content, "registry-data");

        log.info("Generated CLAUDE_CODE_HEADER for service {} — {} chars, version {}",
                service.getSlug(), content.length(), template.getVersion());

        return mapToResponse(template);
    }

    /**
     * Generates all three config types (Docker Compose, application.yml, Claude Code Header)
     * for a service in one call.
     *
     * <p>If any individual generation fails, it logs a warning and continues with the remaining
     * types. Returns all successfully generated templates.</p>
     *
     * @param serviceId   the service ID
     * @param environment the target environment
     * @return list of generated config template responses
     * @throws NotFoundException if the service does not exist
     */
    @Transactional
    public List<ConfigTemplateResponse> generateAllForService(UUID serviceId, String environment) {
        serviceRepository.findById(serviceId)
                .orElseThrow(() -> new NotFoundException("ServiceRegistration", serviceId));

        List<ConfigTemplateResponse> results = new ArrayList<>();

        try {
            results.add(generateDockerCompose(serviceId, environment));
        } catch (Exception e) {
            log.warn("Failed to generate DOCKER_COMPOSE for service {}: {}", serviceId, e.getMessage());
        }

        try {
            results.add(generateApplicationYml(serviceId, environment));
        } catch (Exception e) {
            log.warn("Failed to generate APPLICATION_YML for service {}: {}", serviceId, e.getMessage());
        }

        try {
            results.add(generateClaudeCodeHeader(serviceId, environment));
        } catch (Exception e) {
            log.warn("Failed to generate CLAUDE_CODE_HEADER for service {}: {}", serviceId, e.getMessage());
        }

        return results;
    }

    /**
     * Generates a complete {@code docker-compose.yml} for an entire solution (group of services).
     *
     * <p>Builds service blocks for each solution member, orders them using topological startup
     * order from the dependency graph, and includes shared network and volume blocks. The template
     * is stored against the first member service with {@code generatedFrom = "solution:{solutionId}"}.</p>
     *
     * @param solutionId  the solution ID
     * @param environment the target environment
     * @return the generated config template response
     * @throws NotFoundException   if the solution does not exist
     * @throws ValidationException if the solution has no members
     */
    @Transactional
    public ConfigTemplateResponse generateSolutionDockerCompose(UUID solutionId, String environment) {
        log.info("Generating solution DOCKER_COMPOSE for solution {} environment {}", solutionId, environment);

        Solution solution = solutionRepository.findById(solutionId)
                .orElseThrow(() -> new NotFoundException("Solution", solutionId));

        List<SolutionMember> members = solutionMemberRepository
                .findBySolutionIdOrderByDisplayOrderAsc(solutionId);

        if (members.isEmpty()) {
            throw new ValidationException("Solution '" + solution.getName() + "' has no members");
        }

        // Get startup order for dependency-aware ordering
        List<DependencyNodeResponse> startupOrder = dependencyGraphService
                .getStartupOrder(solution.getTeamId());
        List<UUID> orderedIds = startupOrder.stream()
                .map(DependencyNodeResponse::serviceId)
                .toList();

        // Map member service IDs for filtering
        Set<UUID> memberServiceIds = members.stream()
                .map(m -> m.getService().getId())
                .collect(Collectors.toSet());

        // Build service-by-ID lookup from members
        Map<UUID, ServiceRegistration> memberServiceMap = new LinkedHashMap<>();
        for (SolutionMember member : members) {
            memberServiceMap.put(member.getService().getId(), member.getService());
        }

        // Order member services by startup order, then append any not in startup order
        List<ServiceRegistration> orderedServices = new ArrayList<>();
        for (UUID id : orderedIds) {
            if (memberServiceIds.contains(id)) {
                orderedServices.add(memberServiceMap.get(id));
            }
        }
        for (SolutionMember member : members) {
            if (!orderedServices.contains(member.getService())) {
                orderedServices.add(member.getService());
            }
        }

        // Build service blocks
        Map<String, Object> services = new LinkedHashMap<>();
        Set<String> allVolumes = new LinkedHashSet<>();

        for (ServiceRegistration svc : orderedServices) {
            Map<String, Object> block = buildServiceDockerComposeBlock(svc, environment);
            services.put(svc.getSlug(), block);

            // Collect DOCKER_VOLUME resources for shared volumes block
            List<InfraResource> volumes = infraResourceRepository.findByServiceId(svc.getId()).stream()
                    .filter(r -> r.getResourceType() == InfraResourceType.DOCKER_VOLUME)
                    .filter(r -> r.getEnvironment().equals(environment))
                    .toList();
            for (InfraResource vol : volumes) {
                allVolumes.add(vol.getResourceName());
            }
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", AppConstants.DOCKER_COMPOSE_VERSION);
        root.put("services", services);

        // Networks block
        Map<String, Object> networkConfig = new LinkedHashMap<>();
        networkConfig.put("driver", "bridge");
        Map<String, Object> networksMap = new LinkedHashMap<>();
        networksMap.put(AppConstants.DEFAULT_DOCKER_NETWORK, networkConfig);
        root.put("networks", networksMap);

        // Volumes block
        if (!allVolumes.isEmpty()) {
            Map<String, Object> volumeBlock = new LinkedHashMap<>();
            for (String vol : allVolumes) {
                volumeBlock.put(vol, null);
            }
            root.put("volumes", volumeBlock);
        }

        String content = buildYamlContent(root);

        // Store against first member service
        ServiceRegistration firstService = orderedServices.get(0);
        ConfigTemplate template = upsertTemplate(firstService, ConfigTemplateType.DOCKER_COMPOSE,
                environment, content, "solution:" + solutionId);

        log.info("Generated solution DOCKER_COMPOSE for solution {} — {} services, {} chars, version {}",
                solution.getName(), orderedServices.size(), content.length(), template.getVersion());

        return mapToResponse(template);
    }

    /**
     * Retrieves a previously generated template without regenerating.
     *
     * @param serviceId   the service ID
     * @param type        the template type
     * @param environment the environment
     * @return the config template response
     * @throws NotFoundException if the template does not exist
     */
    public ConfigTemplateResponse getTemplate(UUID serviceId, ConfigTemplateType type, String environment) {
        ConfigTemplate template = configTemplateRepository
                .findByServiceIdAndTemplateTypeAndEnvironment(serviceId, type, environment)
                .orElseThrow(() -> new NotFoundException(
                        "ConfigTemplate for service " + serviceId + " type " + type + " env " + environment));

        return mapToResponse(template);
    }

    /**
     * Retrieves all templates for a service.
     *
     * @param serviceId the service ID
     * @return list of config template responses
     */
    public List<ConfigTemplateResponse> getTemplatesForService(UUID serviceId) {
        return configTemplateRepository.findByServiceId(serviceId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Deletes a config template.
     *
     * @param templateId the template ID
     * @throws NotFoundException if the template does not exist
     */
    @Transactional
    public void deleteTemplate(UUID templateId) {
        ConfigTemplate template = configTemplateRepository.findById(templateId)
                .orElseThrow(() -> new NotFoundException("ConfigTemplate", templateId));

        configTemplateRepository.delete(template);
        log.info("Config template deleted: {} ({}) for service {}",
                template.getTemplateType(), template.getEnvironment(),
                template.getService().getName());
    }

    // ──────────────────────────────────────────────
    // Package-private helper methods
    // ──────────────────────────────────────────────

    /**
     * Builds a docker-compose service block for a single service.
     *
     * <p>Includes image, container_name, ports, environment variables, depends_on,
     * networks, volumes (DOCKER_VOLUME infra resources), labels, and healthcheck.</p>
     *
     * @param service     the service registration
     * @param environment the target environment
     * @return map representing the docker-compose service block
     */
    Map<String, Object> buildServiceDockerComposeBlock(ServiceRegistration service, String environment) {
        Map<String, Object> block = new LinkedHashMap<>();

        block.put("image", service.getSlug() + ":latest");
        block.put("container_name", service.getSlug());

        // Ports
        List<PortAllocation> ports = portAllocationRepository
                .findByServiceIdAndEnvironment(service.getId(), environment);
        if (!ports.isEmpty()) {
            List<String> portMappings = ports.stream()
                    .map(p -> p.getPortNumber() + ":" + p.getPortNumber())
                    .toList();
            block.put("ports", portMappings);
        }

        // Environment variables
        List<EnvironmentConfig> configs = environmentConfigRepository
                .findByServiceIdAndEnvironment(service.getId(), environment);
        if (!configs.isEmpty()) {
            Map<String, String> envVars = new LinkedHashMap<>();
            for (EnvironmentConfig config : configs) {
                envVars.put(config.getConfigKey(), config.getConfigValue());
            }
            block.put("environment", envVars);
        }

        // Dependencies (upstream services this one depends on)
        List<ServiceDependency> deps = dependencyRepository.findBySourceServiceId(service.getId());
        if (!deps.isEmpty()) {
            List<String> dependsOn = deps.stream()
                    .map(d -> d.getTargetService().getSlug())
                    .toList();
            block.put("depends_on", dependsOn);
        }

        // Networks
        block.put("networks", List.of(AppConstants.DEFAULT_DOCKER_NETWORK));

        // Volumes from DOCKER_VOLUME infra resources
        List<InfraResource> infraResources = infraResourceRepository.findByServiceId(service.getId());
        List<InfraResource> volumes = infraResources.stream()
                .filter(r -> r.getResourceType() == InfraResourceType.DOCKER_VOLUME)
                .filter(r -> r.getEnvironment().equals(environment))
                .toList();
        if (!volumes.isEmpty()) {
            List<String> volumeMappings = volumes.stream()
                    .map(v -> v.getResourceName() + ":/data/" + v.getResourceName())
                    .toList();
            block.put("volumes", volumeMappings);
        }

        // Labels
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("com.codeops.service-id", service.getId().toString());
        labels.put("com.codeops.service-type", service.getServiceType().name());
        labels.put("com.codeops.team-id", service.getTeamId().toString());
        block.put("labels", labels);

        // Health check
        if (service.getHealthCheckUrl() != null && !service.getHealthCheckUrl().isEmpty()) {
            Map<String, Object> healthcheck = new LinkedHashMap<>();
            healthcheck.put("test", List.of("CMD", "curl", "-f", service.getHealthCheckUrl()));
            healthcheck.put("interval", service.getHealthCheckIntervalSeconds() + "s");
            healthcheck.put("timeout", AppConstants.HEALTH_CHECK_TIMEOUT_SECONDS + "s");
            healthcheck.put("retries", 3);
            block.put("healthcheck", healthcheck);
        }

        return block;
    }

    /**
     * Upserts a config template — finds existing by (service, type, env) and updates,
     * or creates new if not found. Increments version on update.
     *
     * @param service       the owning service
     * @param type          the template type
     * @param environment   the environment
     * @param content       the generated content
     * @param generatedFrom source description
     * @return the saved config template entity
     */
    ConfigTemplate upsertTemplate(ServiceRegistration service, ConfigTemplateType type,
                                  String environment, String content, String generatedFrom) {
        Optional<ConfigTemplate> existing = configTemplateRepository
                .findByServiceIdAndTemplateTypeAndEnvironment(service.getId(), type, environment);

        if (existing.isPresent()) {
            ConfigTemplate template = existing.get();
            template.setContentText(content);
            template.setVersion(template.getVersion() + 1);
            template.setIsAutoGenerated(true);
            template.setGeneratedFrom(generatedFrom);
            return configTemplateRepository.save(template);
        }

        ConfigTemplate template = ConfigTemplate.builder()
                .service(service)
                .templateType(type)
                .environment(environment)
                .contentText(content)
                .isAutoGenerated(true)
                .generatedFrom(generatedFrom)
                .version(1)
                .build();

        return configTemplateRepository.save(template);
    }

    /**
     * Converts a nested map structure to a YAML string using SnakeYAML.
     *
     * @param data the data to serialize
     * @return the YAML string
     */
    String buildYamlContent(Map<String, Object> data) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
        options.setIndent(2);
        options.setPrettyFlow(true);

        Yaml yaml = new Yaml(options);
        return yaml.dump(data);
    }

    /**
     * Resolves a specific port number for a service in an environment.
     *
     * @param serviceId   the service ID
     * @param environment the environment
     * @param type        the port type to find
     * @return the port number, or null if not allocated
     */
    Integer resolveServicePort(UUID serviceId, String environment, PortType type) {
        return portAllocationRepository.findByServiceIdAndEnvironment(serviceId, environment).stream()
                .filter(p -> p.getPortType() == type)
                .map(PortAllocation::getPortNumber)
                .findFirst()
                .orElse(null);
    }

    /**
     * Puts a value into a nested map structure using a dotted key path.
     *
     * <p>For example, {@code putNestedKey(map, "spring.datasource.url", "jdbc:...")} creates
     * the nested structure {@code {spring: {datasource: {url: "jdbc:..."}}}}.</p>
     *
     * @param root      the root map
     * @param dottedKey the dotted key path
     * @param value     the value to set
     */
    @SuppressWarnings("unchecked")
    void putNestedKey(Map<String, Object> root, String dottedKey, String value) {
        String[] parts = dottedKey.split("\\.");
        Map<String, Object> current = root;

        for (int i = 0; i < parts.length - 1; i++) {
            Object existing = current.get(parts[i]);
            if (existing instanceof Map) {
                current = (Map<String, Object>) existing;
            } else {
                Map<String, Object> nested = new LinkedHashMap<>();
                current.put(parts[i], nested);
                current = nested;
            }
        }

        current.put(parts[parts.length - 1], value);
    }

    // ──────────────────────────────────────────────
    // Mapping helper
    // ──────────────────────────────────────────────

    private ConfigTemplateResponse mapToResponse(ConfigTemplate entity) {
        ServiceRegistration svc = entity.getService();
        return new ConfigTemplateResponse(
                entity.getId(),
                svc.getId(),
                svc.getName(),
                entity.getTemplateType(),
                entity.getEnvironment(),
                entity.getContentText(),
                entity.getIsAutoGenerated(),
                entity.getGeneratedFrom(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
