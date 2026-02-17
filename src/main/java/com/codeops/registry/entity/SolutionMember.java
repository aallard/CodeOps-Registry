package com.codeops.registry.entity;

import com.codeops.registry.entity.enums.SolutionMemberRole;
import jakarta.persistence.*;
import lombok.*;

/**
 * Join entity linking a {@link Solution} to a {@link ServiceRegistration},
 * with a role describing the service's function within the solution.
 */
@Entity
@Table(name = "solution_members",
        uniqueConstraints = @UniqueConstraint(name = "uk_sm_solution_service", columnNames = {"solution_id", "service_id"}),
        indexes = {
                @Index(name = "idx_sm_solution_id", columnList = "solution_id"),
                @Index(name = "idx_sm_service_id", columnList = "service_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolutionMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solution_id", nullable = false)
    private Solution solution;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceRegistration service;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private SolutionMemberRole role;

    @Builder.Default
    @Column(name = "display_order")
    private Integer displayOrder = 0;

    @Column(name = "notes", length = 500)
    private String notes;
}
