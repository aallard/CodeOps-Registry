package com.codeops.registry.repository;

import com.codeops.registry.entity.PortAllocation;
import com.codeops.registry.entity.enums.PortType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link PortAllocation} entities.
 */
@Repository
public interface PortAllocationRepository extends JpaRepository<PortAllocation, UUID> {

    /** Lists all port allocations for a service. */
    List<PortAllocation> findByServiceId(UUID serviceId);

    /** Lists port allocations for a service in a specific environment. */
    List<PortAllocation> findByServiceIdAndEnvironment(UUID serviceId, String environment);

    /** Finds all port allocations for a team in an environment (via join to service). */
    @Query("SELECT pa FROM PortAllocation pa JOIN pa.service s WHERE s.teamId = :teamId AND pa.environment = :environment")
    List<PortAllocation> findByTeamIdAndEnvironment(@Param("teamId") UUID teamId, @Param("environment") String environment);

    /** Finds a specific port allocation by team, environment, and port number. */
    @Query("SELECT pa FROM PortAllocation pa JOIN pa.service s WHERE s.teamId = :teamId AND pa.environment = :environment AND pa.portNumber = :portNumber")
    Optional<PortAllocation> findByTeamIdAndEnvironmentAndPortNumber(@Param("teamId") UUID teamId, @Param("environment") String environment, @Param("portNumber") Integer portNumber);

    /** Finds all port allocations of a specific type for a team in an environment, ordered by port number. */
    @Query("SELECT pa FROM PortAllocation pa JOIN pa.service s WHERE s.teamId = :teamId AND pa.environment = :environment AND pa.portType = :portType ORDER BY pa.portNumber ASC")
    List<PortAllocation> findByTeamIdAndEnvironmentAndPortType(@Param("teamId") UUID teamId, @Param("environment") String environment, @Param("portType") PortType portType);

    /** Finds ports allocated to multiple services within a team (conflict detection). */
    @Query("SELECT pa.portNumber, pa.environment, COUNT(DISTINCT pa.service.id) FROM PortAllocation pa JOIN pa.service s WHERE s.teamId = :teamId GROUP BY pa.portNumber, pa.environment HAVING COUNT(DISTINCT pa.service.id) > 1")
    List<Object[]> findConflictingPorts(@Param("teamId") UUID teamId);

    /** Checks if a specific port is already allocated for a service in an environment. */
    boolean existsByServiceIdAndEnvironmentAndPortNumber(UUID serviceId, String environment, Integer portNumber);
}
