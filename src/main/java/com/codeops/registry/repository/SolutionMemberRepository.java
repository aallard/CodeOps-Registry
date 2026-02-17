package com.codeops.registry.repository;

import com.codeops.registry.entity.SolutionMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link SolutionMember} join entities.
 */
@Repository
public interface SolutionMemberRepository extends JpaRepository<SolutionMember, UUID> {

    /** Lists all members of a solution. */
    List<SolutionMember> findBySolutionId(UUID solutionId);

    /** Lists all members of a solution ordered by display order. */
    List<SolutionMember> findBySolutionIdOrderByDisplayOrderAsc(UUID solutionId);

    /** Lists all solution memberships for a service. */
    List<SolutionMember> findByServiceId(UUID serviceId);

    /** Finds a specific solution-service membership. */
    Optional<SolutionMember> findBySolutionIdAndServiceId(UUID solutionId, UUID serviceId);

    /** Checks if a service is already a member of a solution. */
    boolean existsBySolutionIdAndServiceId(UUID solutionId, UUID serviceId);

    /** Removes a specific solution-service membership. */
    void deleteBySolutionIdAndServiceId(UUID solutionId, UUID serviceId);

    /** Count members in a solution. */
    long countBySolutionId(UUID solutionId);
}
