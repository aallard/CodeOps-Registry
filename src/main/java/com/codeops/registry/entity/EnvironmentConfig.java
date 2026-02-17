package com.codeops.registry.entity;

import com.codeops.registry.entity.enums.ConfigSource;
import jakarta.persistence.*;
import lombok.*;

/**
 * Non-secret environment-specific configuration key-value pair for a service.
 *
 * <p>Tracks the source of the configuration value (auto-generated, manual,
 * inherited, or derived from registry data).</p>
 */
@Entity
@Table(name = "environment_configs",
        uniqueConstraints = @UniqueConstraint(name = "uk_ec_service_env_key",
                columnNames = {"service_id", "environment", "config_key"}),
        indexes = @Index(name = "idx_ec_service_id", columnList = "service_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnvironmentConfig extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceRegistration service;

    @Column(name = "environment", nullable = false, length = 50)
    private String environment;

    @Column(name = "config_key", nullable = false, length = 200)
    private String configKey;

    @Column(name = "config_value", nullable = false, columnDefinition = "TEXT")
    private String configValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "config_source", nullable = false, length = 20)
    private ConfigSource configSource;

    @Column(name = "description", length = 500)
    private String description;
}
