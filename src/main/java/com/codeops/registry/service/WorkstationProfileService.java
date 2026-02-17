package com.codeops.registry.service;

import com.codeops.registry.config.AppConstants;
import com.codeops.registry.dto.request.CreateWorkstationProfileRequest;
import com.codeops.registry.dto.request.UpdateWorkstationProfileRequest;
import com.codeops.registry.dto.response.DependencyNodeResponse;
import com.codeops.registry.dto.response.WorkstationProfileResponse;
import com.codeops.registry.dto.response.WorkstationServiceEntry;
import com.codeops.registry.entity.ServiceRegistration;
import com.codeops.registry.entity.Solution;
import com.codeops.registry.entity.SolutionMember;
import com.codeops.registry.entity.WorkstationProfile;
import com.codeops.registry.entity.enums.HealthStatus;
import com.codeops.registry.exception.NotFoundException;
import com.codeops.registry.exception.ValidationException;
import com.codeops.registry.repository.ServiceRegistrationRepository;
import com.codeops.registry.repository.SolutionMemberRepository;
import com.codeops.registry.repository.SolutionRepository;
import com.codeops.registry.repository.WorkstationProfileRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing workstation profiles — named startup configurations
 * that define which subset of services to run during local development.
 *
 * <p>Profiles store service IDs and computed startup order as JSON, enabling
 * workstation tooling to spin up the correct service set in the right order.
 * Startup order is derived from the team's dependency graph via
 * {@link DependencyGraphService}.</p>
 *
 * @see WorkstationProfile
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class WorkstationProfileService {

    private final WorkstationProfileRepository profileRepository;
    private final ServiceRegistrationRepository serviceRepository;
    private final SolutionMemberRepository solutionMemberRepository;
    private final SolutionRepository solutionRepository;
    private final DependencyGraphService dependencyGraphService;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Creates a new workstation profile for a team.
     *
     * <p>Validates team profile limit, name uniqueness, and service ownership.
     * Resolves services from either explicit IDs or a solution's members.
     * Computes startup order from the team's dependency graph. If marked as
     * default, clears any existing default for the team.</p>
     *
     * @param request       the creation request
     * @param currentUserId the user creating the profile
     * @return the created profile response with enriched service details
     * @throws ValidationException if limit exceeded, name taken, IDs invalid, or no services provided
     */
    @Transactional
    public WorkstationProfileResponse createProfile(CreateWorkstationProfileRequest request, UUID currentUserId) {
        long count = profileRepository.countByTeamId(request.teamId());
        if (count >= AppConstants.MAX_WORKSTATION_PROFILES_PER_TEAM) {
            throw new ValidationException(
                    "Team has reached the maximum of " + AppConstants.MAX_WORKSTATION_PROFILES_PER_TEAM
                            + " workstation profiles");
        }

        if (profileRepository.findByTeamIdAndName(request.teamId(), request.name()).isPresent()) {
            throw new ValidationException(
                    "A workstation profile named '" + request.name() + "' already exists for this team");
        }

        List<UUID> serviceIds = resolveServiceIds(request);

        List<ServiceRegistration> services = validateServiceIds(serviceIds, request.teamId());

        List<UUID> startupOrder = computeProfileStartupOrder(serviceIds, request.teamId());

        if (request.isDefault()) {
            clearExistingDefault(request.teamId(), null);
        }

        WorkstationProfile entity = WorkstationProfile.builder()
                .teamId(request.teamId())
                .name(request.name())
                .description(request.description())
                .solutionId(request.solutionId())
                .servicesJson(serializeServiceIds(serviceIds))
                .startupOrder(serializeServiceIds(startupOrder))
                .createdByUserId(currentUserId)
                .isDefault(request.isDefault())
                .build();

        entity = profileRepository.save(entity);
        log.info("Workstation profile created: {} for team {}", entity.getName(), entity.getTeamId());

        return enrichResponse(entity, services);
    }

    /**
     * Retrieves a single workstation profile with enriched service details.
     *
     * @param profileId the profile ID
     * @return the enriched profile response
     * @throws NotFoundException if the profile does not exist
     */
    public WorkstationProfileResponse getProfile(UUID profileId) {
        WorkstationProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new NotFoundException("WorkstationProfile", profileId));

        List<UUID> serviceIds = parseServiceIds(profile.getServicesJson());
        List<ServiceRegistration> services = serviceIds.isEmpty()
                ? List.of()
                : serviceRepository.findByTeamIdAndIdIn(profile.getTeamId(), serviceIds);

        return enrichResponse(profile, services);
    }

    /**
     * Lists all workstation profiles for a team.
     *
     * <p>Returns lightweight responses without enriched service details
     * to avoid N+1 queries. Use {@link #getProfile(UUID)} for full detail.</p>
     *
     * @param teamId the team ID
     * @return list of profile responses (services list empty for performance)
     */
    public List<WorkstationProfileResponse> getProfilesForTeam(UUID teamId) {
        return profileRepository.findByTeamId(teamId).stream()
                .map(this::lightweightResponse)
                .toList();
    }

    /**
     * Retrieves the team's default workstation profile with enriched service details.
     *
     * @param teamId the team ID
     * @return the default profile response
     * @throws NotFoundException if no default profile is set for the team
     */
    public WorkstationProfileResponse getDefaultProfile(UUID teamId) {
        WorkstationProfile profile = profileRepository.findByTeamIdAndIsDefaultTrue(teamId)
                .orElseThrow(() -> new NotFoundException(
                        "No default workstation profile set for team: " + teamId));

        List<UUID> serviceIds = parseServiceIds(profile.getServicesJson());
        List<ServiceRegistration> services = serviceIds.isEmpty()
                ? List.of()
                : serviceRepository.findByTeamIdAndIdIn(profile.getTeamId(), serviceIds);

        return enrichResponse(profile, services);
    }

    /**
     * Partially updates a workstation profile with non-null fields.
     *
     * <p>If the name is changed, uniqueness is validated (excluding the current profile).
     * If service IDs are changed, they are validated and startup order is recomputed.
     * If marked as default, any existing default for the team is cleared.</p>
     *
     * @param profileId the profile ID
     * @param request   the update request with optional fields
     * @return the updated profile response
     * @throws NotFoundException   if the profile does not exist
     * @throws ValidationException if the new name conflicts with another profile or service IDs are invalid
     */
    @Transactional
    public WorkstationProfileResponse updateProfile(UUID profileId, UpdateWorkstationProfileRequest request) {
        WorkstationProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new NotFoundException("WorkstationProfile", profileId));

        if (request.name() != null) {
            profileRepository.findByTeamIdAndName(profile.getTeamId(), request.name())
                    .filter(existing -> !existing.getId().equals(profileId))
                    .ifPresent(existing -> {
                        throw new ValidationException(
                                "A workstation profile named '" + request.name()
                                        + "' already exists for this team");
                    });
            profile.setName(request.name());
        }

        if (request.description() != null) {
            profile.setDescription(request.description());
        }

        if (request.serviceIds() != null) {
            List<ServiceRegistration> validatedServices = validateServiceIds(
                    request.serviceIds(), profile.getTeamId());
            profile.setServicesJson(serializeServiceIds(request.serviceIds()));

            List<UUID> startupOrder = computeProfileStartupOrder(request.serviceIds(), profile.getTeamId());
            profile.setStartupOrder(serializeServiceIds(startupOrder));
        }

        if (request.isDefault() != null && request.isDefault()) {
            clearExistingDefault(profile.getTeamId(), profileId);
            profile.setIsDefault(true);
        } else if (request.isDefault() != null) {
            profile.setIsDefault(false);
        }

        profile = profileRepository.save(profile);

        List<UUID> serviceIds = parseServiceIds(profile.getServicesJson());
        List<ServiceRegistration> services = serviceIds.isEmpty()
                ? List.of()
                : serviceRepository.findByTeamIdAndIdIn(profile.getTeamId(), serviceIds);

        return enrichResponse(profile, services);
    }

    /**
     * Deletes a workstation profile.
     *
     * @param profileId the profile ID
     * @throws NotFoundException if the profile does not exist
     */
    @Transactional
    public void deleteProfile(UUID profileId) {
        WorkstationProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new NotFoundException("WorkstationProfile", profileId));

        profileRepository.delete(profile);
        log.info("Workstation profile deleted: {} (id: {})", profile.getName(), profile.getId());
    }

    /**
     * Sets a workstation profile as the team's default, clearing any existing default.
     *
     * @param profileId the profile ID to set as default
     * @return the updated profile response
     * @throws NotFoundException if the profile does not exist
     */
    @Transactional
    public WorkstationProfileResponse setDefault(UUID profileId) {
        WorkstationProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new NotFoundException("WorkstationProfile", profileId));

        clearExistingDefault(profile.getTeamId(), profileId);

        profile.setIsDefault(true);
        profile = profileRepository.save(profile);

        List<UUID> serviceIds = parseServiceIds(profile.getServicesJson());
        List<ServiceRegistration> services = serviceIds.isEmpty()
                ? List.of()
                : serviceRepository.findByTeamIdAndIdIn(profile.getTeamId(), serviceIds);

        return enrichResponse(profile, services);
    }

    /**
     * Quick-creates a workstation profile from a solution's member services.
     *
     * <p>Loads the solution and its members, then delegates to
     * {@link #createProfile} with the solution name as the profile name
     * and the solution's service IDs.</p>
     *
     * @param solutionId    the solution ID
     * @param teamId        the team ID
     * @param currentUserId the user creating the profile
     * @return the created profile response
     * @throws NotFoundException if the solution does not exist
     */
    @Transactional
    public WorkstationProfileResponse createFromSolution(UUID solutionId, UUID teamId, UUID currentUserId) {
        Solution solution = solutionRepository.findById(solutionId)
                .orElseThrow(() -> new NotFoundException("Solution", solutionId));

        List<SolutionMember> members = solutionMemberRepository.findBySolutionId(solutionId);
        List<UUID> serviceIds = members.stream()
                .map(m -> m.getService().getId())
                .toList();

        CreateWorkstationProfileRequest request = new CreateWorkstationProfileRequest(
                teamId,
                "Solution: " + solution.getName(),
                solution.getDescription(),
                solutionId,
                serviceIds,
                false);

        return createProfile(request, currentUserId);
    }

    /**
     * Recomputes the startup order for a profile based on the current dependency graph.
     *
     * <p>Useful after dependency changes to ensure the profile's startup order
     * reflects the latest topological sort.</p>
     *
     * @param profileId the profile ID
     * @return the updated profile response
     * @throws NotFoundException if the profile does not exist
     */
    @Transactional
    public WorkstationProfileResponse refreshStartupOrder(UUID profileId) {
        WorkstationProfile profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new NotFoundException("WorkstationProfile", profileId));

        List<UUID> serviceIds = parseServiceIds(profile.getServicesJson());
        List<UUID> startupOrder = computeProfileStartupOrder(serviceIds, profile.getTeamId());

        profile.setStartupOrder(serializeServiceIds(startupOrder));
        profile = profileRepository.save(profile);

        List<ServiceRegistration> services = serviceIds.isEmpty()
                ? List.of()
                : serviceRepository.findByTeamIdAndIdIn(profile.getTeamId(), serviceIds);

        return enrichResponse(profile, services);
    }

    // ──────────────────────────────────────────────
    // Package-private helpers
    // ──────────────────────────────────────────────

    /**
     * Parses a JSON array string into a list of UUIDs.
     *
     * @param json the JSON array string (may be null or blank)
     * @return the parsed UUID list, or empty list if input is null/blank
     * @throws ValidationException if the JSON is malformed
     */
    List<UUID> parseServiceIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, MAPPER.getTypeFactory()
                    .constructCollectionType(List.class, UUID.class));
        } catch (JsonProcessingException e) {
            throw new ValidationException("Invalid service ID JSON: " + e.getMessage());
        }
    }

    /**
     * Serializes a list of UUIDs to a JSON array string.
     *
     * @param ids the UUID list
     * @return the JSON array string
     * @throws ValidationException if serialization fails
     */
    String serializeServiceIds(List<UUID> ids) {
        try {
            return MAPPER.writeValueAsString(ids);
        } catch (JsonProcessingException e) {
            throw new ValidationException("Failed to serialize service IDs: " + e.getMessage());
        }
    }

    /**
     * Builds an enriched response with service details and startup positions.
     *
     * @param profile  the workstation profile entity
     * @param services the loaded service registrations
     * @return the enriched profile response
     */
    WorkstationProfileResponse enrichResponse(WorkstationProfile profile, List<ServiceRegistration> services) {
        List<UUID> serviceIds = parseServiceIds(profile.getServicesJson());
        List<UUID> startupOrderIds = parseServiceIds(profile.getStartupOrder());

        Map<UUID, Integer> positionMap = new HashMap<>();
        for (int i = 0; i < startupOrderIds.size(); i++) {
            positionMap.put(startupOrderIds.get(i), i + 1);
        }

        List<WorkstationServiceEntry> entries = services.stream()
                .map(svc -> new WorkstationServiceEntry(
                        svc.getId(),
                        svc.getName(),
                        svc.getSlug(),
                        svc.getServiceType(),
                        svc.getStatus(),
                        svc.getLastHealthStatus() != null ? svc.getLastHealthStatus() : HealthStatus.UNKNOWN,
                        positionMap.getOrDefault(svc.getId(), 0)))
                .toList();

        return new WorkstationProfileResponse(
                profile.getId(),
                profile.getTeamId(),
                profile.getName(),
                profile.getDescription(),
                profile.getSolutionId(),
                serviceIds,
                entries,
                startupOrderIds,
                profile.getIsDefault(),
                profile.getCreatedByUserId(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }

    /**
     * Computes the startup order for a profile's services by filtering the team's
     * full topological order to only include services in the profile.
     *
     * @param profileServiceIds the service IDs in this profile
     * @param teamId            the team ID
     * @return the filtered startup order preserving topological ordering
     */
    List<UUID> computeProfileStartupOrder(List<UUID> profileServiceIds, UUID teamId) {
        List<DependencyNodeResponse> fullOrder = dependencyGraphService.getStartupOrder(teamId);
        Set<UUID> profileSet = new HashSet<>(profileServiceIds);

        return fullOrder.stream()
                .map(DependencyNodeResponse::serviceId)
                .filter(profileSet::contains)
                .toList();
    }

    // ──────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────

    /**
     * Resolves service IDs from the creation request, either from explicit IDs
     * or from a solution's members.
     */
    private List<UUID> resolveServiceIds(CreateWorkstationProfileRequest request) {
        if (request.serviceIds() != null && !request.serviceIds().isEmpty()) {
            return request.serviceIds();
        }
        if (request.solutionId() != null) {
            List<SolutionMember> members = solutionMemberRepository.findBySolutionId(request.solutionId());
            return members.stream()
                    .map(m -> m.getService().getId())
                    .toList();
        }
        throw new ValidationException("Either solutionId or serviceIds required");
    }

    /**
     * Validates that all service IDs exist and belong to the specified team.
     */
    private List<ServiceRegistration> validateServiceIds(List<UUID> serviceIds, UUID teamId) {
        if (serviceIds.isEmpty()) {
            return List.of();
        }
        List<ServiceRegistration> services = serviceRepository.findByTeamIdAndIdIn(teamId, serviceIds);
        if (services.size() != serviceIds.size()) {
            Set<UUID> foundIds = services.stream()
                    .map(ServiceRegistration::getId)
                    .collect(Collectors.toSet());
            List<UUID> invalidIds = serviceIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .toList();
            throw new ValidationException("Invalid service IDs: " + invalidIds);
        }
        return services;
    }

    /**
     * Clears any existing default profile for a team, optionally excluding a specific profile.
     */
    private void clearExistingDefault(UUID teamId, UUID excludeProfileId) {
        profileRepository.findByTeamIdAndIsDefaultTrue(teamId)
                .filter(existing -> excludeProfileId == null || !existing.getId().equals(excludeProfileId))
                .ifPresent(existing -> {
                    existing.setIsDefault(false);
                    profileRepository.save(existing);
                });
    }

    /**
     * Builds a lightweight response without loading service details (for list views).
     */
    private WorkstationProfileResponse lightweightResponse(WorkstationProfile profile) {
        List<UUID> serviceIds = parseServiceIds(profile.getServicesJson());
        List<UUID> startupOrderIds = parseServiceIds(profile.getStartupOrder());

        return new WorkstationProfileResponse(
                profile.getId(),
                profile.getTeamId(),
                profile.getName(),
                profile.getDescription(),
                profile.getSolutionId(),
                serviceIds,
                List.of(),
                startupOrderIds,
                profile.getIsDefault(),
                profile.getCreatedByUserId(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }
}
