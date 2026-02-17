package com.codeops.registry.service;

import com.codeops.registry.config.AppConstants;
import com.codeops.registry.dto.request.CreateWorkstationProfileRequest;
import com.codeops.registry.dto.request.UpdateWorkstationProfileRequest;
import com.codeops.registry.dto.response.DependencyNodeResponse;
import com.codeops.registry.dto.response.WorkstationProfileResponse;
import com.codeops.registry.entity.ServiceRegistration;
import com.codeops.registry.entity.Solution;
import com.codeops.registry.entity.SolutionMember;
import com.codeops.registry.entity.WorkstationProfile;
import com.codeops.registry.entity.enums.HealthStatus;
import com.codeops.registry.entity.enums.ServiceStatus;
import com.codeops.registry.entity.enums.ServiceType;
import com.codeops.registry.entity.enums.SolutionCategory;
import com.codeops.registry.entity.enums.SolutionMemberRole;
import com.codeops.registry.entity.enums.SolutionStatus;
import com.codeops.registry.exception.NotFoundException;
import com.codeops.registry.exception.ValidationException;
import com.codeops.registry.repository.ServiceRegistrationRepository;
import com.codeops.registry.repository.SolutionMemberRepository;
import com.codeops.registry.repository.SolutionRepository;
import com.codeops.registry.repository.WorkstationProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WorkstationProfileService}.
 *
 * <p>Tests cover profile CRUD, default management, solution-based creation,
 * startup order computation, and all validation paths.</p>
 */
@ExtendWith(MockitoExtension.class)
class WorkstationProfileServiceTest {

    @Mock
    private WorkstationProfileRepository profileRepository;

    @Mock
    private ServiceRegistrationRepository serviceRepository;

    @Mock
    private SolutionMemberRepository solutionMemberRepository;

    @Mock
    private SolutionRepository solutionRepository;

    @Mock
    private DependencyGraphService dependencyGraphService;

    @InjectMocks
    private WorkstationProfileService workstationProfileService;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PROFILE_ID = UUID.randomUUID();
    private static final UUID PROFILE_ID_2 = UUID.randomUUID();
    private static final UUID SOLUTION_ID = UUID.randomUUID();
    private static final UUID SERVICE_ID_1 = UUID.randomUUID();
    private static final UUID SERVICE_ID_2 = UUID.randomUUID();
    private static final UUID SERVICE_ID_3 = UUID.randomUUID();

    // ──────────────────────────────────────────────────────────────
    // Helper methods
    // ──────────────────────────────────────────────────────────────

    private ServiceRegistration buildService(UUID serviceId, String name, String slug) {
        ServiceRegistration service = ServiceRegistration.builder()
                .teamId(TEAM_ID)
                .name(name)
                .slug(slug)
                .serviceType(ServiceType.SPRING_BOOT_API)
                .status(ServiceStatus.ACTIVE)
                .createdByUserId(USER_ID)
                .build();
        service.setId(serviceId);
        service.setCreatedAt(Instant.now());
        service.setUpdatedAt(Instant.now());
        service.setLastHealthStatus(HealthStatus.UP);
        return service;
    }

    private WorkstationProfile buildProfile(UUID profileId, String name, List<UUID> serviceIds,
                                            List<UUID> startupOrder, boolean isDefault) {
        WorkstationProfile profile = WorkstationProfile.builder()
                .teamId(TEAM_ID)
                .name(name)
                .description("Test profile")
                .servicesJson(serializeIds(serviceIds))
                .startupOrder(serializeIds(startupOrder))
                .createdByUserId(USER_ID)
                .isDefault(isDefault)
                .build();
        profile.setId(profileId);
        profile.setCreatedAt(Instant.now());
        profile.setUpdatedAt(Instant.now());
        return profile;
    }

    private Solution buildSolution(UUID solutionId, String name) {
        Solution solution = Solution.builder()
                .teamId(TEAM_ID)
                .name(name)
                .slug("solution-slug")
                .description("Solution description")
                .category(SolutionCategory.PLATFORM)
                .status(SolutionStatus.ACTIVE)
                .createdByUserId(USER_ID)
                .build();
        solution.setId(solutionId);
        solution.setCreatedAt(Instant.now());
        solution.setUpdatedAt(Instant.now());
        return solution;
    }

