package com.codeops.registry.config;

/**
 * Application-wide constants for the CodeOps Registry service.
 *
 * <p>Defines pagination defaults, port allocation ranges, slug validation rules,
 * health check parameters, and per-team resource limits. These values are used
 * across services and controllers to enforce consistent business rules.</p>
 *
 * <p>This class cannot be instantiated.</p>
 */
public final class AppConstants {
    private AppConstants() {}

    // Pagination
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    // Port ranges (defaults)
    public static final int HTTP_API_RANGE_START = 8080;
    public static final int HTTP_API_RANGE_END = 8199;
    public static final int FRONTEND_DEV_RANGE_START = 3000;
    public static final int FRONTEND_DEV_RANGE_END = 3199;
    public static final int DATABASE_RANGE_START = 5432;
    public static final int DATABASE_RANGE_END = 5499;
    public static final int REDIS_RANGE_START = 6379;
    public static final int REDIS_RANGE_END = 6399;
    public static final int KAFKA_RANGE_START = 9092;
    public static final int KAFKA_RANGE_END = 9099;
    public static final int KAFKA_INTERNAL_RANGE_START = 29092;
    public static final int KAFKA_INTERNAL_RANGE_END = 29099;
    public static final int ZOOKEEPER_RANGE_START = 2181;
    public static final int ZOOKEEPER_RANGE_END = 2199;
    public static final int GRPC_RANGE_START = 50051;
    public static final int GRPC_RANGE_END = 50099;
    public static final int WEBSOCKET_RANGE_START = 8200;
    public static final int WEBSOCKET_RANGE_END = 8249;
    public static final int DEBUG_RANGE_START = 5005;
    public static final int DEBUG_RANGE_END = 5049;
    public static final int ACTUATOR_RANGE_START = 8300;
    public static final int ACTUATOR_RANGE_END = 8349;

    // Slug validation
    public static final String SLUG_PATTERN = "^[a-z0-9][a-z0-9-]*[a-z0-9]$";
    public static final int SLUG_MIN_LENGTH = 2;
    public static final int SLUG_MAX_LENGTH = 63;

    // Health check
    public static final int HEALTH_CHECK_TIMEOUT_SECONDS = 5;
    public static final int DEFAULT_HEALTH_CHECK_INTERVAL_SECONDS = 30;

    // Limits
    public static final int MAX_SERVICES_PER_TEAM = 200;
    public static final int MAX_SOLUTIONS_PER_TEAM = 50;
    public static final int MAX_PORTS_PER_SERVICE = 20;
    public static final int MAX_DEPENDENCIES_PER_SERVICE = 50;

    // Config engine
    public static final int CONFIG_TEMPLATE_MAX_CONTENT_SIZE = 1_000_000;
    public static final String DOCKER_COMPOSE_VERSION = "3.8";
    public static final String DEFAULT_DOCKER_NETWORK = "codeops-network";
}
