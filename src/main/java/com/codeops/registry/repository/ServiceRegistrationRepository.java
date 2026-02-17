package com.codeops.registry.repository;

import com.codeops.registry.entity.ServiceRegistration;
import com.codeops.registry.entity.enums.ServiceStatus;
import com.codeops.registry.entity.enums.ServiceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ServiceRegistration} entities.
 */
@Repository
public interface ServiceRegistrationRepository extends JpaRepository<ServiceRegistration, UUID> {

    /** Finds a service by team and unique slug. */
    Optional<ServiceRegistration> findByTeamIdAndSlug(UUID teamId, String slug);

    /** Lists all services for a team. */
    List<ServiceRegistration> findByTeamId(UUID teamId);

    /** Paginated listing of all services for a team. */
    Page<ServiceRegistration> findByTeamId(UUID teamId, Pageable pageable);

    /** Paginated listing filtered by team and status. */
    Page<ServiceRegistration> findByTeamIdAndStatus(UUID teamId, ServiceStatus status, Pageable pageable);

    /** Paginated listing filtered by team and service type. */
    Page<ServiceRegistration> findByTeamIdAndServiceType(UUID teamId, ServiceType type, Pageable pageable);

    /** Paginated listing filtered by team, status, and service type. */
    Page<ServiceRegistration> findByTeamIdAndStatusAndServiceType(UUID teamId, ServiceStatus status, ServiceType type, Pageable pageable);

    /** Lists all services for a team with a given status. */
    List<ServiceRegistration> findByTeamIdAndStatus(UUID teamId, ServiceStatus status);

    /** Batch lookup of services by team and IDs. */
    List<ServiceRegistration> findByTeamIdAndIdIn(UUID teamId, List<UUID> ids);

    /** Search by name (case-insensitive contains). */
    Page<ServiceRegistration> findByTeamIdAndNameContainingIgnoreCase(UUID teamId, String name, Pageable pageable);

    /** Count all services for a team. */
    long countByTeamId(UUID teamId);

    /** Count services for a team with a given status. */
    long countByTeamIdAndStatus(UUID teamId, ServiceStatus status);

    /** Checks if a slug is already taken within a team. */
    boolean existsByTeamIdAndSlug(UUID teamId, String slug);
}
