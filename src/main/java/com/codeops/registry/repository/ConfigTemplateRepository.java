package com.codeops.registry.repository;

import com.codeops.registry.entity.ConfigTemplate;
import com.codeops.registry.entity.enums.ConfigTemplateType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ConfigTemplate} entities.
 */
@Repository
public interface ConfigTemplateRepository extends JpaRepository<ConfigTemplate, UUID> {

    /** Lists all config templates for a service. */
    List<ConfigTemplate> findByServiceId(UUID serviceId);

    /** Lists config templates for a service in a specific environment. */
    List<ConfigTemplate> findByServiceIdAndEnvironment(UUID serviceId, String environment);

    /** Lists config templates for a service of a specific type. */
    List<ConfigTemplate> findByServiceIdAndTemplateType(UUID serviceId, ConfigTemplateType type);

    /** Finds a specific config template by service, type, and environment. */
    Optional<ConfigTemplate> findByServiceIdAndTemplateTypeAndEnvironment(UUID serviceId, ConfigTemplateType type, String environment);
}
