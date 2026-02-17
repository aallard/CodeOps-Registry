package com.codeops.registry;

import com.codeops.registry.config.JwtProperties;
import com.codeops.registry.config.ServiceUrlProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Main entry point for the CodeOps Registry service.
 *
 * <p>Manages service registrations, port allocations, solutions, dependencies,
 * topology, and configuration for the AI-First Development Control Plane.</p>
 */
@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, ServiceUrlProperties.class})
public class CodeOpsRegistryApplication {

    /**
     * Launches the CodeOps Registry Spring Boot application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(CodeOpsRegistryApplication.class, args);
    }
}
