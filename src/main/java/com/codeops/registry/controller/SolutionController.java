package com.codeops.registry.controller;

import com.codeops.registry.dto.request.AddSolutionMemberRequest;
import com.codeops.registry.dto.request.CreateSolutionRequest;
import com.codeops.registry.dto.request.UpdateSolutionMemberRequest;
import com.codeops.registry.dto.request.UpdateSolutionRequest;
import com.codeops.registry.dto.response.PageResponse;
import com.codeops.registry.dto.response.SolutionDetailResponse;
import com.codeops.registry.dto.response.SolutionHealthResponse;
import com.codeops.registry.dto.response.SolutionMemberResponse;
import com.codeops.registry.dto.response.SolutionResponse;
import com.codeops.registry.entity.enums.SolutionCategory;
import com.codeops.registry.entity.enums.SolutionStatus;
import com.codeops.registry.security.SecurityUtils;
import com.codeops.registry.service.SolutionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for solution CRUD, member management, and aggregated health.
 *
 * <p>All endpoints require JWT authentication. Write operations require the {@code ADMIN}
 * role or the {@code registry:write} authority; read operations require the {@code ADMIN}
 * role or the {@code registry:read} authority.</p>
 *
 * @see SolutionService
 */
@RestController
@RequestMapping("/api/v1/registry")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Solutions")
public class SolutionController {

    private final SolutionService solutionService;

    /**
     * Creates a new solution for a team.
     *
     * @param teamId  the team ID (path segment for REST structure)
     * @param request the solution creation request (contains teamId, name, category, etc.)
     * @return a 201 response with the created solution
     */
    @PostMapping("/teams/{teamId}/solutions")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:write')")
    public ResponseEntity<SolutionResponse> createSolution(
            @PathVariable UUID teamId,
            @Valid @RequestBody CreateSolutionRequest request) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        SolutionResponse response = solutionService.createSolution(request, currentUserId);
        return ResponseEntity.status(201).body(response);
    }

    /**
     * Lists solutions for a team with optional filtering by status and category.
     *
     * @param teamId   the team ID
     * @param status   optional solution status filter
     * @param category optional solution category filter
     * @param pageable pagination parameters (default size 20)
     * @return a 200 response with the paginated solution list
     */
    @GetMapping("/teams/{teamId}/solutions")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:read')")
    public ResponseEntity<PageResponse<SolutionResponse>> getSolutionsForTeam(
            @PathVariable UUID teamId,
            @RequestParam(required = false) SolutionStatus status,
            @RequestParam(required = false) SolutionCategory category,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(solutionService.getSolutionsForTeam(teamId, status, category, pageable));
    }

    /**
     * Retrieves a single solution by ID with computed member count.
     *
     * @param solutionId the solution ID
     * @return a 200 response with the solution
     */
    @GetMapping("/solutions/{solutionId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:read')")
    public ResponseEntity<SolutionResponse> getSolution(@PathVariable UUID solutionId) {
        return ResponseEntity.ok(solutionService.getSolution(solutionId));
    }

    /**
     * Updates a solution with non-null fields from the request.
     *
     * @param solutionId the solution ID
     * @param request    the update request with optional fields
     * @return a 200 response with the updated solution
     */
    @PutMapping("/solutions/{solutionId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:write')")
    public ResponseEntity<SolutionResponse> updateSolution(
            @PathVariable UUID solutionId,
            @Valid @RequestBody UpdateSolutionRequest request) {
        return ResponseEntity.ok(solutionService.updateSolution(solutionId, request));
    }

    /**
     * Deletes a solution and its member associations.
     *
     * @param solutionId the solution ID
     * @return a 204 response with no content
     */
    @DeleteMapping("/solutions/{solutionId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:write')")
    public ResponseEntity<Void> deleteSolution(@PathVariable UUID solutionId) {
        solutionService.deleteSolution(solutionId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Retrieves full solution detail including the member list.
     *
     * @param solutionId the solution ID
     * @return a 200 response with the solution detail
     */
    @GetMapping("/solutions/{solutionId}/detail")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:read')")
    public ResponseEntity<SolutionDetailResponse> getSolutionDetail(@PathVariable UUID solutionId) {
        return ResponseEntity.ok(solutionService.getSolutionDetail(solutionId));
    }

    /**
     * Adds a service as a member of a solution.
     *
     * @param solutionId the solution ID
     * @param request    the add member request (contains serviceId, role, etc.)
     * @return a 201 response with the created solution member
     */
    @PostMapping("/solutions/{solutionId}/members")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:write')")
    public ResponseEntity<SolutionMemberResponse> addSolutionMember(
            @PathVariable UUID solutionId,
            @Valid @RequestBody AddSolutionMemberRequest request) {
        SolutionMemberResponse response = solutionService.addMember(solutionId, request);
        return ResponseEntity.status(201).body(response);
    }

    /**
     * Updates a solution member's role, display order, or notes.
     *
     * @param solutionId the solution ID
     * @param serviceId  the service ID
     * @param request    the update request with optional fields
     * @return a 200 response with the updated solution member
     */
    @PutMapping("/solutions/{solutionId}/members/{serviceId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:write')")
    public ResponseEntity<SolutionMemberResponse> updateSolutionMember(
            @PathVariable UUID solutionId,
            @PathVariable UUID serviceId,
            @Valid @RequestBody UpdateSolutionMemberRequest request) {
        return ResponseEntity.ok(solutionService.updateMember(solutionId, serviceId, request));
    }

    /**
     * Removes a service from a solution.
     *
     * @param solutionId the solution ID
     * @param serviceId  the service ID
     * @return a 204 response with no content
     */
    @DeleteMapping("/solutions/{solutionId}/members/{serviceId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:write')")
    public ResponseEntity<Void> removeSolutionMember(
            @PathVariable UUID solutionId,
            @PathVariable UUID serviceId) {
        solutionService.removeMember(solutionId, serviceId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Reorders members within a solution by assigning sequential display orders.
     *
     * @param solutionId       the solution ID
     * @param orderedServiceIds the service IDs in desired order
     * @return a 200 response with the reordered member list
     */
    @PutMapping("/solutions/{solutionId}/members/reorder")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:write')")
    public ResponseEntity<List<SolutionMemberResponse>> reorderSolutionMembers(
            @PathVariable UUID solutionId,
            @RequestBody List<UUID> orderedServiceIds) {
        return ResponseEntity.ok(solutionService.reorderMembers(solutionId, orderedServiceIds));
    }

    /**
     * Returns aggregated health status across all services in a solution.
     *
     * @param solutionId the solution ID
     * @return a 200 response with the aggregated health
     */
    @GetMapping("/solutions/{solutionId}/health")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('registry:read')")
    public ResponseEntity<SolutionHealthResponse> getSolutionHealth(@PathVariable UUID solutionId) {
        return ResponseEntity.ok(solutionService.getSolutionHealth(solutionId));
    }
}
