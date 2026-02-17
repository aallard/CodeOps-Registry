package com.codeops.registry.repository;

import com.codeops.registry.entity.InfraResource;
import com.codeops.registry.entity.enums.InfraResourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link InfraResource} entities.
 */
@Repository
public interface InfraResourceRepository extends JpaRepository<InfraResource, UUID> {

    /** Lists all infrastructure resources for a team. */
    List<InfraResource> findByTeamId(UUID teamId);

    /** Paginated listing of all resources for a team. */
    Page<InfraResource> findByTeamId(UUID teamId, Pageable pageable);

    /** Paginated listing filtered by team and resource type. */
    Page<InfraResource> findByTeamIdAndResourceType(UUID teamId, InfraResourceType type, Pageable pageable);

    /** Paginated listing filtered by team and environment. */
    Page<InfraResource> findByTeamIdAndEnvironment(UUID teamId, String environment, Pageable pageable);

    /** Paginated listing filtered by team, resource type, and environment. */
    Page<InfraResource> findByTeamIdAndResourceTypeAndEnvironment(UUID teamId, InfraResourceType type, String environment, Pageable pageable);

    /** Lists all resources owned by a specific service. */
    List<InfraResource> findByServiceId(UUID serviceId);

    /** Finds a specific resource by its unique key (team + type + name + environment). */
    Optional<InfraResource> findByTeamIdAndResourceTypeAndResourceNameAndEnvironment(UUID teamId, InfraResourceType type, String name, String environment);

    /** Finds resources with no owning service. */
    @Query("SELECT ir FROM InfraResource ir WHERE ir.teamId = :teamId AND ir.service IS NULL")
    List<InfraResource> findOrphansByTeamId(@Param("teamId") UUID teamId);
}
