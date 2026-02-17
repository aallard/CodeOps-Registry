package com.codeops.registry.service;

import com.codeops.registry.dto.request.CreateInfraResourceRequest;
import com.codeops.registry.dto.request.UpdateInfraResourceRequest;
import com.codeops.registry.dto.response.InfraResourceResponse;
import com.codeops.registry.dto.response.PageResponse;
import com.codeops.registry.entity.InfraResource;
import com.codeops.registry.entity.ServiceRegistration;
import com.codeops.registry.entity.enums.InfraResourceType;
import com.codeops.registry.exception.NotFoundException;
import com.codeops.registry.exception.ValidationException;
import com.codeops.registry.repository.InfraResourceRepository;
import com.codeops.registry.repository.ServiceRegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing infrastructure resource registrations.
 *
 * <p>Tracks cloud and infrastructure resources (S3 buckets, SQS queues, RDS instances,
 * Docker networks, etc.) with ownership tracking per service. Key features include
 * duplicate detection, orphan finding (resources with no owning service), and
 * resource reassignment between services.</p>
 *
 * @see InfraResource
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class InfraResourceService {

    private final InfraResourceRepository infraRepository;
    private final ServiceRegistrationRepository serviceRepository;

    /**
     * Registers an infrastructure resource.
     *
     * <p>Optionally links to an owning service (must belong to the same team).
     * Checks for duplicate resources by team, type, name, and environment.</p>
     *
     * @param request       the creation request
     * @param currentUserId the user creating the resource
     * @return the created resource response
     * @throws NotFoundException   if the specified service does not exist
     * @throws ValidationException if the service belongs to a different team or a duplicate exists
     */
    @Transactional
    public InfraResourceResponse createResource(CreateInfraResourceRequest request, UUID currentUserId) {
        ServiceRegistration service = null;
        if (request.serviceId() != null) {
            service = serviceRepository.findById(request.serviceId())
                    .orElseThrow(() -> new NotFoundException("ServiceRegistration", request.serviceId()));

            if (!service.getTeamId().equals(request.teamId())) {
                throw new ValidationException(
                        "Service " + service.getName() + " does not belong to the specified team");
            }
        }

        infraRepository.findByTeamIdAndResourceTypeAndResourceNameAndEnvironment(
                request.teamId(), request.resourceType(), request.resourceName(), request.environment())
                .ifPresent(existing -> {
                    throw new ValidationException(
                            "Infrastructure resource '" + request.resourceName() + "' of type "
                                    + request.resourceType() + " already exists in environment '"
                                    + request.environment() + "'");
                });

        InfraResource entity = InfraResource.builder()
                .teamId(request.teamId())
                .service(service)
                .resourceType(request.resourceType())
                .resourceName(request.resourceName())
                .environment(request.environment())
                .region(request.region())
                .arnOrUrl(request.arnOrUrl())
                .metadataJson(request.metadataJson())
                .description(request.description())
                .createdByUserId(currentUserId)
                .build();

        entity = infraRepository.save(entity);
        log.info("Infra resource registered: {} ({}) in {} — owner: {}",
                entity.getResourceName(), entity.getResourceType(), entity.getEnvironment(),
                service != null ? service.getName() : "shared");

        return mapToResponse(entity);
    }

    /**
     * Partially updates an infrastructure resource with non-null fields from the request.
     *
     * <p>If {@code request.serviceId()} is non-null, validates the new owner service exists.</p>
     *
     * @param resourceId the resource ID
     * @param request    the update request with optional fields
     * @return the updated resource response
     * @throws NotFoundException if the resource or the new service does not exist
     */
    @Transactional
    public InfraResourceResponse updateResource(UUID resourceId, UpdateInfraResourceRequest request) {
        InfraResource entity = infraRepository.findById(resourceId)
                .orElseThrow(() -> new NotFoundException("InfraResource", resourceId));

        if (request.serviceId() != null) {
            ServiceRegistration newService = serviceRepository.findById(request.serviceId())
                    .orElseThrow(() -> new NotFoundException("ServiceRegistration", request.serviceId()));
            entity.setService(newService);
        }

        if (request.resourceName() != null) entity.setResourceName(request.resourceName());
        if (request.region() != null) entity.setRegion(request.region());
        if (request.arnOrUrl() != null) entity.setArnOrUrl(request.arnOrUrl());
        if (request.metadataJson() != null) entity.setMetadataJson(request.metadataJson());
        if (request.description() != null) entity.setDescription(request.description());

        entity = infraRepository.save(entity);
        return mapToResponse(entity);
    }

    /**
     * Deletes an infrastructure resource.
     *
     * @param resourceId the resource ID
     * @throws NotFoundException if the resource does not exist
     */
    @Transactional
    public void deleteResource(UUID resourceId) {
        InfraResource entity = infraRepository.findById(resourceId)
                .orElseThrow(() -> new NotFoundException("InfraResource", resourceId));

        infraRepository.delete(entity);
        log.info("Infra resource deleted: {} ({})",
                entity.getResourceName(), entity.getResourceType());
    }

    /**
     * Lists infrastructure resources for a team with optional type and environment filters.
     *
     * @param teamId      the team ID
     * @param type        optional resource type filter
     * @param environment optional environment filter
     * @param pageable    pagination parameters
     * @return a paged response of infrastructure resources
     */
    public PageResponse<InfraResourceResponse> getResourcesForTeam(UUID teamId, InfraResourceType type,
                                                                     String environment, Pageable pageable) {
        Page<InfraResource> page;

        if (type != null && environment != null) {
            page = infraRepository.findByTeamIdAndResourceTypeAndEnvironment(teamId, type, environment, pageable);
        } else if (type != null) {
            page = infraRepository.findByTeamIdAndResourceType(teamId, type, pageable);
        } else if (environment != null) {
            page = infraRepository.findByTeamIdAndEnvironment(teamId, environment, pageable);
        } else {
            page = infraRepository.findByTeamId(teamId, pageable);
        }

        Page<InfraResourceResponse> mapped = page.map(this::mapToResponse);
        return PageResponse.from(mapped);
    }

    /**
     * Lists all infrastructure resources owned by a specific service.
     *
     * @param serviceId the service ID
     * @return list of resource responses
     */
    public List<InfraResourceResponse> getResourcesForService(UUID serviceId) {
        return infraRepository.findByServiceId(serviceId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Finds orphaned resources — resources with no owning service.
     *
     * <p>Common after service deletion or infrastructure drift.</p>
     *
     * @param teamId the team ID
     * @return list of orphaned resource responses
     */
    public List<InfraResourceResponse> findOrphanedResources(UUID teamId) {
        return infraRepository.findOrphansByTeamId(teamId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    /**
     * Reassigns an infrastructure resource to a different service.
     *
     * @param resourceId   the resource ID
     * @param newServiceId the new owning service ID
     * @return the updated resource response
     * @throws NotFoundException   if the resource or service does not exist
     * @throws ValidationException if the new service belongs to a different team
     */
    @Transactional
    public InfraResourceResponse reassignResource(UUID resourceId, UUID newServiceId) {
        InfraResource entity = infraRepository.findById(resourceId)
                .orElseThrow(() -> new NotFoundException("InfraResource", resourceId));

        ServiceRegistration newService = serviceRepository.findById(newServiceId)
                .orElseThrow(() -> new NotFoundException("ServiceRegistration", newServiceId));

        if (!newService.getTeamId().equals(entity.getTeamId())) {
            throw new ValidationException(
                    "Cannot reassign resource to a service in a different team");
        }

        entity.setService(newService);
        entity = infraRepository.save(entity);
        log.info("Infra resource {} reassigned to service {}", entity.getResourceName(), newService.getName());

        return mapToResponse(entity);
    }

    /**
     * Removes service ownership from a resource, making it orphaned/shared.
     *
     * @param resourceId the resource ID
     * @return the updated resource response
     * @throws NotFoundException if the resource does not exist
     */
    @Transactional
    public InfraResourceResponse orphanResource(UUID resourceId) {
        InfraResource entity = infraRepository.findById(resourceId)
                .orElseThrow(() -> new NotFoundException("InfraResource", resourceId));

        entity.setService(null);
        entity = infraRepository.save(entity);
        log.info("Infra resource {} orphaned (removed service ownership)", entity.getResourceName());

        return mapToResponse(entity);
    }

    // ──────────────────────────────────────────────
    // Mapping helper
    // ──────────────────────────────────────────────

    private InfraResourceResponse mapToResponse(InfraResource entity) {
        ServiceRegistration svc = entity.getService();
        return new InfraResourceResponse(
                entity.getId(),
                entity.getTeamId(),
                svc != null ? svc.getId() : null,
                svc != null ? svc.getName() : null,
                svc != null ? svc.getSlug() : null,
                entity.getResourceType(),
                entity.getResourceName(),
                entity.getEnvironment(),
                entity.getRegion(),
                entity.getArnOrUrl(),
                entity.getMetadataJson(),
                entity.getDescription(),
                entity.getCreatedByUserId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
