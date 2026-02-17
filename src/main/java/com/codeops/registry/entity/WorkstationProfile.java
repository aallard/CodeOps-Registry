package com.codeops.registry.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Named local development profile defining a set of services to run together.
 *
 * <p>Profiles store service IDs and startup configuration as JSON, enabling
 * workstation tooling to spin up the correct service set in the right order.</p>
 */
@Entity
@Table(name = "workstation_profiles",
        indexes = @Index(name = "idx_wp_team_id", columnList = "team_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkstationProfile extends BaseEntity {

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "solution_id")
    private UUID solutionId;

    @Column(name = "services_json", nullable = false, columnDefinition = "TEXT")
    private String servicesJson;

    @Column(name = "startup_order", columnDefinition = "TEXT")
    private String startupOrder;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @Builder.Default
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;
}
