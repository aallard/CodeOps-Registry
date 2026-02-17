package com.codeops.registry.exception;

/**
 * Base runtime exception for the CodeOps Registry service.
 *
 * <p>All application-specific exceptions extend this class, enabling centralized
 * handling in the {@link com.codeops.registry.config.GlobalExceptionHandler}.</p>
 */
public class CodeOpsRegistryException extends RuntimeException {

    /**
     * Creates a new exception with the specified detail message.
     *
     * @param message the detail message
     */
    public CodeOpsRegistryException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public CodeOpsRegistryException(String message, Throwable cause) {
        super(message, cause);
    }
}
