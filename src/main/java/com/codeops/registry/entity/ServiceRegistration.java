package com.codeops.registry.entity;

import com.codeops.registry.entity.enums.HealthStatus;
import com.codeops.registry.entity.enums.ServiceStatus;
import com.codeops.registry.entity.enums.ServiceType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Core entity representing a registered service in the development ecosystem.
 *
 * <p>Each service belongs to a team (referenced by UUID, not a FK since teams live in
 * CodeOps-Server's database). Services track their type, status, health, repository info,
 * and hold relationships to port allocations, dependencies, routes, configs, and solutions.</p>
 */
@Entity
@Table(name = "service_registrations",
        uniqueConstraints = @UniqueConstraint(name = "uk_sr_team_slug", columnNames = {"team_id", "slug"}),
        indexes = {
                @Index(name = "idx_sr_team_id", columnList = "team_id"),
                @Index(name = "idx_sr_status", columnList = "status")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceRegistration extends BaseEntity {

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "slug", nullable = false, length = 63)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false, length = 30)
    private ServiceType serviceType;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "repo_url", length = 500)
    private String repoUrl;

    @Column(name = "repo_full_name", length = 200)
    private String repoFullName;

    @Builder.Default
    @Column(name = "default_branch", length = 50)
    private String defaultBranch = "main";

    @Column(name = "tech_stack", length = 500)
    private String techStack;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ServiceStatus status = ServiceStatus.ACTIVE;

    @Column(name = "health_check_url", length = 500)
    private String healthCheckUrl;

    @Builder.Default
    @Column(name = "health_check_interval_seconds")
    private Integer healthCheckIntervalSeconds = 30;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_health_status", length = 20)
    private HealthStatus lastHealthStatus;

    @Column(name = "last_health_check_at")
    private Instant lastHealthCheckAt;

    @Column(name = "environments_json", columnDefinition = "TEXT")
    private String environmentsJson;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PortAllocation> portAllocations = new ArrayList<>();

    @OneToMany(mappedBy = "sourceService")
    @Builder.Default
    private List<ServiceDependency> dependenciesAsSource = new ArrayList<>();

    @OneToMany(mappedBy = "targetService")
    @Builder.Default
    private List<ServiceDependency> dependenciesAsTarget = new ArrayList<>();

    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ApiRouteRegistration> routes = new ArrayList<>();

    @OneToMany(mappedBy = "service")
    @Builder.Default
    private List<SolutionMember> solutionMemberships = new ArrayList<>();

    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ConfigTemplate> configTemplates = new ArrayList<>();

    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<EnvironmentConfig> environmentConfigs = new ArrayList<>();
}
