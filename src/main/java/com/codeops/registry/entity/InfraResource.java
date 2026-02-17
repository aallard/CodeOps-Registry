package com.codeops.registry.entity;

import com.codeops.registry.entity.enums.InfraResourceType;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Cloud or infrastructure resource with ownership tracking.
 *
 * <p>Resources may be owned by a specific {@link ServiceRegistration} or shared
 * across the team (when {@code service} is null).</p>
 */
@Entity
@Table(name = "infra_resources",
        uniqueConstraints = @UniqueConstraint(name = "uk_ir_team_type_name_env",
                columnNames = {"team_id", "resource_type", "resource_name", "environment"}),
        indexes = {
                @Index(name = "idx_ir_team_id", columnList = "team_id"),
                @Index(name = "idx_ir_service_id", columnList = "service_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InfraResource extends BaseEntity {

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private ServiceRegistration service;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 30)
    private InfraResourceType resourceType;

    @Column(name = "resource_name", nullable = false, length = 300)
    private String resourceName;

    @Column(name = "environment", nullable = false, length = 50)
    private String environment;

    @Column(name = "region", length = 30)
    private String region;

    @Column(name = "arn_or_url", length = 500)
    private String arnOrUrl;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;
}
