package com.codeops.registry.entity;

import com.codeops.registry.entity.enums.SolutionCategory;
import com.codeops.registry.entity.enums.SolutionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Logical grouping of services into a product, application, or platform.
 *
 * <p>Solutions belong to a team and contain {@link SolutionMember} entries
 * that link services with their role within the solution.</p>
 */
@Entity
@Table(name = "solutions",
        uniqueConstraints = @UniqueConstraint(name = "uk_sol_team_slug", columnNames = {"team_id", "slug"}),
        indexes = @Index(name = "idx_sol_team_id", columnList = "team_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Solution extends BaseEntity {

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "slug", nullable = false, length = 63)
    private String slug;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private SolutionCategory category;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SolutionStatus status = SolutionStatus.ACTIVE;

    @Column(name = "icon_name", length = 50)
    private String iconName;

    @Column(name = "color_hex", length = 7)
    private String colorHex;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "repository_url", length = 500)
    private String repositoryUrl;

    @Column(name = "documentation_url", length = 500)
    private String documentationUrl;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @OneToMany(mappedBy = "solution", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SolutionMember> members = new ArrayList<>();
}
