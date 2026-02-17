package com.codeops.registry.config;

import com.codeops.registry.dto.response.ErrorResponse;
import com.codeops.registry.exception.AuthorizationException;
import com.codeops.registry.exception.CodeOpsRegistryException;
import com.codeops.registry.exception.NotFoundException;
import com.codeops.registry.exception.ValidationException;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * Centralized exception handler for all REST controllers in the Registry API.
 *
 * <p>Catches application-specific exceptions ({@link NotFoundException}, {@link ValidationException},
 * {@link AuthorizationException}, {@link CodeOpsRegistryException}), Spring/JPA exceptions
 * ({@link EntityNotFoundException}, {@link AccessDeniedException}, {@link MethodArgumentNotValidException}),
 * and general uncaught exceptions. Each handler returns a structured {@link ErrorResponse} with the
 * appropriate HTTP status code.</p>
 *
 * <p>Internal error details are never exposed to clients.</p>
 *
 * @see ErrorResponse
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles JPA {@link EntityNotFoundException} by returning a 404 response.
     *
     * @param ex the thrown entity not found exception
     * @return a 404 response with an {@link ErrorResponse} body
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EntityNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(404).body(new ErrorResponse(404, "Resource not found"));
    }

    /**
     * Handles {@link IllegalArgumentException} by returning a 400 response.
     *
     * @param ex the thrown illegal argument exception
     * @return a 400 response with an {@link ErrorResponse} body
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.status(400).body(new ErrorResponse(400, "Invalid request"));
    }

    /**
     * Handles Spring Security {@link AccessDeniedException} by returning a 403 response.
     *
     * @param ex the thrown access denied exception
     * @return a 403 response with an {@link ErrorResponse} body
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(403).body(new ErrorResponse(403, "Access denied"));
    }

    /**
     * Handles Jakarta Bean Validation failures by returning a 400 response with
     * comma-separated field-level error messages.
     *
     * @param ex the thrown method argument not valid exception
     * @return a 400 response with an {@link ErrorResponse} body containing all field error details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation failed: {}", msg);
        return ResponseEntity.status(400).body(new ErrorResponse(400, msg));
    }

    /**
     * Handles CodeOps-specific {@link NotFoundException} by returning a 404 response.
     *
     * @param ex the thrown not found exception
     * @return a 404 response with an {@link ErrorResponse} body
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCodeOpsNotFound(NotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(404).body(new ErrorResponse(404, ex.getMessage()));
    }

    /**
     * Handles CodeOps-specific {@link ValidationException} by returning a 400 response.
     *
     * @param ex the thrown validation exception
     * @return a 400 response with an {@link ErrorResponse} body
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleCodeOpsValidation(ValidationException ex) {
        log.warn("Validation failed: {}", ex.getMessage());
        return ResponseEntity.status(400).body(new ErrorResponse(400, ex.getMessage()));
    }

    /**
     * Handles CodeOps-specific {@link AuthorizationException} by returning a 403 response.
     *
     * @param ex the thrown authorization exception
     * @return a 403 response with an {@link ErrorResponse} body
     */
    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<ErrorResponse> handleCodeOpsAuth(AuthorizationException ex) {
        log.warn("Authorization denied: {}", ex.getMessage());
        return ResponseEntity.status(403).body(new ErrorResponse(403, ex.getMessage()));
    }

    /**
     * Handles missing required {@code @RequestParam} parameters by returning a 400 response.
     *
     * @param ex the thrown missing servlet request parameter exception
     * @return a 400 response with an {@link ErrorResponse} body identifying the missing parameter
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("Missing required parameter: {}", ex.getParameterName());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, "Missing required parameter: " + ex.getParameterName()));
    }

    /**
     * Handles malformed JSON or type-mismatch errors in request bodies by returning a 400 response.
     *
     * @param ex the thrown HTTP message not readable exception
     * @return a 400 response with an {@link ErrorResponse} body
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMessage());
        return ResponseEntity.status(400).body(new ErrorResponse(400, "Malformed request body"));
    }

    /**
     * Handles requests for unmapped paths that fall through to the static resource handler.
     *
     * @param ex the thrown no resource found exception
     * @return a 404 response with an {@link ErrorResponse} body
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("No resource found: {}", ex.getMessage());
        return ResponseEntity.status(404).body(new ErrorResponse(404, "Resource not found"));
    }

    /**
     * Handles the base {@link CodeOpsRegistryException} by returning a 500 response with a
     * generic error message. Logs the exception at ERROR level with the full stack trace.
     *
     * @param ex the thrown application exception
     * @return a 500 response with an {@link ErrorResponse} body (internal details not exposed)
     */
    @ExceptionHandler(CodeOpsRegistryException.class)
    public ResponseEntity<ErrorResponse> handleCodeOps(CodeOpsRegistryException ex) {
        log.error("Application exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(500).body(new ErrorResponse(500, "An internal error occurred"));
    }

    /**
     * Catch-all handler for any unhandled exceptions. Returns a 500 response with a
     * generic error message. Logs the exception at ERROR level with the full stack trace.
     *
     * @param ex the unhandled exception
     * @return a 500 response with an {@link ErrorResponse} body (internal details not exposed)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(500).body(new ErrorResponse(500, "An internal error occurred"));
    }
}
