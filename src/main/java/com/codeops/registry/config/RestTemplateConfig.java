package com.codeops.registry.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Provides a {@link RestTemplate} bean configured with connection and read timeouts
 * for cross-service REST calls (to Vault, Logger, health checks).
 */
@Configuration
public class RestTemplateConfig {

    /**
     * Creates a {@link RestTemplate} with a 5-second connect timeout and 10-second read timeout.
     *
     * @return the configured {@link RestTemplate}
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        return new RestTemplate(factory);
    }
}
