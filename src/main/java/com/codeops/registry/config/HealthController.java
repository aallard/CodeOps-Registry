package com.codeops.registry.config;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * REST controller providing a public health check endpoint for the Registry service.
 *
 * <p>This endpoint is excluded from JWT authentication requirements in
 * {@link com.codeops.registry.security.SecurityConfig} and can be used by load balancers,
 * monitoring systems, and deployment pipelines to verify service availability.</p>
 *
 * @see com.codeops.registry.security.SecurityConfig
 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health")
public class HealthController {

    /**
     * Returns the current health status of the CodeOps Registry service.
     *
     * <p>The response includes the service status ({@code "UP"}), service name
     * ({@code "codeops-registry"}), and the current server timestamp in ISO-8601 format.</p>
     *
     * @return a 200 response containing a map with {@code status}, {@code service}, and
     *         {@code timestamp} keys
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "codeops-registry",
                "timestamp", Instant.now().toString()
        ));
    }
}
