package com.codeops.registry.service;

import com.codeops.registry.config.AppConstants;
import com.codeops.registry.dto.request.AddSolutionMemberRequest;
import com.codeops.registry.dto.request.CreateSolutionRequest;
import com.codeops.registry.dto.request.UpdateSolutionMemberRequest;
import com.codeops.registry.dto.request.UpdateSolutionRequest;
import com.codeops.registry.dto.response.PageResponse;
import com.codeops.registry.dto.response.SolutionDetailResponse;
import com.codeops.registry.dto.response.SolutionHealthResponse;
import com.codeops.registry.dto.response.SolutionMemberResponse;
import com.codeops.registry.dto.response.SolutionResponse;
import com.codeops.registry.entity.ServiceRegistration;
import com.codeops.registry.entity.Solution;
import com.codeops.registry.entity.SolutionMember;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SolutionService}.
 *
 * <p>Tests cover CRUD operations, member management (add, update, remove, reorder),
 * aggregated health checks, and paginated filtering.</p>
 */
@ExtendWith(MockitoExtension.class)
class SolutionServiceTest {

    @Mock
    private SolutionRepository solutionRepository;

    @Mock
    private SolutionMemberRepository memberRepository;

    @Mock
    private ServiceRegistrationRepository serviceRepository;

    @InjectMocks
    private SolutionService solutionService;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SOLUTION_ID = UUID.randomUUID();
    private static final UUID SERVICE_ID_1 = UUID.randomUUID();
    private static final UUID SERVICE_ID_2 = UUID.randomUUID();
    private static final UUID SERVICE_ID_3 = UUID.randomUUID();
    private static final UUID MEMBER_ID_1 = UUID.randomUUID();
    private static final UUID MEMBER_ID_2 = UUID.randomUUID();
    private static final UUID MEMBER_ID_3 = UUID.randomUUID();

    // ──────────────────────────────────────────────────────────────
    // Helper methods
    // ──────────────────────────────────────────────────────────────

