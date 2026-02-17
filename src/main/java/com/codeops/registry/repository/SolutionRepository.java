package com.codeops.registry.repository;

import com.codeops.registry.entity.Solution;
import com.codeops.registry.entity.enums.SolutionCategory;
import com.codeops.registry.entity.enums.SolutionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Solution} entities.
 */
@Repository
public interface SolutionRepository extends JpaRepository<Solution, UUID> {

    /** Finds a solution by team and unique slug. */
    Optional<Solution> findByTeamIdAndSlug(UUID teamId, String slug);

    /** Lists all solutions for a team. */
    List<Solution> findByTeamId(UUID teamId);

    /** Paginated listing of all solutions for a team. */
    Page<Solution> findByTeamId(UUID teamId, Pageable pageable);

    /** Paginated listing filtered by team and status. */
    Page<Solution> findByTeamIdAndStatus(UUID teamId, SolutionStatus status, Pageable pageable);

    /** Paginated listing filtered by team and category. */
    Page<Solution> findByTeamIdAndCategory(UUID teamId, SolutionCategory category, Pageable pageable);

    /** Count all solutions for a team. */
    long countByTeamId(UUID teamId);

    /** Checks if a slug is already taken within a team. */
    boolean existsByTeamIdAndSlug(UUID teamId, String slug);
}
