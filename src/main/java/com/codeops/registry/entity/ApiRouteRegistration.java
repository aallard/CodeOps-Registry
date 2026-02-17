package com.codeops.registry.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Claimed API route prefix for a service, optionally behind a gateway.
 *
 * <p>When {@code gatewayService} is set, the route is behind that gateway.
 * When null, the route is a direct endpoint on the service itself.</p>
 */
@Entity
@Table(name = "api_route_registrations",
        indexes = {
                @Index(name = "idx_arr_service_id", columnList = "service_id"),
                @Index(name = "idx_arr_gateway_id", columnList = "gateway_service_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiRouteRegistration extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private ServiceRegistration service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gateway_service_id")
    private ServiceRegistration gatewayService;

    @Column(name = "route_prefix", nullable = false, length = 200)
    private String routePrefix;

    @Column(name = "http_methods", length = 100)
    private String httpMethods;

    @Column(name = "environment", nullable = false, length = 50)
    private String environment;

    @Column(name = "description", length = 500)
    private String description;
}
