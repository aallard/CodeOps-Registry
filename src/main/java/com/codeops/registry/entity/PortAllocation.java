package com.codeops.registry.entity;

import com.codeops.registry.entity.enums.PortType;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Allocated port number for a service in a specific environment.
 *
 * <p>Port uniqueness per team and environment is enforced at the service layer
 * since the team ID comes from the parent {@link ServiceRegistration}.</p>
 */
@Entity
@Table(name = "port_allocations",
        uniqueConstraints = @UniqueConstraint(name = "uk_pa_service_env_port",
                columnNames = {"service_id", "environment", "port_number"}),
        indexes = {
                @Index(name = "idx_pa_service_id", columnList = "service_id"),
                @Index(name = "idx_pa_environment", columnList = "environment"),
                @Index(name = "idx_pa_port_number", columnList = "port_number")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortAllocation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceRegistration service;

    @Column(name = "environment", nullable = false, length = 50)
    private String environment;

    @Enumerated(EnumType.STRING)
    @Column(name = "port_type", nullable = false, length = 30)
    private PortType portType;

    @Column(name = "port_number", nullable = false)
    private Integer portNumber;

    @Builder.Default
    @Column(name = "protocol", length = 10)
    private String protocol = "TCP";

    @Column(name = "description", length = 200)
    private String description;

    @Builder.Default
    @Column(name = "is_auto_allocated", nullable = false)
    private Boolean isAutoAllocated = true;

    @Column(name = "allocated_by_user_id", nullable = false)
    private UUID allocatedByUserId;
}
