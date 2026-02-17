package com.codeops.registry.repository;

import com.codeops.registry.entity.EnvironmentConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link EnvironmentConfig} entities.
 */
@Repository
public interface EnvironmentConfigRepository extends JpaRepository<EnvironmentConfig, UUID> {

    /** Lists all config entries for a service in a specific environment. */
    List<EnvironmentConfig> findByServiceIdAndEnvironment(UUID serviceId, String environment);

    /** Finds a specific config entry by service, environment, and key. */
    Optional<EnvironmentConfig> findByServiceIdAndEnvironmentAndConfigKey(UUID serviceId, String environment, String configKey);

    /** Lists all config entries for a service across all environments. */
    List<EnvironmentConfig> findByServiceId(UUID serviceId);

    /** Deletes a specific config entry by service, environment, and key. */
    void deleteByServiceIdAndEnvironmentAndConfigKey(UUID serviceId, String environment, String configKey);
}
