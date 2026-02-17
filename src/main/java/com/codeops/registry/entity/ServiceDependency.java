package com.codeops.registry.entity;

import com.codeops.registry.entity.enums.DependencyType;
import jakarta.persistence.*;
import lombok.*;

/**
 * Directed dependency edge between two registered services.
 *
 * <p>Represents that the {@code sourceService} depends on the {@code targetService}
 * via the specified {@link DependencyType} communication mechanism.</p>
 */
@Entity
@Table(name = "service_dependencies",
        uniqueConstraints = @UniqueConstraint(name = "uk_sd_source_target_type",
                columnNames = {"source_service_id", "target_service_id", "dependency_type"}),
        indexes = {
                @Index(name = "idx_sd_source_id", columnList = "source_service_id"),
                @Index(name = "idx_sd_target_id", columnList = "target_service_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceDependency extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_service_id", nullable = false)
    private ServiceRegistration sourceService;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_service_id", nullable = false)
    private ServiceRegistration targetService;

    @Enumerated(EnumType.STRING)
    @Column(name = "dependency_type", nullable = false, length = 30)
    private DependencyType dependencyType;

    @Column(name = "description", length = 500)
    private String description;

    @Builder.Default
    @Column(name = "is_required", nullable = false)
    private Boolean isRequired = true;

    @Column(name = "target_endpoint", length = 500)
    private String targetEndpoint;
}
