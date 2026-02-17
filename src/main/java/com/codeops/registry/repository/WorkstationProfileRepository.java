package com.codeops.registry.repository;

import com.codeops.registry.entity.WorkstationProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link WorkstationProfile} entities.
 */
@Repository
public interface WorkstationProfileRepository extends JpaRepository<WorkstationProfile, UUID> {

    /** Lists all workstation profiles for a team. */
    List<WorkstationProfile> findByTeamId(UUID teamId);

    /** Finds the default profile for a team. */
    Optional<WorkstationProfile> findByTeamIdAndIsDefaultTrue(UUID teamId);

    /** Finds a profile by team and name. */
    Optional<WorkstationProfile> findByTeamIdAndName(UUID teamId, String name);

    /** Count all profiles for a team. */
    long countByTeamId(UUID teamId);
}
