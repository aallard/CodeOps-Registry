package com.codeops.registry.repository;

import com.codeops.registry.entity.ApiRouteRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ApiRouteRegistration} entities.
 */
@Repository
public interface ApiRouteRegistrationRepository extends JpaRepository<ApiRouteRegistration, UUID> {

    /** Lists all routes for a service. */
    List<ApiRouteRegistration> findByServiceId(UUID serviceId);

    /** Lists all routes behind a gateway in an environment. */
    List<ApiRouteRegistration> findByGatewayServiceIdAndEnvironment(UUID gatewayServiceId, String environment);

    /** Finds a specific route by gateway, prefix, and environment. */
    @Query("SELECT arr FROM ApiRouteRegistration arr WHERE arr.gatewayService.id = :gatewayId AND arr.environment = :environment AND arr.routePrefix = :prefix")
    Optional<ApiRouteRegistration> findByGatewayAndPrefixAndEnvironment(@Param("gatewayId") UUID gatewayId, @Param("environment") String environment, @Param("prefix") String prefix);

    /** Finds routes whose prefix overlaps with the given prefix (for conflict detection). */
    @Query("SELECT arr FROM ApiRouteRegistration arr WHERE arr.gatewayService.id = :gatewayId AND arr.environment = :environment AND (arr.routePrefix LIKE CONCAT(:prefix, '%') OR :prefix LIKE CONCAT(arr.routePrefix, '%'))")
    List<ApiRouteRegistration> findOverlappingRoutes(@Param("gatewayId") UUID gatewayId, @Param("environment") String environment, @Param("prefix") String prefix);

    /** Finds overlapping direct routes (no gateway) for a team in an environment. */
    @Query("SELECT arr FROM ApiRouteRegistration arr WHERE arr.gatewayService IS NULL AND arr.service.teamId = :teamId AND arr.environment = :environment AND (arr.routePrefix LIKE CONCAT(:prefix, '%') OR :prefix LIKE CONCAT(arr.routePrefix, '%'))")
    List<ApiRouteRegistration> findOverlappingDirectRoutes(@Param("teamId") UUID teamId, @Param("environment") String environment, @Param("prefix") String prefix);
}