    private Solution buildSolution(UUID solutionId, String name, String slug) {
        Solution solution = Solution.builder()
                .teamId(TEAM_ID)
                .name(name)
                .slug(slug)
                .description("Test solution description")
                .category(SolutionCategory.PLATFORM)
                .status(SolutionStatus.ACTIVE)
                .createdByUserId(USER_ID)
                .build();
        solution.setId(solutionId);
        solution.setCreatedAt(Instant.now());
        solution.setUpdatedAt(Instant.now());
        return solution;
    }

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
        return service;
    }

    private ServiceRegistration buildServiceWithHealth(UUID serviceId, String name, String slug,
                                                       HealthStatus healthStatus) {
        ServiceRegistration service = buildService(serviceId, name, slug);
        service.setLastHealthStatus(healthStatus);
        service.setLastHealthCheckAt(Instant.now());
        service.setHealthCheckUrl("http://localhost:8080/actuator/health");
        return service;
    }

    private SolutionMember buildMember(UUID memberId, Solution solution, ServiceRegistration service,
                                       SolutionMemberRole role, int displayOrder) {
        SolutionMember member = SolutionMember.builder()
                .solution(solution)
                .service(service)
                .role(role)
                .displayOrder(displayOrder)
                .notes("Test notes")
                .build();
        member.setId(memberId);
        member.setCreatedAt(Instant.now());
        member.setUpdatedAt(Instant.now());
        return member;
    }

    // ──────────────────────────────────────────────────────────────
    // createSolution tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void createSolution_success() {
        CreateSolutionRequest request = new CreateSolutionRequest(
                TEAM_ID, "My Platform", null, "A platform", SolutionCategory.PLATFORM,
                "icon-platform", "#FF0000", USER_ID, "https://github.com/repo",
                "https://docs.example.com", null);

        when(solutionRepository.countByTeamId(TEAM_ID)).thenReturn(0L);
        when(solutionRepository.existsByTeamIdAndSlug(eq(TEAM_ID), anyString())).thenReturn(false);
        when(solutionRepository.save(any(Solution.class))).thenAnswer(invocation -> {
            Solution saved = invocation.getArgument(0);
            saved.setId(SOLUTION_ID);
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });
        when(memberRepository.countBySolutionId(SOLUTION_ID)).thenReturn(0L);

        SolutionResponse response = solutionService.createSolution(request, USER_ID);

        assertThat(response.id()).isEqualTo(SOLUTION_ID);
        assertThat(response.teamId()).isEqualTo(TEAM_ID);
        assertThat(response.name()).isEqualTo("My Platform");
        assertThat(response.slug()).isEqualTo("my-platform");
        assertThat(response.category()).isEqualTo(SolutionCategory.PLATFORM);
        assertThat(response.createdByUserId()).isEqualTo(USER_ID);
        assertThat(response.memberCount()).isZero();
        verify(solutionRepository).save(any(Solution.class));
    }

    @Test
    void createSolution_withCustomSlug() {
        CreateSolutionRequest request = new CreateSolutionRequest(
                TEAM_ID, "My Platform", "custom-slug", "A platform", SolutionCategory.PLATFORM,
                null, null, null, null, null, null);

        when(solutionRepository.countByTeamId(TEAM_ID)).thenReturn(0L);
        when(solutionRepository.existsByTeamIdAndSlug(eq(TEAM_ID), anyString())).thenReturn(false);
        when(solutionRepository.save(any(Solution.class))).thenAnswer(invocation -> {
            Solution saved = invocation.getArgument(0);
            saved.setId(SOLUTION_ID);
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });
        when(memberRepository.countBySolutionId(SOLUTION_ID)).thenReturn(0L);

        SolutionResponse response = solutionService.createSolution(request, USER_ID);

        assertThat(response.slug()).isEqualTo("custom-slug");
        verify(solutionRepository).save(any(Solution.class));
    }

    @Test
    void createSolution_teamLimitExceeded() {
        CreateSolutionRequest request = new CreateSolutionRequest(
                TEAM_ID, "Overflow Solution", null, null, SolutionCategory.APPLICATION,
                null, null, null, null, null, null);

        when(solutionRepository.countByTeamId(TEAM_ID)).thenReturn((long) AppConstants.MAX_SOLUTIONS_PER_TEAM);

        assertThatThrownBy(() -> solutionService.createSolution(request, USER_ID))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("maximum of " + AppConstants.MAX_SOLUTIONS_PER_TEAM);

        verify(solutionRepository, never()).save(any(Solution.class));
    }

    @Test
    void createSolution_slugCollision() {
        CreateSolutionRequest request = new CreateSolutionRequest(
                TEAM_ID, "My Platform", null, null, SolutionCategory.PLATFORM,
                null, null, null, null, null, null);

        when(solutionRepository.countByTeamId(TEAM_ID)).thenReturn(0L);
        // First call for "my-platform" returns true (collision), second for "my-platform-2" returns false
        when(solutionRepository.existsByTeamIdAndSlug(TEAM_ID, "my-platform")).thenReturn(true);
        when(solutionRepository.existsByTeamIdAndSlug(TEAM_ID, "my-platform-2")).thenReturn(false);
        when(solutionRepository.save(any(Solution.class))).thenAnswer(invocation -> {
            Solution saved = invocation.getArgument(0);
            saved.setId(SOLUTION_ID);
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });
        when(memberRepository.countBySolutionId(SOLUTION_ID)).thenReturn(0L);

        SolutionResponse response = solutionService.createSolution(request, USER_ID);

        assertThat(response.slug()).isEqualTo("my-platform-2");
    }

    // ──────────────────────────────────────────────────────────────
    // getSolution tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void getSolution_found() {
        Solution solution = buildSolution(SOLUTION_ID, "My Platform", "my-platform");

        when(solutionRepository.findById(SOLUTION_ID)).thenReturn(Optional.of(solution));
        when(memberRepository.countBySolutionId(SOLUTION_ID)).thenReturn(3L);

        SolutionResponse response = solutionService.getSolution(SOLUTION_ID);

        assertThat(response.id()).isEqualTo(SOLUTION_ID);
        assertThat(response.name()).isEqualTo("My Platform");
        assertThat(response.slug()).isEqualTo("my-platform");
        assertThat(response.memberCount()).isEqualTo(3);
    }

    @Test
    void getSolution_notFound() {
        UUID unknownId = UUID.randomUUID();
        when(solutionRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> solutionService.getSolution(unknownId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Solution");
    }

    // ──────────────────────────────────────────────────────────────
    // getSolutionBySlug tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void getSolutionBySlug_found() {
        Solution solution = buildSolution(SOLUTION_ID, "My Platform", "my-platform");

        when(solutionRepository.findByTeamIdAndSlug(TEAM_ID, "my-platform")).thenReturn(Optional.of(solution));
        when(memberRepository.countBySolutionId(SOLUTION_ID)).thenReturn(2L);

        SolutionResponse response = solutionService.getSolutionBySlug(TEAM_ID, "my-platform");

        assertThat(response.id()).isEqualTo(SOLUTION_ID);
        assertThat(response.slug()).isEqualTo("my-platform");
        assertThat(response.memberCount()).isEqualTo(2);
    }

    @Test
    void getSolutionBySlug_notFound() {
        when(solutionRepository.findByTeamIdAndSlug(TEAM_ID, "nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> solutionService.getSolutionBySlug(TEAM_ID, "nonexistent"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Solution");
    }

    // ──────────────────────────────────────────────────────────────
    // getSolutionDetail tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void getSolutionDetail_found() {
        Solution solution = buildSolution(SOLUTION_ID, "My Platform", "my-platform");
        ServiceRegistration svc1 = buildService(SERVICE_ID_1, "Auth Service", "auth-service");
        ServiceRegistration svc2 = buildService(SERVICE_ID_2, "API Gateway", "api-gateway");

        SolutionMember member1 = buildMember(MEMBER_ID_1, solution, svc1, SolutionMemberRole.CORE, 0);
        SolutionMember member2 = buildMember(MEMBER_ID_2, solution, svc2, SolutionMemberRole.SUPPORTING, 1);

        when(solutionRepository.findById(SOLUTION_ID)).thenReturn(Optional.of(solution));
        when(memberRepository.findBySolutionIdOrderByDisplayOrderAsc(SOLUTION_ID))
                .thenReturn(List.of(member1, member2));

        SolutionDetailResponse response = solutionService.getSolutionDetail(SOLUTION_ID);

        assertThat(response.id()).isEqualTo(SOLUTION_ID);
        assertThat(response.name()).isEqualTo("My Platform");
        assertThat(response.members()).hasSize(2);
        assertThat(response.members().get(0).serviceName()).isEqualTo("Auth Service");
        assertThat(response.members().get(0).role()).isEqualTo(SolutionMemberRole.CORE);
        assertThat(response.members().get(1).serviceName()).isEqualTo("API Gateway");
        assertThat(response.members().get(1).role()).isEqualTo(SolutionMemberRole.SUPPORTING);
    }

    @Test
    void getSolutionDetail_notFound() {
        UUID unknownId = UUID.randomUUID();
        when(solutionRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> solutionService.getSolutionDetail(unknownId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Solution");
    }

    // ──────────────────────────────────────────────────────────────
    // getSolutionsForTeam tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void getSolutionsForTeam_noFilters() {
        Solution s1 = buildSolution(UUID.randomUUID(), "Platform A", "platform-a");
        Solution s2 = buildSolution(UUID.randomUUID(), "Platform B", "platform-b");
        Pageable pageable = PageRequest.of(0, 20);
        Page<Solution> page = new PageImpl<>(List.of(s1, s2), pageable, 2);

        when(solutionRepository.findByTeamId(TEAM_ID, pageable)).thenReturn(page);

        PageResponse<SolutionResponse> response = solutionService.getSolutionsForTeam(
                TEAM_ID, null, null, pageable);

        assertThat(response.content()).hasSize(2);
        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.page()).isZero();
        verify(solutionRepository).findByTeamId(TEAM_ID, pageable);
    }

    @Test
    void getSolutionsForTeam_filterByStatus() {
        Solution s1 = buildSolution(UUID.randomUUID(), "Active Solution", "active-solution");
        Pageable pageable = PageRequest.of(0, 20);
        Page<Solution> page = new PageImpl<>(List.of(s1), pageable, 1);

        when(solutionRepository.findByTeamIdAndStatus(TEAM_ID, SolutionStatus.ACTIVE, pageable))
                .thenReturn(page);

        PageResponse<SolutionResponse> response = solutionService.getSolutionsForTeam(
                TEAM_ID, SolutionStatus.ACTIVE, null, pageable);

        assertThat(response.content()).hasSize(1);
        verify(solutionRepository).findByTeamIdAndStatus(TEAM_ID, SolutionStatus.ACTIVE, pageable);
        verify(solutionRepository, never()).findByTeamId(any(UUID.class), any(Pageable.class));
    }

    @Test
    void getSolutionsForTeam_filterByCategory() {
        Solution s1 = buildSolution(UUID.randomUUID(), "Infra Solution", "infra-solution");
        s1.setCategory(SolutionCategory.INFRASTRUCTURE);
        Pageable pageable = PageRequest.of(0, 20);
        Page<Solution> page = new PageImpl<>(List.of(s1), pageable, 1);

        when(solutionRepository.findByTeamIdAndCategory(TEAM_ID, SolutionCategory.INFRASTRUCTURE, pageable))
                .thenReturn(page);

        PageResponse<SolutionResponse> response = solutionService.getSolutionsForTeam(
                TEAM_ID, null, SolutionCategory.INFRASTRUCTURE, pageable);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).category()).isEqualTo(SolutionCategory.INFRASTRUCTURE);
        verify(solutionRepository).findByTeamIdAndCategory(TEAM_ID, SolutionCategory.INFRASTRUCTURE, pageable);
    }

    // ──────────────────────────────────────────────────────────────
    // updateSolution tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void updateSolution_success() {
        Solution solution = buildSolution(SOLUTION_ID, "Old Name", "old-name");

        UpdateSolutionRequest request = new UpdateSolutionRequest(
                "New Name", "Updated description", SolutionCategory.APPLICATION, null,
                null, null, null, null, null, null);

        when(solutionRepository.findById(SOLUTION_ID)).thenReturn(Optional.of(solution));
        when(solutionRepository.save(any(Solution.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(memberRepository.countBySolutionId(SOLUTION_ID)).thenReturn(0L);

        SolutionResponse response = solutionService.updateSolution(SOLUTION_ID, request);

        assertThat(response.name()).isEqualTo("New Name");
        assertThat(response.description()).isEqualTo("Updated description");
        assertThat(response.category()).isEqualTo(SolutionCategory.APPLICATION);
        // status should remain unchanged since request.status() was null
        assertThat(response.status()).isEqualTo(SolutionStatus.ACTIVE);
        verify(solutionRepository).save(any(Solution.class));
    }

    @Test
    void updateSolution_notFound() {
        UUID unknownId = UUID.randomUUID();
        UpdateSolutionRequest request = new UpdateSolutionRequest(
                "New Name", null, null, null, null, null, null, null, null, null);

        when(solutionRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> solutionService.updateSolution(unknownId, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Solution");
    }

    // ──────────────────────────────────────────────────────────────
    // deleteSolution tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void deleteSolution_success() {
        Solution solution = buildSolution(SOLUTION_ID, "Doomed Solution", "doomed-solution");

        when(solutionRepository.findById(SOLUTION_ID)).thenReturn(Optional.of(solution));

        solutionService.deleteSolution(SOLUTION_ID);

        verify(solutionRepository).delete(solution);
    }

    @Test
    void deleteSolution_notFound() {
        UUID unknownId = UUID.randomUUID();
        when(solutionRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> solutionService.deleteSolution(unknownId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Solution");

        verify(solutionRepository, never()).delete(any(Solution.class));
    }

    // ──────────────────────────────────────────────────────────────
    // addMember tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void addMember_success() {
        Solution solution = buildSolution(SOLUTION_ID, "My Platform", "my-platform");
        ServiceRegistration service = buildService(SERVICE_ID_1, "Auth Service", "auth-service");

        AddSolutionMemberRequest request = new AddSolutionMemberRequest(
                SERVICE_ID_1, SolutionMemberRole.CORE, 0, "Primary auth service");

        when(solutionRepository.findById(SOLUTION_ID)).thenReturn(Optional.of(solution));
        when(serviceRepository.findById(SERVICE_ID_1)).thenReturn(Optional.of(service));
        when(memberRepository.existsBySolutionIdAndServiceId(SOLUTION_ID, SERVICE_ID_1)).thenReturn(false);
        when(memberRepository.save(any(SolutionMember.class))).thenAnswer(invocation -> {
            SolutionMember saved = invocation.getArgument(0);
            saved.setId(MEMBER_ID_1);
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });

        SolutionMemberResponse response = solutionService.addMember(SOLUTION_ID, request);

        assertThat(response.id()).isEqualTo(MEMBER_ID_1);
        assertThat(response.solutionId()).isEqualTo(SOLUTION_ID);
        assertThat(response.serviceId()).isEqualTo(SERVICE_ID_1);
        assertThat(response.serviceName()).isEqualTo("Auth Service");
        assertThat(response.role()).isEqualTo(SolutionMemberRole.CORE);
        assertThat(response.displayOrder()).isZero();
        assertThat(response.notes()).isEqualTo("Primary auth service");
        verify(memberRepository).save(any(SolutionMember.class));
    }

    @Test
    void addMember_solutionNotFound() {
        UUID unknownSolutionId = UUID.randomUUID();
        AddSolutionMemberRequest request = new AddSolutionMemberRequest(
                SERVICE_ID_1, SolutionMemberRole.CORE, null, null);

        when(solutionRepository.findById(unknownSolutionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> solutionService.addMember(unknownSolutionId, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Solution");

        verify(memberRepository, never()).save(any(SolutionMember.class));
    }

    @Test
    void addMember_serviceNotFound() {
        Solution solution = buildSolution(SOLUTION_ID, "My Platform", "my-platform");
        UUID unknownServiceId = UUID.randomUUID();

        AddSolutionMemberRequest request = new AddSolutionMemberRequest(
                unknownServiceId, SolutionMemberRole.SUPPORTING, null, null);

        when(solutionRepository.findById(SOLUTION_ID)).thenReturn(Optional.of(solution));
        when(serviceRepository.findById(unknownServiceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> solutionService.addMember(SOLUTION_ID, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("ServiceRegistration");

        verify(memberRepository, never()).save(any(SolutionMember.class));
    }

    @Test
    void addMember_alreadyMember() {
        Solution solution = buildSolution(SOLUTION_ID, "My Platform", "my-platform");
        ServiceRegistration service = buildService(SERVICE_ID_1, "Auth Service", "auth-service");

        AddSolutionMemberRequest request = new AddSolutionMemberRequest(
                SERVICE_ID_1, SolutionMemberRole.CORE, null, null);

        when(solutionRepository.findById(SOLUTION_ID)).thenReturn(Optional.of(solution));
        when(serviceRepository.findById(SERVICE_ID_1)).thenReturn(Optional.of(service));
        when(memberRepository.existsBySolutionIdAndServiceId(SOLUTION_ID, SERVICE_ID_1)).thenReturn(true);

        assertThatThrownBy(() -> solutionService.addMember(SOLUTION_ID, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("already a member");

        verify(memberRepository, never()).save(any(SolutionMember.class));
    }

    // ──────────────────────────────────────────────────────────────
    // updateMember tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void updateMember_success() {
        Solution solution = buildSolution(SOLUTION_ID, "My Platform", "my-platform");
        ServiceRegistration service = buildService(SERVICE_ID_1, "Auth Service", "auth-service");
        SolutionMember member = buildMember(MEMBER_ID_1, solution, service, SolutionMemberRole.CORE, 0);

        UpdateSolutionMemberRequest request = new UpdateSolutionMemberRequest(
                SolutionMemberRole.SUPPORTING, 5, "Updated notes");

        when(memberRepository.findBySolutionIdAndServiceId(SOLUTION_ID, SERVICE_ID_1))
                .thenReturn(Optional.of(member));
        when(memberRepository.save(any(SolutionMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SolutionMemberResponse response = solutionService.updateMember(SOLUTION_ID, SERVICE_ID_1, request);

        assertThat(response.role()).isEqualTo(SolutionMemberRole.SUPPORTING);
        assertThat(response.displayOrder()).isEqualTo(5);
        assertThat(response.notes()).isEqualTo("Updated notes");
        verify(memberRepository).save(any(SolutionMember.class));
    }

    @Test
    void updateMember_notFound() {
        UUID unknownServiceId = UUID.randomUUID();
        UpdateSolutionMemberRequest request = new UpdateSolutionMemberRequest(
                SolutionMemberRole.INFRASTRUCTURE, null, null);

        when(memberRepository.findBySolutionIdAndServiceId(SOLUTION_ID, unknownServiceId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> solutionService.updateMember(SOLUTION_ID, unknownServiceId, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("SolutionMember");
    }

    // ──────────────────────────────────────────────────────────────
    // removeMember tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void removeMember_success() {
        when(memberRepository.existsBySolutionIdAndServiceId(SOLUTION_ID, SERVICE_ID_1)).thenReturn(true);

        solutionService.removeMember(SOLUTION_ID, SERVICE_ID_1);

        verify(memberRepository).deleteBySolutionIdAndServiceId(SOLUTION_ID, SERVICE_ID_1);
    }

    @Test
    void removeMember_notFound() {
        UUID unknownServiceId = UUID.randomUUID();
        when(memberRepository.existsBySolutionIdAndServiceId(SOLUTION_ID, unknownServiceId)).thenReturn(false);

        assertThatThrownBy(() -> solutionService.removeMember(SOLUTION_ID, unknownServiceId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("SolutionMember");

        verify(memberRepository, never()).deleteBySolutionIdAndServiceId(any(UUID.class), any(UUID.class));
    }

    // ──────────────────────────────────────────────────────────────
    // reorderMembers tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void reorderMembers_success() {
        Solution solution = buildSolution(SOLUTION_ID, "My Platform", "my-platform");
        ServiceRegistration svc1 = buildService(SERVICE_ID_1, "Service A", "service-a");
        ServiceRegistration svc2 = buildService(SERVICE_ID_2, "Service B", "service-b");
        ServiceRegistration svc3 = buildService(SERVICE_ID_3, "Service C", "service-c");

        SolutionMember m1 = buildMember(MEMBER_ID_1, solution, svc1, SolutionMemberRole.CORE, 0);
        SolutionMember m2 = buildMember(MEMBER_ID_2, solution, svc2, SolutionMemberRole.SUPPORTING, 1);
        SolutionMember m3 = buildMember(MEMBER_ID_3, solution, svc3, SolutionMemberRole.INFRASTRUCTURE, 2);

        // Reorder: C, A, B
        List<UUID> newOrder = List.of(SERVICE_ID_3, SERVICE_ID_1, SERVICE_ID_2);

        when(solutionRepository.existsById(SOLUTION_ID)).thenReturn(true);
        when(memberRepository.findBySolutionId(SOLUTION_ID)).thenReturn(List.of(m1, m2, m3));
        when(memberRepository.findBySolutionIdOrderByDisplayOrderAsc(SOLUTION_ID))
                .thenReturn(List.of(m3, m1, m2));

        List<SolutionMemberResponse> responses = solutionService.reorderMembers(SOLUTION_ID, newOrder);

        verify(memberRepository).saveAll(List.of(m1, m2, m3));
        assertThat(m3.getDisplayOrder()).isZero();
        assertThat(m1.getDisplayOrder()).isEqualTo(1);
        assertThat(m2.getDisplayOrder()).isEqualTo(2);
        assertThat(responses).hasSize(3);
    }

    @Test
    void reorderMembers_solutionNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(solutionRepository.existsById(unknownId)).thenReturn(false);

        assertThatThrownBy(() -> solutionService.reorderMembers(unknownId, List.of(SERVICE_ID_1)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Solution");
    }

    @Test
    void reorderMembers_invalidServiceId() {
        UUID invalidServiceId = UUID.randomUUID();
        Solution solution = buildSolution(SOLUTION_ID, "My Platform", "my-platform");
        ServiceRegistration svc1 = buildService(SERVICE_ID_1, "Service A", "service-a");
        SolutionMember m1 = buildMember(MEMBER_ID_1, solution, svc1, SolutionMemberRole.CORE, 0);

        when(solutionRepository.existsById(SOLUTION_ID)).thenReturn(true);
        when(memberRepository.findBySolutionId(SOLUTION_ID)).thenReturn(List.of(m1));

        assertThatThrownBy(() -> solutionService.reorderMembers(SOLUTION_ID, List.of(invalidServiceId)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("is not a member of solution");
    }

    // ──────────────────────────────────────────────────────────────
    // getSolutionHealth tests
    // ──────────────────────────────────────────────────────────────

    @Test
    void getSolutionHealth_allUp() {
        Solution solution = buildSolution(SOLUTION_ID, "Healthy Platform", "healthy-platform");
        ServiceRegistration svc1 = buildServiceWithHealth(SERVICE_ID_1, "Svc1", "svc1", HealthStatus.UP);
        ServiceRegistration svc2 = buildServiceWithHealth(SERVICE_ID_2, "Svc2", "svc2", HealthStatus.UP);

        SolutionMember m1 = buildMember(MEMBER_ID_1, solution, svc1, SolutionMemberRole.CORE, 0);
        SolutionMember m2 = buildMember(MEMBER_ID_2, solution, svc2, SolutionMemberRole.SUPPORTING, 1);

        when(solutionRepository.findById(SOLUTION_ID)).thenReturn(Optional.of(solution));
        when(memberRepository.findBySolutionId(SOLUTION_ID)).thenReturn(List.of(m1, m2));

        SolutionHealthResponse response = solutionService.getSolutionHealth(SOLUTION_ID);

        assertThat(response.solutionId()).isEqualTo(SOLUTION_ID);
        assertThat(response.solutionName()).isEqualTo("Healthy Platform");
        assertThat(response.totalServices()).isEqualTo(2);
        assertThat(response.servicesUp()).isEqualTo(2);
        assertThat(response.servicesDown()).isZero();
        assertThat(response.servicesDegraded()).isZero();
        assertThat(response.servicesUnknown()).isZero();
        assertThat(response.aggregatedHealth()).isEqualTo(HealthStatus.UP);
        assertThat(response.serviceHealths()).hasSize(2);
    }

    @Test
    void getSolutionHealth_oneDown() {
        Solution solution = buildSolution(SOLUTION_ID, "Partial Platform", "partial-platform");
        ServiceRegistration svc1 = buildServiceWithHealth(SERVICE_ID_1, "Svc1", "svc1", HealthStatus.UP);
        ServiceRegistration svc2 = buildServiceWithHealth(SERVICE_ID_2, "Svc2", "svc2", HealthStatus.DOWN);

        SolutionMember m1 = buildMember(MEMBER_ID_1, solution, svc1, SolutionMemberRole.CORE, 0);
        SolutionMember m2 = buildMember(MEMBER_ID_2, solution, svc2, SolutionMemberRole.SUPPORTING, 1);

        when(solutionRepository.findById(SOLUTION_ID)).thenReturn(Optional.of(solution));
        when(memberRepository.findBySolutionId(SOLUTION_ID)).thenReturn(List.of(m1, m2));

        SolutionHealthResponse response = solutionService.getSolutionHealth(SOLUTION_ID);

        assertThat(response.servicesUp()).isEqualTo(1);
        assertThat(response.servicesDown()).isEqualTo(1);
        assertThat(response.aggregatedHealth()).isEqualTo(HealthStatus.DOWN);
    }

    @Test
    void getSolutionHealth_oneDegraded() {
        Solution solution = buildSolution(SOLUTION_ID, "Degraded Platform", "degraded-platform");
        ServiceRegistration svc1 = buildServiceWithHealth(SERVICE_ID_1, "Svc1", "svc1", HealthStatus.UP);
        ServiceRegistration svc2 = buildServiceWithHealth(SERVICE_ID_2, "Svc2", "svc2", HealthStatus.DEGRADED);

        SolutionMember m1 = buildMember(MEMBER_ID_1, solution, svc1, SolutionMemberRole.CORE, 0);
        SolutionMember m2 = buildMember(MEMBER_ID_2, solution, svc2, SolutionMemberRole.SUPPORTING, 1);

        when(solutionRepository.findById(SOLUTION_ID)).thenReturn(Optional.of(solution));
        when(memberRepository.findBySolutionId(SOLUTION_ID)).thenReturn(List.of(m1, m2));

        SolutionHealthResponse response = solutionService.getSolutionHealth(SOLUTION_ID);

        assertThat(response.servicesUp()).isEqualTo(1);
        assertThat(response.servicesDegraded()).isEqualTo(1);
        assertThat(response.aggregatedHealth()).isEqualTo(HealthStatus.DEGRADED);
    }

    @Test
    void getSolutionHealth_noMembers() {
        Solution solution = buildSolution(SOLUTION_ID, "Empty Platform", "empty-platform");

        when(solutionRepository.findById(SOLUTION_ID)).thenReturn(Optional.of(solution));
        when(memberRepository.findBySolutionId(SOLUTION_ID)).thenReturn(Collections.emptyList());

        SolutionHealthResponse response = solutionService.getSolutionHealth(SOLUTION_ID);

        assertThat(response.totalServices()).isZero();
        assertThat(response.servicesUp()).isZero();
        assertThat(response.servicesDown()).isZero();
        assertThat(response.servicesDegraded()).isZero();
        assertThat(response.servicesUnknown()).isZero();
        assertThat(response.aggregatedHealth()).isEqualTo(HealthStatus.UNKNOWN);
        assertThat(response.serviceHealths()).isEmpty();
    }
}
