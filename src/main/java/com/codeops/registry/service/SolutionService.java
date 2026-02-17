package com.codeops.registry.service;

import com.codeops.registry.config.AppConstants;
import com.codeops.registry.dto.request.AddSolutionMemberRequest;
import com.codeops.registry.dto.request.CreateSolutionRequest;
import com.codeops.registry.dto.request.UpdateSolutionMemberRequest;
import com.codeops.registry.dto.request.UpdateSolutionRequest;
import com.codeops.registry.dto.response.*;
import com.codeops.registry.entity.ServiceRegistration;
import com.codeops.registry.entity.Solution;
import com.codeops.registry.entity.SolutionMember;
import com.codeops.registry.entity.enums.HealthStatus;
import com.codeops.registry.entity.enums.SolutionCategory;
import com.codeops.registry.entity.enums.SolutionStatus;
import com.codeops.registry.exception.NotFoundException;
import com.codeops.registry.exception.ValidationException;
import com.codeops.registry.repository.ServiceRegistrationRepository;
import com.codeops.registry.repository.SolutionMemberRepository;
import com.codeops.registry.repository.SolutionRepository;
import com.codeops.registry.util.SlugUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing solutions — logical groupings of services into products or platforms.
 *
 * <p>Provides CRUD operations, member management (add, update, remove, reorder),
 * aggregated health checks, and paginated search with filtering. All mutations
 * are transactional.</p>
 *
 * @see Solution
 * @see SolutionMember
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SolutionService {

    private final SolutionRepository solutionRepository;
    private final SolutionMemberRepository memberRepository;
    private final ServiceRegistrationRepository serviceRepository;

    /**
     * Creates a new solution for a team.
     *
     * <p>Generates a slug from the name if none is provided. Ensures slug uniqueness
     * within the team. Enforces the per-team solution limit.</p>
     *
     * @param request       the creation request
     * @param currentUserId the user creating the solution
     * @return the created solution response
     * @throws ValidationException if the team has reached its solution limit or the slug is invalid
     */
    @Transactional
    public SolutionResponse createSolution(CreateSolutionRequest request, UUID currentUserId) {
        long teamSolutionCount = solutionRepository.countByTeamId(request.teamId());
        if (teamSolutionCount >= AppConstants.MAX_SOLUTIONS_PER_TEAM) {
            throw new ValidationException(
                    "Team has reached the maximum of " + AppConstants.MAX_SOLUTIONS_PER_TEAM + " solutions");
        }

        String slug;
        if (request.slug() != null && !request.slug().isBlank()) {
            SlugUtils.validateSlug(request.slug());
            slug = request.slug();
        } else {
            slug = SlugUtils.generateSlug(request.name());
        }
        slug = SlugUtils.makeUnique(slug, s -> solutionRepository.existsByTeamIdAndSlug(request.teamId(), s));

        Solution entity = Solution.builder()
                .teamId(request.teamId())
                .name(request.name())
                .slug(slug)
                .description(request.description())
                .category(request.category())
                .iconName(request.iconName())
                .colorHex(request.colorHex())
                .ownerUserId(request.ownerUserId())
                .repositoryUrl(request.repositoryUrl())
                .documentationUrl(request.documentationUrl())
                .metadataJson(request.metadataJson())
                .createdByUserId(currentUserId)
                .build();

        entity = solutionRepository.save(entity);

        log.info("Solution created: {} (slug: {}) for team {}", entity.getName(), entity.getSlug(), entity.getTeamId());
        return mapToResponse(entity, true);
    }

    /**
     * Retrieves a single solution by ID with computed member count.
     *
     * @param solutionId the solution ID
     * @return the solution response with member count
     * @throws NotFoundException if the solution does not exist
     */
    public SolutionResponse getSolution(UUID solutionId) {
        Solution entity = solutionRepository.findById(solutionId)
                .orElseThrow(() -> new NotFoundException("Solution", solutionId));
        return mapToResponse(entity, true);
    }

    /**
     * Retrieves a solution by team ID and slug.
     *
     * @param teamId the team ID
     * @param slug   the solution slug
     * @return the solution response
     * @throws NotFoundException if no solution exists with the given slug in the team
     */
    public SolutionResponse getSolutionBySlug(UUID teamId, String slug) {
        Solution entity = solutionRepository.findByTeamIdAndSlug(teamId, slug)
                .orElseThrow(() -> new NotFoundException("Solution", "slug", slug));
        return mapToResponse(entity, true);
    }

    /**
     * Retrieves full solution detail including member list.
     *
     * @param solutionId the solution ID
     * @return the solution detail response with all members
     * @throws NotFoundException if the solution does not exist
     */
    public SolutionDetailResponse getSolutionDetail(UUID solutionId) {
        Solution entity = solutionRepository.findById(solutionId)
                .orElseThrow(() -> new NotFoundException("Solution", solutionId));

        List<SolutionMember> members = memberRepository.findBySolutionIdOrderByDisplayOrderAsc(solutionId);
        List<SolutionMemberResponse> memberResponses = members.stream()
                .map(this::mapMemberToResponse)
                .toList();

        return new SolutionDetailResponse(
                entity.getId(),
                entity.getTeamId(),
                entity.getName(),
                entity.getSlug(),
                entity.getDescription(),
                entity.getCategory(),
                entity.getStatus(),
                entity.getIconName(),
                entity.getColorHex(),
                entity.getOwnerUserId(),
                entity.getRepositoryUrl(),
                entity.getDocumentationUrl(),
                entity.getMetadataJson(),
                entity.getCreatedByUserId(),
                memberResponses,
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    /**
     * Lists solutions for a team with optional filtering by status and category.
     *
     * <p>Member count is set to 0 on list views to avoid N+1 queries.
     * Use {@link #getSolution(UUID)} for detail views with counts.</p>
     *
     * @param teamId   the team ID
     * @param status   optional status filter
     * @param category optional category filter
     * @param pageable pagination parameters
     * @return a paged response of solutions
     */
    public PageResponse<SolutionResponse> getSolutionsForTeam(UUID teamId, SolutionStatus status,
                                                                SolutionCategory category, Pageable pageable) {
        Page<Solution> page;

        if (status != null) {
            page = solutionRepository.findByTeamIdAndStatus(teamId, status, pageable);
        } else if (category != null) {
            page = solutionRepository.findByTeamIdAndCategory(teamId, category, pageable);
        } else {
            page = solutionRepository.findByTeamId(teamId, pageable);
        }

        Page<SolutionResponse> mapped = page.map(e -> mapToResponse(e, false));
        return PageResponse.from(mapped);
    }

    /**
     * Partially updates a solution with non-null fields from the request.
     *
     * @param solutionId the solution ID
     * @param request    the update request with optional fields
     * @return the updated solution response
     * @throws NotFoundException if the solution does not exist
     */
    @Transactional
    public SolutionResponse updateSolution(UUID solutionId, UpdateSolutionRequest request) {
        Solution entity = solutionRepository.findById(solutionId)
                .orElseThrow(() -> new NotFoundException("Solution", solutionId));

        if (request.name() != null) entity.setName(request.name());
        if (request.description() != null) entity.setDescription(request.description());
        if (request.category() != null) entity.setCategory(request.category());
        if (request.status() != null) entity.setStatus(request.status());
        if (request.iconName() != null) entity.setIconName(request.iconName());
        if (request.colorHex() != null) entity.setColorHex(request.colorHex());
        if (request.ownerUserId() != null) entity.setOwnerUserId(request.ownerUserId());
        if (request.repositoryUrl() != null) entity.setRepositoryUrl(request.repositoryUrl());
        if (request.documentationUrl() != null) entity.setDocumentationUrl(request.documentationUrl());
        if (request.metadataJson() != null) entity.setMetadataJson(request.metadataJson());

        entity = solutionRepository.save(entity);
        return mapToResponse(entity, true);
    }

    /**
     * Deletes a solution.
     *
     * <p>Solution members are cascade-deleted via {@code orphanRemoval = true}.</p>
     *
     * @param solutionId the solution ID
     * @throws NotFoundException if the solution does not exist
     */
    @Transactional
    public void deleteSolution(UUID solutionId) {
        Solution entity = solutionRepository.findById(solutionId)
                .orElseThrow(() -> new NotFoundException("Solution", solutionId));

        String name = entity.getName();
        UUID id = entity.getId();
        solutionRepository.delete(entity);
        log.info("Solution deleted: {} (id: {})", name, id);
    }

    /**
     * Adds a service as a member of a solution.
     *
     * @param solutionId the solution ID
     * @param request    the add member request
     * @return the created solution member response
     * @throws NotFoundException   if the solution or service does not exist
     * @throws ValidationException if the service is already a member of the solution
     */
    @Transactional
    public SolutionMemberResponse addMember(UUID solutionId, AddSolutionMemberRequest request) {
        Solution solution = solutionRepository.findById(solutionId)
                .orElseThrow(() -> new NotFoundException("Solution", solutionId));

        ServiceRegistration service = serviceRepository.findById(request.serviceId())
                .orElseThrow(() -> new NotFoundException("ServiceRegistration", request.serviceId()));

        if (memberRepository.existsBySolutionIdAndServiceId(solutionId, request.serviceId())) {
            throw new ValidationException(
                    "Service " + service.getName() + " is already a member of solution " + solution.getName());
        }

        int displayOrder = request.displayOrder() != null
                ? request.displayOrder()
                : (int) memberRepository.countBySolutionId(solutionId);

        SolutionMember member = SolutionMember.builder()
                .solution(solution)
                .service(service)
                .role(request.role())
                .displayOrder(displayOrder)
                .notes(request.notes())
                .build();

        member = memberRepository.save(member);
        log.info("Added service {} to solution {} with role {}",
                service.getName(), solution.getName(), request.role());

        return mapMemberToResponse(member);
    }

    /**
     * Updates a solution member's role, display order, or notes.
     *
     * @param solutionId the solution ID
     * @param serviceId  the service ID
     * @param request    the update request with optional fields
     * @return the updated solution member response
     * @throws NotFoundException if the membership does not exist
     */
    @Transactional
    public SolutionMemberResponse updateMember(UUID solutionId, UUID serviceId,
                                                 UpdateSolutionMemberRequest request) {
        SolutionMember member = memberRepository.findBySolutionIdAndServiceId(solutionId, serviceId)
                .orElseThrow(() -> new NotFoundException(
                        "SolutionMember for solution " + solutionId + " and service " + serviceId));

        if (request.role() != null) member.setRole(request.role());
        if (request.displayOrder() != null) member.setDisplayOrder(request.displayOrder());
        if (request.notes() != null) member.setNotes(request.notes());

        member = memberRepository.save(member);
        return mapMemberToResponse(member);
    }

    /**
     * Removes a service from a solution.
     *
     * @param solutionId the solution ID
     * @param serviceId  the service ID
     * @throws NotFoundException if the membership does not exist
     */
    @Transactional
    public void removeMember(UUID solutionId, UUID serviceId) {
        if (!memberRepository.existsBySolutionIdAndServiceId(solutionId, serviceId)) {
            throw new NotFoundException(
                    "SolutionMember for solution " + solutionId + " and service " + serviceId);
        }
        memberRepository.deleteBySolutionIdAndServiceId(solutionId, serviceId);
        log.info("Removed service {} from solution {}", serviceId, solutionId);
    }

    /**
     * Reorders members within a solution by assigning sequential display orders.
     *
     * @param solutionId       the solution ID
     * @param orderedServiceIds the service IDs in desired order
     * @return the reordered member list
     * @throws NotFoundException   if the solution does not exist
     * @throws ValidationException if any service ID is not a member of the solution
     */
    @Transactional
    public List<SolutionMemberResponse> reorderMembers(UUID solutionId, List<UUID> orderedServiceIds) {
        if (!solutionRepository.existsById(solutionId)) {
            throw new NotFoundException("Solution", solutionId);
        }

        List<SolutionMember> members = memberRepository.findBySolutionId(solutionId);

        for (UUID serviceId : orderedServiceIds) {
            boolean found = members.stream().anyMatch(m -> m.getService().getId().equals(serviceId));
            if (!found) {
                throw new ValidationException("Service " + serviceId + " is not a member of solution " + solutionId);
            }
        }

        for (int i = 0; i < orderedServiceIds.size(); i++) {
            UUID serviceId = orderedServiceIds.get(i);
            SolutionMember member = members.stream()
                    .filter(m -> m.getService().getId().equals(serviceId))
                    .findFirst()
                    .orElseThrow();
            member.setDisplayOrder(i);
        }

        memberRepository.saveAll(members);
        return memberRepository.findBySolutionIdOrderByDisplayOrderAsc(solutionId).stream()
                .map(this::mapMemberToResponse)
                .toList();
    }

    /**
     * Aggregates health status across all services in a solution.
     *
     * <p>Uses cached health data from service registrations rather than performing
     * live health checks. Aggregation logic: DOWN if any service is DOWN, DEGRADED if
     * any service is DEGRADED, UNKNOWN if any service has no health data, UP otherwise.</p>
     *
     * @param solutionId the solution ID
     * @return the aggregated health response
     * @throws NotFoundException if the solution does not exist
     */
    public SolutionHealthResponse getSolutionHealth(UUID solutionId) {
        Solution solution = solutionRepository.findById(solutionId)
                .orElseThrow(() -> new NotFoundException("Solution", solutionId));

        List<SolutionMember> members = memberRepository.findBySolutionId(solutionId);

        int up = 0, down = 0, degraded = 0, unknown = 0;
        List<ServiceHealthResponse> serviceHealths = new java.util.ArrayList<>();

        for (SolutionMember member : members) {
            ServiceRegistration svc = member.getService();
            HealthStatus status = svc.getLastHealthStatus() != null ? svc.getLastHealthStatus() : HealthStatus.UNKNOWN;

            switch (status) {
                case UP -> up++;
                case DOWN -> down++;
                case DEGRADED -> degraded++;
                case UNKNOWN -> unknown++;
            }

            serviceHealths.add(new ServiceHealthResponse(
                    svc.getId(), svc.getName(), svc.getSlug(),
                    status, svc.getLastHealthCheckAt(), svc.getHealthCheckUrl(),
                    null, null));
        }

        HealthStatus aggregated;
        if (members.isEmpty()) {
            aggregated = HealthStatus.UNKNOWN;
        } else if (down > 0) {
            aggregated = HealthStatus.DOWN;
        } else if (degraded > 0) {
            aggregated = HealthStatus.DEGRADED;
        } else if (unknown > 0) {
            aggregated = HealthStatus.UNKNOWN;
        } else {
            aggregated = HealthStatus.UP;
        }

        return new SolutionHealthResponse(
                solution.getId(),
                solution.getName(),
                members.size(),
                up, down, degraded, unknown,
                aggregated,
                serviceHealths);
    }

    // ──────────────────────────────────────────────
    // Mapping helpers
    // ──────────────────────────────────────────────

    /**
     * Maps a Solution entity to a response DTO.
     *
     * @param entity        the entity
     * @param computeCount  if true, compute memberCount via query; if false, set to 0
     */
    private SolutionResponse mapToResponse(Solution entity, boolean computeCount) {
        int memberCount = computeCount
                ? (int) memberRepository.countBySolutionId(entity.getId())
                : 0;

        return new SolutionResponse(
                entity.getId(),
                entity.getTeamId(),
                entity.getName(),
                entity.getSlug(),
                entity.getDescription(),
                entity.getCategory(),
                entity.getStatus(),
                entity.getIconName(),
                entity.getColorHex(),
                entity.getOwnerUserId(),
                entity.getRepositoryUrl(),
                entity.getDocumentationUrl(),
                entity.getMetadataJson(),
                entity.getCreatedByUserId(),
                memberCount,
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private SolutionMemberResponse mapMemberToResponse(SolutionMember member) {
        ServiceRegistration svc = member.getService();
        return new SolutionMemberResponse(
                member.getId(),
                member.getSolution().getId(),
                svc.getId(),
                svc.getName(),
                svc.getSlug(),
                svc.getServiceType(),
                svc.getStatus(),
                svc.getLastHealthStatus(),
                member.getRole(),
                member.getDisplayOrder(),
                member.getNotes());
    }
}
