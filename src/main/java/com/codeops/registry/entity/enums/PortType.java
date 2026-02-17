package com.codeops.registry.entity.enums;

/**
 * Classifies the purpose of an allocated port number.
 */
public enum PortType {
    HTTP_API,
    FRONTEND_DEV,
    DATABASE,
    REDIS,
    KAFKA,
    KAFKA_INTERNAL,
    ZOOKEEPER,
    GRPC,
    WEBSOCKET,
    DEBUG,
    ACTUATOR,
    CUSTOM
}
