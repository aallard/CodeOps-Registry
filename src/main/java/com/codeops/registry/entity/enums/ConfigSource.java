package com.codeops.registry.entity.enums;

/**
 * Indicates how an environment configuration value was produced.
 */
public enum ConfigSource {
    AUTO_GENERATED,
    MANUAL,
    INHERITED,
    REGISTRY_DERIVED
}
