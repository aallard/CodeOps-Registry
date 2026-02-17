package com.codeops.registry.controller;

import com.codeops.registry.dto.request.CreateWorkstationProfileRequest;
import com.codeops.registry.dto.request.UpdateWorkstationProfileRequest;
import com.codeops.registry.dto.response.WorkstationProfileResponse;
import com.codeops.registry.security.SecurityUtils;
import com.codeops.registry.service.WorkstationProfileService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for workstation profile management.
 *
 * <p>Provides CRUD operations for workstation profiles, default profile management,
 * solution-based profile creation, and startup order refresh. All endpoints require
 * JWT authentication. Write operations require the {@code ARCHITECT} role or the
 * {@code registry:write} authority; read operations require the {@code ARCHITECT}
 * role or the {@code registry:read} authority; delete operations require the
 * {@code ARCHITECT} role or the {@code registry:delete} authority.</p>
 *
 * @see WorkstationProfileService
 */
@RestController
@RequestMapping("/api/v1/registry")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Profiles")
public class WorkstationController {

    private final WorkstationProfileService workstationProfileService;

    /**
     * Creates a new workstation profile for a team.
     *
     * @param teamId  the team ID (path variable for URL structure)
     * @param request the creation request (teamId, name, serviceIds or solutionId)
     * @return a 201 response with the created profile
     */
    @PostMapping("/teams/{teamId}/workstations")
    @PreAuthorize("hasRole('ARCHITECT') or hasAuthority('registry:write')")
    public ResponseEntity<WorkstationProfileResponse> createProfile(
            @PathVariable UUID teamId,
            @Valid @RequestBody CreateWorkstationProfileRequest request) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workstationProfileService.createProfile(request, userId));
    }

    /**
     * Lists all workstation profiles for a team.
     *
     * @param teamId the team ID
     * @return a 200 response with the list of profiles
     */
    @GetMapping("/teams/{teamId}/workstations")
    @PreAuthorize("hasRole('ARCHITECT') or hasAuthority('registry:read')")
    public ResponseEntity<List<WorkstationProfileResponse>> getProfilesForTeam(
            @PathVariable UUID teamId) {
        return ResponseEntity.ok(workstationProfileService.getProfilesForTeam(teamId));
    }

    /**
     * Retrieves a single workstation profile with enriched service details.
     *
     * @param profileId the profile ID
     * @return a 200 response with the profile
     */
    @GetMapping("/workstations/{profileId}")
    @PreAuthorize("hasRole('ARCHITECT') or hasAuthority('registry:read')")
    public ResponseEntity<WorkstationProfileResponse> getProfile(
            @PathVariable UUID profileId) {
        return ResponseEntity.ok(workstationProfileService.getProfile(profileId));
    }

    /**
     * Partially updates a workstation profile.
     *
     * @param profileId the profile ID
     * @param request   the update request with optional fields
     * @return a 200 response with the updated profile
     */
    @PutMapping("/workstations/{profileId}")
    @PreAuthorize("hasRole('ARCHITECT') or hasAuthority('registry:write')")
    public ResponseEntity<WorkstationProfileResponse> updateProfile(
            @PathVariable UUID profileId,
            @Valid @RequestBody UpdateWorkstationProfileRequest request) {
        return ResponseEntity.ok(workstationProfileService.updateProfile(profileId, request));
    }

    /**
     * Deletes a workstation profile.
     *
     * @param profileId the profile ID
     * @return a 204 response with no content
     */
    @DeleteMapping("/workstations/{profileId}")
    @PreAuthorize("hasRole('ARCHITECT') or hasAuthority('registry:delete')")
    public ResponseEntity<Void> deleteProfile(@PathVariable UUID profileId) {
        workstationProfileService.deleteProfile(profileId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Retrieves the team's default workstation profile.
     *
     * @param teamId the team ID
     * @return a 200 response with the default profile
     */
    @GetMapping("/teams/{teamId}/workstations/default")
    @PreAuthorize("hasRole('ARCHITECT') or hasAuthority('registry:read')")
    public ResponseEntity<WorkstationProfileResponse> getDefaultProfile(
            @PathVariable UUID teamId) {
        return ResponseEntity.ok(workstationProfileService.getDefaultProfile(teamId));
    }

    /**
     * Sets a workstation profile as the team's default.
     *
     * @param profileId the profile ID
     * @return a 200 response with the updated profile
     */
    @PatchMapping("/workstations/{profileId}/set-default")
    @PreAuthorize("hasRole('ARCHITECT') or hasAuthority('registry:write')")
    public ResponseEntity<WorkstationProfileResponse> setDefault(
            @PathVariable UUID profileId) {
        return ResponseEntity.ok(workstationProfileService.setDefault(profileId));
    }

    /**
     * Quick-creates a workstation profile from a solution's member services.
     *
     * @param solutionId the solution ID
     * @param teamId     the team ID (required query parameter)
     * @return a 201 response with the created profile
     */
    @PostMapping("/solutions/{solutionId}/workstations/from-solution")
    @PreAuthorize("hasRole('ARCHITECT') or hasAuthority('registry:write')")
    public ResponseEntity<WorkstationProfileResponse> createFromSolution(
            @PathVariable UUID solutionId,
            @RequestParam UUID teamId) {
        UUID userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workstationProfileService.createFromSolution(solutionId, teamId, userId));
    }

    /**
     * Recomputes the startup order for a profile based on the current dependency graph.
     *
     * @param profileId the profile ID
     * @return a 200 response with the updated profile
     */
    @PostMapping("/workstations/{profileId}/refresh-startup-order")
    @PreAuthorize("hasRole('ARCHITECT') or hasAuthority('registry:write')")
    public ResponseEntity<WorkstationProfileResponse> refreshStartupOrder(
            @PathVariable UUID profileId) {
        return ResponseEntity.ok(workstationProfileService.refreshStartupOrder(profileId));
    }
}
