package com.codeops.registry.repository;

import com.codeops.registry.entity.PortRange;
import com.codeops.registry.entity.enums.PortType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link PortRange} entities.
 */
@Repository
public interface PortRangeRepository extends JpaRepository<PortRange, UUID> {

    /** Lists all port ranges for a team. */
    List<PortRange> findByTeamId(UUID teamId);

    /** Lists port ranges for a team in a specific environment. */
    List<PortRange> findByTeamIdAndEnvironment(UUID teamId, String environment);

    /** Finds a specific port range by team, type, and environment. */
    Optional<PortRange> findByTeamIdAndPortTypeAndEnvironment(UUID teamId, PortType portType, String environment);

    /** Checks if any port ranges exist for a team. */
    boolean existsByTeamId(UUID teamId);
}