    private SolutionMember buildSolutionMember(Solution solution, ServiceRegistration service) {
        SolutionMember member = SolutionMember.builder()
                .solution(solution)
                .service(service)
                .role(SolutionMemberRole.CORE)
                .displayOrder(0)
                .build();
        member.setId(UUID.randomUUID());
        member.setCreatedAt(Instant.now());
        member.setUpdatedAt(Instant.now());
        return member;
    }

    private List<DependencyNodeResponse> buildStartupOrder(UUID... serviceIds) {
        List<DependencyNodeResponse> order = new java.util.ArrayList<>();
        for (UUID id : serviceIds) {
            order.add(new DependencyNodeResponse(
                    id, "svc-" + id.toString().substring(0, 4), "slug",
                    ServiceType.SPRING_BOOT_API, ServiceStatus.ACTIVE, HealthStatus.UNKNOWN));
        }
        return order;
    }

    private String serializeIds(List<UUID> ids) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(ids);
        } catch (Exception e) {
            return "[]";
        }
    }

    private void mockStandardCreateDependencies(List<UUID> serviceIds) {
        when(profileRepository.countByTeamId(TEAM_ID)).thenReturn(0L);
        when(profileRepository.findByTeamIdAndName(eq(TEAM_ID), any())).thenReturn(Optional.empty());

        List<ServiceRegistration> services = serviceIds.stream()
                .map(id -> buildService(id, "svc-" + id.toString().substring(0, 4), "slug-" + id.toString().substring(0, 4)))
                .toList();
        when(serviceRepository.findByTeamIdAndIdIn(eq(TEAM_ID), eq(serviceIds))).thenReturn(services);
        when(dependencyGraphService.getStartupOrder(TEAM_ID))
                .thenReturn(buildStartupOrder(serviceIds.toArray(new UUID[0])));
        when(profileRepository.save(any(WorkstationProfile.class))).thenAnswer(invocation -> {
            WorkstationProfile saved = invocation.getArgument(0);
            saved.setId(PROFILE_ID);
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });
    }

    // ──────────────────────────────────────────────────────────────
    // createProfile tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void createProfile_withServiceIds_success() {
        List<UUID> serviceIds = List.of(SERVICE_ID_1, SERVICE_ID_2);
        mockStandardCreateDependencies(serviceIds);

        CreateWorkstationProfileRequest request = new CreateWorkstationProfileRequest(
                TEAM_ID, "Full Platform", "All services", null, serviceIds, false);

        WorkstationProfileResponse response = workstationProfileService.createProfile(request, USER_ID);

        assertThat(response.id()).isEqualTo(PROFILE_ID);
        assertThat(response.teamId()).isEqualTo(TEAM_ID);
        assertThat(response.name()).isEqualTo("Full Platform");
        assertThat(response.serviceIds()).hasSize(2);
        assertThat(response.startupOrder()).hasSize(2);
        assertThat(response.services()).hasSize(2);
        assertThat(response.isDefault()).isFalse();
        verify(profileRepository).save(any(WorkstationProfile.class));
    }

    @Test
    void createProfile_withSolutionId_loadsMembersFromSolution() {
        ServiceRegistration svc1 = buildService(SERVICE_ID_1, "Auth", "auth");
        ServiceRegistration svc2 = buildService(SERVICE_ID_2, "Gateway", "gateway");
        Solution solution = buildSolution(SOLUTION_ID, "My Solution");

        SolutionMember m1 = buildSolutionMember(solution, svc1);
        SolutionMember m2 = buildSolutionMember(solution, svc2);

        when(profileRepository.countByTeamId(TEAM_ID)).thenReturn(0L);
        when(profileRepository.findByTeamIdAndName(eq(TEAM_ID), any())).thenReturn(Optional.empty());
        when(solutionMemberRepository.findBySolutionId(SOLUTION_ID)).thenReturn(List.of(m1, m2));
        when(serviceRepository.findByTeamIdAndIdIn(eq(TEAM_ID), any())).thenReturn(List.of(svc1, svc2));
        when(dependencyGraphService.getStartupOrder(TEAM_ID))
                .thenReturn(buildStartupOrder(SERVICE_ID_1, SERVICE_ID_2));
        when(profileRepository.save(any(WorkstationProfile.class))).thenAnswer(invocation -> {
            WorkstationProfile saved = invocation.getArgument(0);
            saved.setId(PROFILE_ID);
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });

        CreateWorkstationProfileRequest request = new CreateWorkstationProfileRequest(
                TEAM_ID, "From Solution", null, SOLUTION_ID, null, false);

        WorkstationProfileResponse response = workstationProfileService.createProfile(request, USER_ID);

        assertThat(response.serviceIds()).containsExactlyInAnyOrder(SERVICE_ID_1, SERVICE_ID_2);
        verify(solutionMemberRepository).findBySolutionId(SOLUTION_ID);
    }

    @Test
    void createProfile_nameAlreadyExists_throwsValidation() {
        WorkstationProfile existing = buildProfile(PROFILE_ID_2, "Duplicate Name",
                List.of(SERVICE_ID_1), List.of(SERVICE_ID_1), false);

        when(profileRepository.countByTeamId(TEAM_ID)).thenReturn(0L);
        when(profileRepository.findByTeamIdAndName(TEAM_ID, "Duplicate Name"))
                .thenReturn(Optional.of(existing));

        CreateWorkstationProfileRequest request = new CreateWorkstationProfileRequest(
                TEAM_ID, "Duplicate Name", null, null, List.of(SERVICE_ID_1), false);

        assertThatThrownBy(() -> workstationProfileService.createProfile(request, USER_ID))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("already exists");

        verify(profileRepository, never()).save(any(WorkstationProfile.class));
    }

    @Test
    void createProfile_teamLimitExceeded_throwsValidation() {
        when(profileRepository.countByTeamId(TEAM_ID))
                .thenReturn((long) AppConstants.MAX_WORKSTATION_PROFILES_PER_TEAM);

        CreateWorkstationProfileRequest request = new CreateWorkstationProfileRequest(
                TEAM_ID, "Overflow", null, null, List.of(SERVICE_ID_1), false);

        assertThatThrownBy(() -> workstationProfileService.createProfile(request, USER_ID))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("maximum of " + AppConstants.MAX_WORKSTATION_PROFILES_PER_TEAM);

        verify(profileRepository, never()).save(any(WorkstationProfile.class));
    }

    @Test
    void createProfile_invalidServiceId_throwsValidation() {
        UUID invalidId = UUID.randomUUID();
        ServiceRegistration svc1 = buildService(SERVICE_ID_1, "Auth", "auth");

        when(profileRepository.countByTeamId(TEAM_ID)).thenReturn(0L);
        when(profileRepository.findByTeamIdAndName(eq(TEAM_ID), any())).thenReturn(Optional.empty());
        when(serviceRepository.findByTeamIdAndIdIn(eq(TEAM_ID), eq(List.of(SERVICE_ID_1, invalidId))))
                .thenReturn(List.of(svc1));

        CreateWorkstationProfileRequest request = new CreateWorkstationProfileRequest(
                TEAM_ID, "Bad IDs", null, null, List.of(SERVICE_ID_1, invalidId), false);

        assertThatThrownBy(() -> workstationProfileService.createProfile(request, USER_ID))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Invalid service IDs");

        verify(profileRepository, never()).save(any(WorkstationProfile.class));
    }

    @Test
    void createProfile_neitherSolutionIdNorServiceIds_throwsValidation() {
        when(profileRepository.countByTeamId(TEAM_ID)).thenReturn(0L);
        when(profileRepository.findByTeamIdAndName(eq(TEAM_ID), any())).thenReturn(Optional.empty());

        CreateWorkstationProfileRequest request = new CreateWorkstationProfileRequest(
                TEAM_ID, "Empty Profile", null, null, null, false);

        assertThatThrownBy(() -> workstationProfileService.createProfile(request, USER_ID))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Either solutionId or serviceIds required");

        verify(profileRepository, never()).save(any(WorkstationProfile.class));
    }

    @Test
    void createProfile_isDefaultTrue_clearsExistingDefault() {
        List<UUID> serviceIds = List.of(SERVICE_ID_1);
        WorkstationProfile existingDefault = buildProfile(PROFILE_ID_2, "Old Default",
                List.of(SERVICE_ID_2), List.of(SERVICE_ID_2), true);

        mockStandardCreateDependencies(serviceIds);
        when(profileRepository.findByTeamIdAndIsDefaultTrue(TEAM_ID))
                .thenReturn(Optional.of(existingDefault));

        CreateWorkstationProfileRequest request = new CreateWorkstationProfileRequest(
                TEAM_ID, "New Default", null, null, serviceIds, true);

        WorkstationProfileResponse response = workstationProfileService.createProfile(request, USER_ID);

        assertThat(response.isDefault()).isTrue();
        assertThat(existingDefault.getIsDefault()).isFalse();
        verify(profileRepository).save(existingDefault);
    }

    // ──────────────────────────────────────────────────────────────
    // getProfile tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void getProfile_found_returnsEnrichedResponse() {
        WorkstationProfile profile = buildProfile(PROFILE_ID, "Full Platform",
                List.of(SERVICE_ID_1, SERVICE_ID_2),
                List.of(SERVICE_ID_2, SERVICE_ID_1), false);

        ServiceRegistration svc1 = buildService(SERVICE_ID_1, "Auth", "auth");
        ServiceRegistration svc2 = buildService(SERVICE_ID_2, "Gateway", "gateway");

        when(profileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile));
        when(serviceRepository.findByTeamIdAndIdIn(eq(TEAM_ID), any())).thenReturn(List.of(svc1, svc2));

        WorkstationProfileResponse response = workstationProfileService.getProfile(PROFILE_ID);

        assertThat(response.id()).isEqualTo(PROFILE_ID);
        assertThat(response.name()).isEqualTo("Full Platform");
        assertThat(response.serviceIds()).containsExactly(SERVICE_ID_1, SERVICE_ID_2);
        assertThat(response.services()).hasSize(2);
        assertThat(response.startupOrder()).containsExactly(SERVICE_ID_2, SERVICE_ID_1);

        // Check startup positions: SERVICE_ID_2 is position 1, SERVICE_ID_1 is position 2
        response.services().stream()
                .filter(s -> s.serviceId().equals(SERVICE_ID_2))
                .findFirst()
                .ifPresent(s -> assertThat(s.startupPosition()).isEqualTo(1));
        response.services().stream()
                .filter(s -> s.serviceId().equals(SERVICE_ID_1))
                .findFirst()
                .ifPresent(s -> assertThat(s.startupPosition()).isEqualTo(2));
    }

    @Test
    void getProfile_notFound_throwsNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(profileRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workstationProfileService.getProfile(unknownId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("WorkstationProfile");
    }

    // ──────────────────────────────────────────────────────────────
    // getProfilesForTeam tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void getProfilesForTeam_returnsAllProfiles() {
        WorkstationProfile p1 = buildProfile(UUID.randomUUID(), "Profile A",
                List.of(SERVICE_ID_1), List.of(SERVICE_ID_1), false);
        WorkstationProfile p2 = buildProfile(UUID.randomUUID(), "Profile B",
                List.of(SERVICE_ID_2), List.of(SERVICE_ID_2), false);
        WorkstationProfile p3 = buildProfile(UUID.randomUUID(), "Profile C",
                List.of(SERVICE_ID_3), List.of(SERVICE_ID_3), true);

        when(profileRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(p1, p2, p3));

        List<WorkstationProfileResponse> responses = workstationProfileService.getProfilesForTeam(TEAM_ID);

        assertThat(responses).hasSize(3);
        assertThat(responses.get(0).name()).isEqualTo("Profile A");
        assertThat(responses.get(1).name()).isEqualTo("Profile B");
        assertThat(responses.get(2).name()).isEqualTo("Profile C");
        // List views have empty services (lightweight)
        assertThat(responses.get(0).services()).isEmpty();
    }

    @Test
    void getProfilesForTeam_noProfiles_returnsEmptyList() {
        when(profileRepository.findByTeamId(TEAM_ID)).thenReturn(List.of());

        List<WorkstationProfileResponse> responses = workstationProfileService.getProfilesForTeam(TEAM_ID);

        assertThat(responses).isEmpty();
    }

    // ──────────────────────────────────────────────────────────────
    // getDefaultProfile tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void getDefaultProfile_exists_returnsEnrichedResponse() {
        WorkstationProfile profile = buildProfile(PROFILE_ID, "Default Profile",
                List.of(SERVICE_ID_1), List.of(SERVICE_ID_1), true);
        ServiceRegistration svc1 = buildService(SERVICE_ID_1, "Auth", "auth");

        when(profileRepository.findByTeamIdAndIsDefaultTrue(TEAM_ID))
                .thenReturn(Optional.of(profile));
        when(serviceRepository.findByTeamIdAndIdIn(eq(TEAM_ID), any())).thenReturn(List.of(svc1));

        WorkstationProfileResponse response = workstationProfileService.getDefaultProfile(TEAM_ID);

        assertThat(response.id()).isEqualTo(PROFILE_ID);
        assertThat(response.isDefault()).isTrue();
        assertThat(response.services()).hasSize(1);
    }

    @Test
    void getDefaultProfile_noDefault_throwsNotFound() {
        when(profileRepository.findByTeamIdAndIsDefaultTrue(TEAM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workstationProfileService.getDefaultProfile(TEAM_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("No default workstation profile");
    }

    // ──────────────────────────────────────────────────────────────
    // updateProfile tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void updateProfile_nameAndServiceIds_bothUpdated() {
        WorkstationProfile profile = buildProfile(PROFILE_ID, "Old Name",
                List.of(SERVICE_ID_1), List.of(SERVICE_ID_1), false);
        List<UUID> newServiceIds = List.of(SERVICE_ID_2, SERVICE_ID_3);
        ServiceRegistration svc2 = buildService(SERVICE_ID_2, "Gateway", "gateway");
        ServiceRegistration svc3 = buildService(SERVICE_ID_3, "Worker", "worker");

        when(profileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile));
        when(profileRepository.findByTeamIdAndName(TEAM_ID, "New Name")).thenReturn(Optional.empty());
        when(serviceRepository.findByTeamIdAndIdIn(eq(TEAM_ID), any()))
                .thenReturn(List.of(svc2, svc3));
        when(dependencyGraphService.getStartupOrder(TEAM_ID))
                .thenReturn(buildStartupOrder(SERVICE_ID_3, SERVICE_ID_2));
        when(profileRepository.save(any(WorkstationProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateWorkstationProfileRequest request = new UpdateWorkstationProfileRequest(
                "New Name", null, newServiceIds, null);

        WorkstationProfileResponse response = workstationProfileService.updateProfile(PROFILE_ID, request);

        assertThat(response.name()).isEqualTo("New Name");
        assertThat(response.serviceIds()).containsExactly(SERVICE_ID_2, SERVICE_ID_3);
        verify(profileRepository).save(any(WorkstationProfile.class));
    }

    @Test
    void updateProfile_nameConflict_throwsValidation() {
        WorkstationProfile profile = buildProfile(PROFILE_ID, "My Profile",
                List.of(SERVICE_ID_1), List.of(SERVICE_ID_1), false);
        WorkstationProfile conflicting = buildProfile(PROFILE_ID_2, "Taken Name",
                List.of(SERVICE_ID_2), List.of(SERVICE_ID_2), false);

        when(profileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile));
        when(profileRepository.findByTeamIdAndName(TEAM_ID, "Taken Name"))
                .thenReturn(Optional.of(conflicting));

        UpdateWorkstationProfileRequest request = new UpdateWorkstationProfileRequest(
                "Taken Name", null, null, null);

        assertThatThrownBy(() -> workstationProfileService.updateProfile(PROFILE_ID, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void updateProfile_notFound_throwsNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(profileRepository.findById(unknownId)).thenReturn(Optional.empty());

        UpdateWorkstationProfileRequest request = new UpdateWorkstationProfileRequest(
                "New Name", null, null, null);

        assertThatThrownBy(() -> workstationProfileService.updateProfile(unknownId, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("WorkstationProfile");
    }

    // ──────────────────────────────────────────────────────────────
    // deleteProfile tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void deleteProfile_success() {
        WorkstationProfile profile = buildProfile(PROFILE_ID, "Doomed Profile",
                List.of(SERVICE_ID_1), List.of(SERVICE_ID_1), false);

        when(profileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile));

        workstationProfileService.deleteProfile(PROFILE_ID);

        verify(profileRepository).delete(profile);
    }

    @Test
    void deleteProfile_notFound_throwsNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(profileRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workstationProfileService.deleteProfile(unknownId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("WorkstationProfile");

        verify(profileRepository, never()).delete(any(WorkstationProfile.class));
    }

    // ──────────────────────────────────────────────────────────────
    // setDefault tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void setDefault_clearsExistingDefault() {
        WorkstationProfile profile = buildProfile(PROFILE_ID, "New Default",
                List.of(SERVICE_ID_1), List.of(SERVICE_ID_1), false);
        WorkstationProfile oldDefault = buildProfile(PROFILE_ID_2, "Old Default",
                List.of(SERVICE_ID_2), List.of(SERVICE_ID_2), true);
        ServiceRegistration svc1 = buildService(SERVICE_ID_1, "Auth", "auth");

        when(profileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile));
        when(profileRepository.findByTeamIdAndIsDefaultTrue(TEAM_ID))
                .thenReturn(Optional.of(oldDefault));
        when(profileRepository.save(any(WorkstationProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(serviceRepository.findByTeamIdAndIdIn(eq(TEAM_ID), any())).thenReturn(List.of(svc1));

        WorkstationProfileResponse response = workstationProfileService.setDefault(PROFILE_ID);

        assertThat(response.isDefault()).isTrue();
        assertThat(oldDefault.getIsDefault()).isFalse();
        verify(profileRepository).save(oldDefault);
        verify(profileRepository).save(profile);
    }

    @Test
    void setDefault_notFound_throwsNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(profileRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workstationProfileService.setDefault(unknownId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("WorkstationProfile");
    }

    // ──────────────────────────────────────────────────────────────
    // createFromSolution tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void createFromSolution_success() {
        Solution solution = buildSolution(SOLUTION_ID, "My Platform");
        ServiceRegistration svc1 = buildService(SERVICE_ID_1, "Auth", "auth");
        ServiceRegistration svc2 = buildService(SERVICE_ID_2, "Gateway", "gateway");

        SolutionMember m1 = buildSolutionMember(solution, svc1);
        SolutionMember m2 = buildSolutionMember(solution, svc2);

        when(solutionRepository.findById(SOLUTION_ID)).thenReturn(Optional.of(solution));
        when(solutionMemberRepository.findBySolutionId(SOLUTION_ID)).thenReturn(List.of(m1, m2));

        // Mocks for the delegated createProfile call
        when(profileRepository.countByTeamId(TEAM_ID)).thenReturn(0L);
        when(profileRepository.findByTeamIdAndName(eq(TEAM_ID), eq("Solution: My Platform")))
                .thenReturn(Optional.empty());
        when(serviceRepository.findByTeamIdAndIdIn(eq(TEAM_ID), any())).thenReturn(List.of(svc1, svc2));
        when(dependencyGraphService.getStartupOrder(TEAM_ID))
                .thenReturn(buildStartupOrder(SERVICE_ID_1, SERVICE_ID_2));
        when(profileRepository.save(any(WorkstationProfile.class))).thenAnswer(invocation -> {
            WorkstationProfile saved = invocation.getArgument(0);
            saved.setId(PROFILE_ID);
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });

        WorkstationProfileResponse response = workstationProfileService.createFromSolution(
                SOLUTION_ID, TEAM_ID, USER_ID);

        assertThat(response.name()).isEqualTo("Solution: My Platform");
        assertThat(response.solutionId()).isEqualTo(SOLUTION_ID);
        assertThat(response.serviceIds()).containsExactlyInAnyOrder(SERVICE_ID_1, SERVICE_ID_2);
    }

    @Test
    void createFromSolution_solutionNotFound_throwsNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(solutionRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workstationProfileService.createFromSolution(unknownId, TEAM_ID, USER_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Solution");
    }

    // ──────────────────────────────────────────────────────────────
    // refreshStartupOrder tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void refreshStartupOrder_recomputesFromDependencyGraph() {
        WorkstationProfile profile = buildProfile(PROFILE_ID, "Stale Order",
                List.of(SERVICE_ID_1, SERVICE_ID_2),
                List.of(SERVICE_ID_1, SERVICE_ID_2), false);
        ServiceRegistration svc1 = buildService(SERVICE_ID_1, "Auth", "auth");
        ServiceRegistration svc2 = buildService(SERVICE_ID_2, "Gateway", "gateway");

        when(profileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile));
        // New dependency graph has reversed order
        when(dependencyGraphService.getStartupOrder(TEAM_ID))
                .thenReturn(buildStartupOrder(SERVICE_ID_2, SERVICE_ID_1));
        when(profileRepository.save(any(WorkstationProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(serviceRepository.findByTeamIdAndIdIn(eq(TEAM_ID), any())).thenReturn(List.of(svc1, svc2));

        WorkstationProfileResponse response = workstationProfileService.refreshStartupOrder(PROFILE_ID);

        assertThat(response.startupOrder()).containsExactly(SERVICE_ID_2, SERVICE_ID_1);
        verify(dependencyGraphService).getStartupOrder(TEAM_ID);
        verify(profileRepository).save(any(WorkstationProfile.class));
    }
}
