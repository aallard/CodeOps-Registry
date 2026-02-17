package com.codeops.registry.exception;

import java.util.UUID;

/**
 * Thrown when a requested resource cannot be found in the Registry.
 *
 * <p>Mapped to HTTP 404 by the
 * {@link com.codeops.registry.config.GlobalExceptionHandler}.</p>
 */
public class NotFoundException extends CodeOpsRegistryException {

    /**
     * Creates a not-found exception with a custom message.
     *
     * @param message the detail message
     */
    public NotFoundException(String message) {
        super(message);
    }

    /**
     * Creates a not-found exception for an entity identified by UUID.
     *
     * @param entityName the name of the entity type (e.g., "Service")
     * @param id         the UUID that was not found
     */
    public NotFoundException(String entityName, UUID id) {
        super(entityName + " not found with id: " + id);
    }

    /**
     * Creates a not-found exception for an entity identified by a named field.
     *
     * @param entityName the name of the entity type
     * @param field      the field name used for lookup
     * @param value      the field value that was not found
     */
    public NotFoundException(String entityName, String field, String value) {
        super(entityName + " not found with " + field + ": " + value);
    }
}
