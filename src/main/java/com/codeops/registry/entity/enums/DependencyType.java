package com.codeops.registry.entity.enums;

/**
 * Classifies the communication mechanism of a service-to-service dependency.
 */
public enum DependencyType {
    HTTP_REST,
    GRPC,
    KAFKA_TOPIC,
    DATABASE_SHARED,
    REDIS_SHARED,
    LIBRARY,
    GATEWAY_ROUTE,
    WEBSOCKET,
    FILE_SYSTEM,
    OTHER
}
