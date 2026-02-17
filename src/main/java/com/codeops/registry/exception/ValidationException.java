package com.codeops.registry.exception;

/**
 * Thrown when a request fails business-rule validation in the Registry.
 *
 * <p>Mapped to HTTP 400 by the
 * {@link com.codeops.registry.config.GlobalExceptionHandler}.</p>
 */
public class ValidationException extends CodeOpsRegistryException {

    /**
     * Creates a validation exception with the specified detail message.
     *
     * @param message the detail message describing the validation failure
     */
    public ValidationException(String message) {
        super(message);
    }
}
