package com.codeops.registry.service;

import com.codeops.registry.config.AppConstants;
import com.codeops.registry.dto.request.CloneServiceRequest;
import com.codeops.registry.dto.request.CreateServiceRequest;
import com.codeops.registry.dto.request.UpdateServiceRequest;
import com.codeops.registry.dto.response.*;
import com.codeops.registry.entity.*;
import com.codeops.registry.entity.enums.HealthStatus;
import com.codeops.registry.entity.enums.ServiceStatus;
import com.codeops.registry.entity.enums.ServiceType;
import com.codeops.registry.exception.NotFoundException;
import com.codeops.registry.exception.ValidationException;
import com.codeops.registry.repository.*;
import com.codeops.registry.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing service registrations — the core entity of the Control Plane.
 *
 * <p>Provides CRUD operations, slug generation, service cloning, paginated search,
 * the "service identity" assembly, and health checking. All mutations are transactional.</p>
 *
 * @see ServiceRegistration
 * @see PortAllocationService
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ServiceRegistryService {

    private final ServiceRegistrationRepository serviceRepository;
    private final PortAllocationRepository portAllocationRepository;
    private final ServiceDependencyRepository dependencyRepository;
    private final SolutionMemberRepository solutionMemberRepository;
    private final ApiRouteRegistrationRepository routeRepository;
    private final InfraResourceRepository infraRepository;
    private final EnvironmentConfigRepository envConfigRepository;
    private final PortAllocationService portAllocationService;
    private final RestTemplate restTemplate;

    /**
     * Registers a new service for a team.
     *
     * <p>Generates a slug from the name if none is provided. Ensures slug uniqueness within
     * the team. Optionally auto-allocates ports for the specified port types.</p>
     *
     * @param request       the creation request
     * @param currentUserId the user creating the service
     * @return the created service registration response
     * @throws ValidationException if the team has reached its service limit or the slug is invalid
     */
    @Transactional
    public ServiceRegistrationResponse createService(CreateServiceRequest request, UUID currentUserId) {
        long teamServiceCount = serviceRepository.countByTeamId(request.teamId());
        if (teamServiceCount >= AppConstants.MAX_SERVICES_PER_TEAM) {
            throw new ValidationException(
                    "Team has reached the maximum of " + AppConstants.MAX_SERVICES_PER_TEAM + " services");
        }

        String slug;
        if (request.slug() != null && !request.slug().isBlank()) {
            SlugUtils.validateSlug(request.slug());
            slug = request.slug();
        } else {
            slug = SlugUtils.generateSlug(request.name());
        }
        slug = SlugUtils.makeUnique(slug, s -> serviceRepository.existsByTeamIdAndSlug(request.teamId(), s));

        ServiceRegistration entity = ServiceRegistration.builder()
                .teamId(request.teamId())
                .name(request.name())
                .slug(slug)
                .serviceType(request.serviceType())
                .description(request.description())
                .repoUrl(request.repoUrl())
                .repoFullName(request.repoFullName())
                .defaultBranch(request.defaultBranch() != null ? request.defaultBranch() : "main")
                .techStack(request.techStack())
                .healthCheckUrl(request.healthCheckUrl())
                .healthCheckIntervalSeconds(request.healthCheckIntervalSeconds() != null
                        ? request.healthCheckIntervalSeconds()
                        : AppConstants.DEFAULT_HEALTH_CHECK_INTERVAL_SECONDS)
                .environmentsJson(request.environmentsJson())
                .metadataJson(request.metadataJson())
                .createdByUserId(currentUserId)
                .build();

        entity = serviceRepository.save(entity);

        if (request.autoAllocatePortTypes() != null && !request.autoAllocatePortTypes().isEmpty()) {
            String env = request.autoAllocateEnvironment() != null ? request.autoAllocateEnvironment() : "local";
            portAllocationService.autoAllocateAll(entity.getId(), env, request.autoAllocatePortTypes(), currentUserId);
        }

        log.info("Service registered: {} (slug: {}) for team {}", entity.getName(), entity.getSlug(), entity.getTeamId());
        return mapToResponse(entity, true);
    }

    /**
     * Retrieves a single service by ID with derived counts.
     *
     * @param serviceId the service ID
     * @return the service registration response with port, dependency, and solution counts
     * @throws NotFoundException if the service does not exist
     */
    public ServiceRegistrationResponse getService(UUID serviceId) {
        ServiceRegistration entity = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new NotFoundException("ServiceRegistration", serviceId));
        return mapToResponse(entity, true);
    }

    /**
     * Retrieves a service by team ID and slug.
     *
     * @param teamId the team ID
     * @param slug   the service slug
     * @return the service registration response
     * @throws NotFoundException if no service exists with the given slug in the team
     */
    public ServiceRegistrationResponse getServiceBySlug(UUID teamId, String slug) {
        ServiceRegistration entity = serviceRepository.findByTeamIdAndSlug(teamId, slug)
                .orElseThrow(() -> new NotFoundException("ServiceRegistration", "slug", slug));
        return mapToResponse(entity, true);
    }

    /**
     * Lists services for a team with optional filtering by status, type, and name search.
     *
     * <p>Derived counts (portCount, dependencyCount, solutionCount) are set to 0 on list
     * views to avoid N+1 queries. Use {@link #getService(UUID)} for detail views with counts.</p>
     *
     * @param teamId   the team ID
     * @param status   optional status filter
     * @param type     optional service type filter
     * @param search   optional name search (case-insensitive contains)
     * @param pageable pagination parameters
     * @return a paged response of service registrations
     */
    public PageResponse<ServiceRegistrationResponse> getServicesForTeam(UUID teamId, ServiceStatus status,
                                                                         ServiceType type, String search,
                                                                         Pageable pageable) {
        Page<ServiceRegistration> page;

        if (search != null && !search.isBlank()) {
            page = serviceRepository.findByTeamIdAndNameContainingIgnoreCase(teamId, search, pageable);
        } else if (status != null && type != null) {
            page = serviceRepository.findByTeamIdAndStatusAndServiceType(teamId, status, type, pageable);
        } else if (status != null) {
            page = serviceRepository.findByTeamIdAndStatus(teamId, status, pageable);
        } else if (type != null) {
            page = serviceRepository.findByTeamIdAndServiceType(teamId, type, pageable);
        } else {
            page = serviceRepository.findByTeamId(teamId, pageable);
        }

        // List views use 0 for derived counts to avoid N+1 queries
        Page<ServiceRegistrationResponse> mapped = page.map(e -> mapToResponse(e, false));
        return PageResponse.from(mapped);
    }

    /**
     * Partially updates a service registration with non-null fields from the request.
     *
     * @param serviceId the service ID
     * @param request   the update request with optional fields
     * @return the updated service registration response
     * @throws NotFoundException if the service does not exist
     */
    @Transactional
    public ServiceRegistrationResponse updateService(UUID serviceId, UpdateServiceRequest request) {
        ServiceRegistration entity = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new NotFoundException("ServiceRegistration", serviceId));

        if (request.name() != null) entity.setName(request.name());
        if (request.description() != null) entity.setDescription(request.description());
        if (request.repoUrl() != null) entity.setRepoUrl(request.repoUrl());
        if (request.repoFullName() != null) entity.setRepoFullName(request.repoFullName());
        if (request.defaultBranch() != null) entity.setDefaultBranch(request.defaultBranch());
        if (request.techStack() != null) entity.setTechStack(request.techStack());
        if (request.healthCheckUrl() != null) entity.setHealthCheckUrl(request.healthCheckUrl());
        if (request.healthCheckIntervalSeconds() != null) entity.setHealthCheckIntervalSeconds(request.healthCheckIntervalSeconds());
        if (request.environmentsJson() != null) entity.setEnvironmentsJson(request.environmentsJson());
        if (request.metadataJson() != null) entity.setMetadataJson(request.metadataJson());

        entity = serviceRepository.save(entity);
        return mapToResponse(entity, true);
    }

    /**
     * Updates a service's lifecycle status.
     *
     * @param serviceId the service ID
     * @param status    the new status
     * @return the updated service registration response
     * @throws NotFoundException if the service does not exist
     */
    @Transactional
    public ServiceRegistrationResponse updateServiceStatus(UUID serviceId, ServiceStatus status) {
        ServiceRegistration entity = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new NotFoundException("ServiceRegistration", serviceId));

        entity.setStatus(status);
        entity = serviceRepository.save(entity);
        log.info("Service {} status changed to {}", entity.getName(), status);
        return mapToResponse(entity, true);
    }

    /**
     * Deletes a service registration after checking for active memberships and dependents.
     *
     * @param serviceId the service ID
     * @throws NotFoundException   if the service does not exist
     * @throws ValidationException if the service belongs to solutions or has required dependents
     */
    @Transactional
    public void deleteService(UUID serviceId) {
        ServiceRegistration entity = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new NotFoundException("ServiceRegistration", serviceId));

        List<SolutionMember> memberships = solutionMemberRepository.findByServiceId(serviceId);
        if (!memberships.isEmpty()) {
            List<String> solutionNames = memberships.stream()
                    .map(sm -> sm.getSolution().getName())
                    .toList();
            throw new ValidationException(
                    "Cannot delete service that belongs to solutions: " + solutionNames
                            + ". Remove from solutions first.");
        }

        List<ServiceDependency> dependents = dependencyRepository.findByTargetServiceId(serviceId);
        List<ServiceDependency> requiredDependents = dependents.stream()
                .filter(ServiceDependency::getIsRequired)
                .toList();
        if (!requiredDependents.isEmpty()) {
            List<String> dependentNames = requiredDependents.stream()
                    .map(sd -> sd.getSourceService().getName())
                    .toList();
            throw new ValidationException(
                    "Cannot delete service with active dependents: " + dependentNames
                            + ". Remove dependencies first.");
        }

        String name = entity.getName();
        UUID id = entity.getId();
        serviceRepository.delete(entity);
        log.info("Service deleted: {} (id: {})", name, id);
    }

    /**
     * Clones a service under a new name, copying all fields except identity and health state.
     *
     * <p>Port allocations from the original are auto-allocated anew (next available ports,
     * not the same port numbers).</p>
     *
     * @param serviceId     the original service ID
     * @param request       the clone request with new name and optional slug
     * @param currentUserId the user performing the clone
     * @return the cloned service registration response
     * @throws NotFoundException if the original service does not exist
     */
    @Transactional
    public ServiceRegistrationResponse cloneService(UUID serviceId, CloneServiceRequest request, UUID currentUserId) {
        ServiceRegistration original = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new NotFoundException("ServiceRegistration", serviceId));

        String slug;
        if (request.newSlug() != null && !request.newSlug().isBlank()) {
            SlugUtils.validateSlug(request.newSlug());
            slug = request.newSlug();
        } else {
            slug = SlugUtils.generateSlug(request.newName());
        }
        slug = SlugUtils.makeUnique(slug, s -> serviceRepository.existsByTeamIdAndSlug(original.getTeamId(), s));

        ServiceRegistration clone = ServiceRegistration.builder()
                .teamId(original.getTeamId())
                .name(request.newName())
                .slug(slug)
                .serviceType(original.getServiceType())
                .description(original.getDescription())
                .repoUrl(original.getRepoUrl())
                .repoFullName(original.getRepoFullName())
                .defaultBranch(original.getDefaultBranch())
                .techStack(original.getTechStack())
                .status(original.getStatus())
                .healthCheckUrl(original.getHealthCheckUrl())
                .healthCheckIntervalSeconds(original.getHealthCheckIntervalSeconds())
                .environmentsJson(original.getEnvironmentsJson())
                .metadataJson(original.getMetadataJson())
                .createdByUserId(currentUserId)
                .build();

        clone = serviceRepository.save(clone);

        List<PortAllocation> originalPorts = portAllocationRepository.findByServiceId(serviceId);
        for (PortAllocation pa : originalPorts) {
            portAllocationService.autoAllocate(clone.getId(), pa.getEnvironment(), pa.getPortType(), currentUserId);
        }

        log.info("Service cloned: {} → {} for team {}", original.getName(), clone.getName(), clone.getTeamId());
        return mapToResponse(clone, true);
    }

    /**
     * Assembles the complete service identity in one call.
     *
     * <p>Loads ports, upstream/downstream dependencies, routes, infra resources, and
     * environment configs. When environment is specified, filters ports and configs
     * to that environment only.</p>
     *
     * @param serviceId   the service ID
     * @param environment optional environment filter (null loads all)
     * @return the complete service identity response
     * @throws NotFoundException if the service does not exist
     */
    public ServiceIdentityResponse getServiceIdentity(UUID serviceId, String environment) {
        ServiceRegistration entity = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new NotFoundException("ServiceRegistration", serviceId));

        List<PortAllocation> ports = (environment != null)
                ? portAllocationRepository.findByServiceIdAndEnvironment(serviceId, environment)
                : portAllocationRepository.findByServiceId(serviceId);

        List<ServiceDependency> upstream = dependencyRepository.findBySourceServiceId(serviceId);
        List<ServiceDependency> downstream = dependencyRepository.findByTargetServiceId(serviceId);
        List<ApiRouteRegistration> routes = routeRepository.findByServiceId(serviceId);
        List<InfraResource> infra = infraRepository.findByServiceId(serviceId);

        List<EnvironmentConfig> configs = (environment != null)
                ? envConfigRepository.findByServiceIdAndEnvironment(serviceId, environment)
                : envConfigRepository.findByServiceId(serviceId);

        return new ServiceIdentityResponse(
                mapToResponse(entity, true),
                ports.stream().map(portAllocationService::mapToResponse).toList(),
                upstream.stream().map(this::mapDependencyToResponse).toList(),
                downstream.stream().map(this::mapDependencyToResponse).toList(),
                routes.stream().map(this::mapRouteToResponse).toList(),
                infra.stream().map(this::mapInfraToResponse).toList(),
                configs.stream().map(this::mapConfigToResponse).toList());
    }

    /**
     * Checks the health of a single service by calling its health check URL.
     *
     * @param serviceId the service ID
     * @return the health check response
     * @throws NotFoundException if the service does not exist
     */
    @Transactional
    public ServiceHealthResponse checkHealth(UUID serviceId) {
        ServiceRegistration entity = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new NotFoundException("ServiceRegistration", serviceId));

        if (entity.getHealthCheckUrl() == null || entity.getHealthCheckUrl().isBlank()) {
            return new ServiceHealthResponse(
                    entity.getId(), entity.getName(), entity.getSlug(),
                    HealthStatus.UNKNOWN, Instant.now(), null, null,
                    "No health check URL configured");
        }

        long startTime = System.currentTimeMillis();
        HealthStatus status;
        String errorMessage = null;

        try {
            var response = restTemplate.getForEntity(entity.getHealthCheckUrl(), String.class);
            int responseTimeMs = (int) (System.currentTimeMillis() - startTime);

            if (response.getStatusCode().is2xxSuccessful()) {
                status = HealthStatus.UP;
            } else {
                status = HealthStatus.DEGRADED;
                errorMessage = "HTTP " + response.getStatusCode().value();
            }

            entity.setLastHealthStatus(status);
            entity.setLastHealthCheckAt(Instant.now());
            serviceRepository.save(entity);

            return new ServiceHealthResponse(
                    entity.getId(), entity.getName(), entity.getSlug(),
                    status, entity.getLastHealthCheckAt(), entity.getHealthCheckUrl(),
                    responseTimeMs, errorMessage);

        } catch (ResourceAccessException e) {
            int responseTimeMs = (int) (System.currentTimeMillis() - startTime);
            status = HealthStatus.DOWN;
            errorMessage = e.getMessage();

            entity.setLastHealthStatus(status);
            entity.setLastHealthCheckAt(Instant.now());
            serviceRepository.save(entity);

            return new ServiceHealthResponse(
                    entity.getId(), entity.getName(), entity.getSlug(),
                    status, entity.getLastHealthCheckAt(), entity.getHealthCheckUrl(),
                    responseTimeMs, errorMessage);

        } catch (Exception e) {
            int responseTimeMs = (int) (System.currentTimeMillis() - startTime);
            status = HealthStatus.DOWN;
            errorMessage = e.getMessage();

            entity.setLastHealthStatus(status);
            entity.setLastHealthCheckAt(Instant.now());
            serviceRepository.save(entity);

            return new ServiceHealthResponse(
                    entity.getId(), entity.getName(), entity.getSlug(),
                    status, entity.getLastHealthCheckAt(), entity.getHealthCheckUrl(),
                    responseTimeMs, errorMessage);
        }
    }

    /**
     * Checks health of all active services for a team in parallel.
     *
     * @param teamId the team ID
     * @return list of health check responses for all active services
     */
    public List<ServiceHealthResponse> checkAllHealth(UUID teamId) {
        List<ServiceRegistration> services = serviceRepository
                .findByTeamIdAndStatus(teamId, ServiceStatus.ACTIVE);

        List<CompletableFuture<ServiceHealthResponse>> futures = services.stream()
                .map(svc -> CompletableFuture.supplyAsync(() -> checkHealth(svc.getId())))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    // ──────────────────────────────────────────────
    // Mapping helpers
    // ──────────────────────────────────────────────

    /**
     * Maps a ServiceRegistration entity to a response DTO.
     *
     * @param entity         the entity
     * @param computeCounts  if true, compute portCount/dependencyCount/solutionCount via queries;
     *                       if false, set to 0 (for list views to avoid N+1)
     */
    private ServiceRegistrationResponse mapToResponse(ServiceRegistration entity, boolean computeCounts) {
        int portCount = 0;
        int dependencyCount = 0;
        int solutionCount = 0;

        if (computeCounts) {
            portCount = (int) portAllocationRepository.countByServiceId(entity.getId());
            dependencyCount = (int) dependencyRepository.countBySourceServiceId(entity.getId());
            solutionCount = (int) solutionMemberRepository.countByServiceId(entity.getId());
        }

        return new ServiceRegistrationResponse(
                entity.getId(),
                entity.getTeamId(),
                entity.getName(),
                entity.getSlug(),
                entity.getServiceType(),
                entity.getDescription(),
                entity.getRepoUrl(),
                entity.getRepoFullName(),
                entity.getDefaultBranch(),
                entity.getTechStack(),
                entity.getStatus(),
                entity.getHealthCheckUrl(),
                entity.getHealthCheckIntervalSeconds(),
                entity.getLastHealthStatus(),
                entity.getLastHealthCheckAt(),
                entity.getEnvironmentsJson(),
                entity.getMetadataJson(),
                entity.getCreatedByUserId(),
                portCount,
                dependencyCount,
                solutionCount,
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private ServiceDependencyResponse mapDependencyToResponse(ServiceDependency dep) {
        return new ServiceDependencyResponse(
                dep.getId(),
                dep.getSourceService().getId(),
                dep.getSourceService().getName(),
                dep.getSourceService().getSlug(),
                dep.getTargetService().getId(),
                dep.getTargetService().getName(),
                dep.getTargetService().getSlug(),
                dep.getDependencyType(),
                dep.getDescription(),
                dep.getIsRequired(),
                dep.getTargetEndpoint(),
                dep.getCreatedAt());
    }

    private ApiRouteResponse mapRouteToResponse(ApiRouteRegistration route) {
        return new ApiRouteResponse(
                route.getId(),
                route.getService().getId(),
                route.getService().getName(),
                route.getService().getSlug(),
                route.getGatewayService() != null ? route.getGatewayService().getId() : null,
                route.getGatewayService() != null ? route.getGatewayService().getName() : null,
                route.getRoutePrefix(),
                route.getHttpMethods(),
                route.getEnvironment(),
                route.getDescription(),
                route.getCreatedAt());
    }

    private InfraResourceResponse mapInfraToResponse(InfraResource ir) {
        return new InfraResourceResponse(
                ir.getId(),
                ir.getTeamId(),
                ir.getService() != null ? ir.getService().getId() : null,
                ir.getService() != null ? ir.getService().getName() : null,
                ir.getService() != null ? ir.getService().getSlug() : null,
                ir.getResourceType(),
                ir.getResourceName(),
                ir.getEnvironment(),
                ir.getRegion(),
                ir.getArnOrUrl(),
                ir.getMetadataJson(),
                ir.getDescription(),
                ir.getCreatedByUserId(),
                ir.getCreatedAt(),
                ir.getUpdatedAt());
    }

    private EnvironmentConfigResponse mapConfigToResponse(EnvironmentConfig ec) {
        return new EnvironmentConfigResponse(
                ec.getId(),
                ec.getService().getId(),
                ec.getEnvironment(),
                ec.getConfigKey(),
                ec.getConfigValue(),
                ec.getConfigSource(),
                ec.getDescription(),
                ec.getCreatedAt(),
                ec.getUpdatedAt());
    }
}
