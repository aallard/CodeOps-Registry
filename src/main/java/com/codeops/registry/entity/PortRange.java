package com.codeops.registry.entity;

import com.codeops.registry.entity.enums.PortType;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Configurable port range per team, port type, and environment.
 *
 * <p>Defines the start and end of an allowed port range for auto-allocation.
 * Falls back to {@link com.codeops.registry.config.AppConstants} defaults
 * when no team-specific range is configured.</p>
 */
@Entity
@Table(name = "port_ranges",
        uniqueConstraints = @UniqueConstraint(name = "uk_pr_team_type_env",
                columnNames = {"team_id", "port_type", "environment"}),
        indexes = @Index(name = "idx_pr_team_id", columnList = "team_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortRange extends BaseEntity {

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "port_type", nullable = false, length = 30)
    private PortType portType;

    @Column(name = "range_start", nullable = false)
    private Integer rangeStart;

    @Column(name = "range_end", nullable = false)
    private Integer rangeEnd;

    @Column(name = "environment", nullable = false, length = 50)
    private String environment;

    @Column(name = "description", length = 200)
    private String description;
}
