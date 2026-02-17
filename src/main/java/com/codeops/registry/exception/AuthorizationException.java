package com.codeops.registry.exception;

/**
 * Thrown when a user lacks permission to perform a Registry operation.
 *
 * <p>Mapped to HTTP 403 by the
 * {@link com.codeops.registry.config.GlobalExceptionHandler}.</p>
 */
public class AuthorizationException extends CodeOpsRegistryException {

    /**
     * Creates an authorization exception with the specified detail message.
     *
     * @param message the detail message describing the authorization failure
     */
    public AuthorizationException(String message) {
        super(message);
    }
}
