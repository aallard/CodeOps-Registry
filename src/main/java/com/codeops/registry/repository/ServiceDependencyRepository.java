package com.codeops.registry.repository;

import com.codeops.registry.entity.ServiceDependency;
import com.codeops.registry.entity.enums.DependencyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ServiceDependency} entities.
 */
@Repository
public interface ServiceDependencyRepository extends JpaRepository<ServiceDependency, UUID> {

    /** Lists all dependencies where the given service is the source (depends on others). */
    List<ServiceDependency> findBySourceServiceId(UUID sourceServiceId);

    /** Lists all dependencies where the given service is the target (depended upon). */
    List<ServiceDependency> findByTargetServiceId(UUID targetServiceId);

    /** Finds a specific dependency edge by source, target, and type. */
    Optional<ServiceDependency> findBySourceServiceIdAndTargetServiceIdAndDependencyType(UUID sourceId, UUID targetId, DependencyType type);

    /** Checks if any dependency exists between two services (any type). */
    boolean existsBySourceServiceIdAndTargetServiceId(UUID sourceId, UUID targetId);

    /** Finds all dependencies for a team (via join to source service). */
    @Query("SELECT sd FROM ServiceDependency sd JOIN sd.sourceService s WHERE s.teamId = :teamId")
    List<ServiceDependency> findAllByTeamId(@Param("teamId") UUID teamId);

    /** Count outbound dependencies for a service. */
    long countBySourceServiceId(UUID sourceServiceId);

    /** Count inbound dependencies for a service. */
    long countByTargetServiceId(UUID targetServiceId);
}
