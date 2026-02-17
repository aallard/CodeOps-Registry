package com.codeops.registry.service;

import com.codeops.registry.config.AppConstants;
import com.codeops.registry.dto.request.AllocatePortRequest;
import com.codeops.registry.dto.request.UpdatePortRangeRequest;
import com.codeops.registry.dto.response.PortAllocationResponse;
import com.codeops.registry.dto.response.PortCheckResponse;
import com.codeops.registry.dto.response.PortConflictResponse;
import com.codeops.registry.dto.response.PortMapResponse;
import com.codeops.registry.dto.response.PortRangeResponse;
import com.codeops.registry.dto.response.PortRangeWithAllocationsResponse;
import com.codeops.registry.entity.PortAllocation;
import com.codeops.registry.entity.PortRange;
import com.codeops.registry.entity.ServiceRegistration;
import com.codeops.registry.entity.enums.PortType;
import com.codeops.registry.entity.enums.ServiceStatus;
import com.codeops.registry.entity.enums.ServiceType;
import com.codeops.registry.exception.NotFoundException;
import com.codeops.registry.exception.ValidationException;
import com.codeops.registry.repository.PortAllocationRepository;
import com.codeops.registry.repository.PortRangeRepository;
import com.codeops.registry.repository.ServiceRegistrationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PortAllocationService}.
 *
 * <p>Tests cover auto-allocation, manual allocation, port release, conflict detection,
 * port map assembly, range management, and default range seeding.</p>
 */
@ExtendWith(MockitoExtension.class)
class PortAllocationServiceTest {

    @Mock
    private PortAllocationRepository portAllocationRepository;

    @Mock
    private PortRangeRepository portRangeRepository;

    @Mock
    private ServiceRegistrationRepository serviceRepository;

    @InjectMocks
    private PortAllocationService portAllocationService;

    private static final UUID TEAM_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String ENVIRONMENT = "local";

    // ──────────────────────────────────────────────────────────────
    // Helper methods
    // ──────────────────────────────────────────────────────────────

    private ServiceRegistration buildService(UUID serviceId, String name, String slug) {
        ServiceRegistration service = ServiceRegistration.builder()
                .teamId(TEAM_ID)
                .name(name)
                .slug(slug)
                .serviceType(ServiceType.SPRING_BOOT_API)
                .status(ServiceStatus.ACTIVE)
                .createdByUserId(USER_ID)
                .build();
        service.setId(serviceId);
        service.setCreatedAt(Instant.now());
        service.setUpdatedAt(Instant.now());
        return service;
    }

    private PortAllocation buildPortAllocation(UUID allocationId, ServiceRegistration service,
                                                String environment, PortType portType, int portNumber) {
        PortAllocation allocation = PortAllocation.builder()
                .service(service)
                .environment(environment)
                .portType(portType)
                .portNumber(portNumber)
                .protocol("TCP")
                .isAutoAllocated(true)
                .allocatedByUserId(USER_ID)
                .build();
        allocation.setId(allocationId);
        allocation.setCreatedAt(Instant.now());
        allocation.setUpdatedAt(Instant.now());
        return allocation;
    }

    private PortRange buildPortRange(UUID rangeId, UUID teamId, PortType portType,
                                      int rangeStart, int rangeEnd, String environment) {
        PortRange range = PortRange.builder()
                .teamId(teamId)
                .portType(portType)
                .rangeStart(rangeStart)
                .rangeEnd(rangeEnd)
                .environment(environment)
                .build();
        range.setId(rangeId);
        range.setCreatedAt(Instant.now());
        range.setUpdatedAt(Instant.now());
        return range;
    }

    // ──────────────────────────────────────────────────────────────
    // autoAllocate
    // ──────────────────────────────────────────────────────────────

    @Test
    void autoAllocate_firstPort_returnsRangeStart() {
        UUID serviceId = UUID.randomUUID();
        ServiceRegistration service = buildService(serviceId, "my-service", "my-service");
        PortRange range = buildPortRange(UUID.randomUUID(), TEAM_ID, PortType.HTTP_API, 8080, 8199, ENVIRONMENT);

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(portRangeRepository.findByTeamIdAndPortTypeAndEnvironment(TEAM_ID, PortType.HTTP_API, ENVIRONMENT))
                .thenReturn(Optional.of(range));
        when(portAllocationRepository.findByTeamIdAndEnvironmentAndPortType(TEAM_ID, ENVIRONMENT, PortType.HTTP_API))
                .thenReturn(Collections.emptyList());
        when(portAllocationRepository.save(any(PortAllocation.class)))
                .thenAnswer(invocation -> {
                    PortAllocation saved = invocation.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    saved.setCreatedAt(Instant.now());
                    return saved;
                });

        PortAllocationResponse response = portAllocationService.autoAllocate(serviceId, ENVIRONMENT, PortType.HTTP_API, USER_ID);

        assertThat(response.portNumber()).isEqualTo(8080);
        assertThat(response.portType()).isEqualTo(PortType.HTTP_API);
        assertThat(response.environment()).isEqualTo(ENVIRONMENT);
        assertThat(response.isAutoAllocated()).isTrue();
        assertThat(response.serviceId()).isEqualTo(serviceId);
        verify(portAllocationRepository).save(any(PortAllocation.class));
    }

    @Test
    void autoAllocate_somePortsTaken_returnsNextAvailable() {
        UUID serviceId = UUID.randomUUID();
        ServiceRegistration service = buildService(serviceId, "my-service", "my-service");
        PortRange range = buildPortRange(UUID.randomUUID(), TEAM_ID, PortType.HTTP_API, 8080, 8199, ENVIRONMENT);

        PortAllocation existing1 = buildPortAllocation(UUID.randomUUID(), service, ENVIRONMENT, PortType.HTTP_API, 8080);
        PortAllocation existing2 = buildPortAllocation(UUID.randomUUID(), service, ENVIRONMENT, PortType.HTTP_API, 8081);
        PortAllocation existing3 = buildPortAllocation(UUID.randomUUID(), service, ENVIRONMENT, PortType.HTTP_API, 8082);

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(portRangeRepository.findByTeamIdAndPortTypeAndEnvironment(TEAM_ID, PortType.HTTP_API, ENVIRONMENT))
                .thenReturn(Optional.of(range));
        when(portAllocationRepository.findByTeamIdAndEnvironmentAndPortType(TEAM_ID, ENVIRONMENT, PortType.HTTP_API))
                .thenReturn(List.of(existing1, existing2, existing3));
        when(portAllocationRepository.save(any(PortAllocation.class)))
                .thenAnswer(invocation -> {
                    PortAllocation saved = invocation.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    saved.setCreatedAt(Instant.now());
                    return saved;
                });

        PortAllocationResponse response = portAllocationService.autoAllocate(serviceId, ENVIRONMENT, PortType.HTTP_API, USER_ID);

        assertThat(response.portNumber()).isEqualTo(8083);
    }

    @Test
    void autoAllocate_gapInAllocations_fillsGap() {
        UUID serviceId = UUID.randomUUID();
        ServiceRegistration service = buildService(serviceId, "my-service", "my-service");
        PortRange range = buildPortRange(UUID.randomUUID(), TEAM_ID, PortType.HTTP_API, 8080, 8199, ENVIRONMENT);

        PortAllocation existing1 = buildPortAllocation(UUID.randomUUID(), service, ENVIRONMENT, PortType.HTTP_API, 8080);
        PortAllocation existing2 = buildPortAllocation(UUID.randomUUID(), service, ENVIRONMENT, PortType.HTTP_API, 8082);

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(portRangeRepository.findByTeamIdAndPortTypeAndEnvironment(TEAM_ID, PortType.HTTP_API, ENVIRONMENT))
                .thenReturn(Optional.of(range));
        when(portAllocationRepository.findByTeamIdAndEnvironmentAndPortType(TEAM_ID, ENVIRONMENT, PortType.HTTP_API))
                .thenReturn(List.of(existing1, existing2));
        when(portAllocationRepository.save(any(PortAllocation.class)))
                .thenAnswer(invocation -> {
                    PortAllocation saved = invocation.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    saved.setCreatedAt(Instant.now());
                    return saved;
                });

        PortAllocationResponse response = portAllocationService.autoAllocate(serviceId, ENVIRONMENT, PortType.HTTP_API, USER_ID);

        assertThat(response.portNumber()).isEqualTo(8081);
    }

    @Test
    void autoAllocate_rangeFull_throwsValidation() {
        UUID serviceId = UUID.randomUUID();
        ServiceRegistration service = buildService(serviceId, "my-service", "my-service");
        PortRange range = buildPortRange(UUID.randomUUID(), TEAM_ID, PortType.HTTP_API, 8080, 8082, ENVIRONMENT);

        PortAllocation existing1 = buildPortAllocation(UUID.randomUUID(), service, ENVIRONMENT, PortType.HTTP_API, 8080);
        PortAllocation existing2 = buildPortAllocation(UUID.randomUUID(), service, ENVIRONMENT, PortType.HTTP_API, 8081);
        PortAllocation existing3 = buildPortAllocation(UUID.randomUUID(), service, ENVIRONMENT, PortType.HTTP_API, 8082);

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(portRangeRepository.findByTeamIdAndPortTypeAndEnvironment(TEAM_ID, PortType.HTTP_API, ENVIRONMENT))
                .thenReturn(Optional.of(range));
        when(portAllocationRepository.findByTeamIdAndEnvironmentAndPortType(TEAM_ID, ENVIRONMENT, PortType.HTTP_API))
                .thenReturn(List.of(existing1, existing2, existing3));

        assertThatThrownBy(() -> portAllocationService.autoAllocate(serviceId, ENVIRONMENT, PortType.HTTP_API, USER_ID))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("No available ports in range 8080-8082");

        verify(portAllocationRepository, never()).save(any());
    }

    @Test
    void autoAllocate_noRangeConfigured_throwsValidation() {
        UUID serviceId = UUID.randomUUID();
        ServiceRegistration service = buildService(serviceId, "my-service", "my-service");

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(portRangeRepository.findByTeamIdAndPortTypeAndEnvironment(TEAM_ID, PortType.HTTP_API, "dev"))
                .thenReturn(Optional.empty());
        when(portRangeRepository.findByTeamIdAndPortTypeAndEnvironment(TEAM_ID, PortType.HTTP_API, "local"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> portAllocationService.autoAllocate(serviceId, "dev", PortType.HTTP_API, USER_ID))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("No port range configured for type HTTP_API")
                .hasMessageContaining("Seed default ranges first");

        verify(portAllocationRepository, never()).save(any());
    }

    @Test
    void autoAllocate_fallbackToLocalRange() {
        UUID serviceId = UUID.randomUUID();
        ServiceRegistration service = buildService(serviceId, "my-service", "my-service");
        PortRange localRange = buildPortRange(UUID.randomUUID(), TEAM_ID, PortType.HTTP_API, 8080, 8199, "local");

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(portRangeRepository.findByTeamIdAndPortTypeAndEnvironment(TEAM_ID, PortType.HTTP_API, "dev"))
                .thenReturn(Optional.empty());
        when(portRangeRepository.findByTeamIdAndPortTypeAndEnvironment(TEAM_ID, PortType.HTTP_API, "local"))
                .thenReturn(Optional.of(localRange));
        when(portAllocationRepository.findByTeamIdAndEnvironmentAndPortType(TEAM_ID, "dev", PortType.HTTP_API))
                .thenReturn(Collections.emptyList());
        when(portAllocationRepository.save(any(PortAllocation.class)))
                .thenAnswer(invocation -> {
                    PortAllocation saved = invocation.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    saved.setCreatedAt(Instant.now());
                    return saved;
                });

        PortAllocationResponse response = portAllocationService.autoAllocate(serviceId, "dev", PortType.HTTP_API, USER_ID);

        assertThat(response.portNumber()).isEqualTo(8080);
        assertThat(response.environment()).isEqualTo("dev");
    }

    // ──────────────────────────────────────────────────────────────
    // autoAllocateAll
    // ──────────────────────────────────────────────────────────────

    @Test
    void autoAllocateAll_multipleTypes_allocatesEach() {
        UUID serviceId = UUID.randomUUID();
        ServiceRegistration service = buildService(serviceId, "my-service", "my-service");
        PortRange httpRange = buildPortRange(UUID.randomUUID(), TEAM_ID, PortType.HTTP_API, 8080, 8199, ENVIRONMENT);
        PortRange dbRange = buildPortRange(UUID.randomUUID(), TEAM_ID, PortType.DATABASE, 5432, 5499, ENVIRONMENT);

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(portRangeRepository.findByTeamIdAndPortTypeAndEnvironment(TEAM_ID, PortType.HTTP_API, ENVIRONMENT))
                .thenReturn(Optional.of(httpRange));
        when(portRangeRepository.findByTeamIdAndPortTypeAndEnvironment(TEAM_ID, PortType.DATABASE, ENVIRONMENT))
                .thenReturn(Optional.of(dbRange));
        when(portAllocationRepository.findByTeamIdAndEnvironmentAndPortType(TEAM_ID, ENVIRONMENT, PortType.HTTP_API))
                .thenReturn(Collections.emptyList());
        when(portAllocationRepository.findByTeamIdAndEnvironmentAndPortType(TEAM_ID, ENVIRONMENT, PortType.DATABASE))
                .thenReturn(Collections.emptyList());
        when(portAllocationRepository.save(any(PortAllocation.class)))
                .thenAnswer(invocation -> {
                    PortAllocation saved = invocation.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    saved.setCreatedAt(Instant.now());
                    return saved;
                });

        List<PortAllocationResponse> responses = portAllocationService.autoAllocateAll(
                serviceId, ENVIRONMENT, List.of(PortType.HTTP_API, PortType.DATABASE), USER_ID);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).portType()).isEqualTo(PortType.HTTP_API);
        assertThat(responses.get(0).portNumber()).isEqualTo(8080);
        assertThat(responses.get(1).portType()).isEqualTo(PortType.DATABASE);
        assertThat(responses.get(1).portNumber()).isEqualTo(5432);
    }

    // ──────────────────────────────────────────────────────────────
    // manualAllocate
    // ──────────────────────────────────────────────────────────────

    @Test
    void manualAllocate_availablePort_success() {
        UUID serviceId = UUID.randomUUID();
        ServiceRegistration service = buildService(serviceId, "my-service", "my-service");
        AllocatePortRequest request = new AllocatePortRequest(
                serviceId, ENVIRONMENT, PortType.HTTP_API, 9090, "TCP", "Custom API port");

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(portAllocationRepository.findByTeamIdAndEnvironmentAndPortNumber(TEAM_ID, ENVIRONMENT, 9090))
                .thenReturn(Optional.empty());
        when(portAllocationRepository.save(any(PortAllocation.class)))
                .thenAnswer(invocation -> {
                    PortAllocation saved = invocation.getArgument(0);
                    saved.setId(UUID.randomUUID());
                    saved.setCreatedAt(Instant.now());
                    return saved;
                });

        PortAllocationResponse response = portAllocationService.manualAllocate(request, USER_ID);

        assertThat(response.portNumber()).isEqualTo(9090);
        assertThat(response.portType()).isEqualTo(PortType.HTTP_API);
        assertThat(response.isAutoAllocated()).isFalse();
        assertThat(response.description()).isEqualTo("Custom API port");
        assertThat(response.serviceName()).isEqualTo("my-service");
        verify(portAllocationRepository).save(any(PortAllocation.class));
    }

    @Test
    void manualAllocate_portTaken_throwsValidation() {
        UUID serviceId = UUID.randomUUID();
        ServiceRegistration service = buildService(serviceId, "my-service", "my-service");
        ServiceRegistration ownerService = buildService(UUID.randomUUID(), "owner-service", "owner-service");
        PortAllocation existingAllocation = buildPortAllocation(UUID.randomUUID(), ownerService, ENVIRONMENT, PortType.HTTP_API, 9090);

        AllocatePortRequest request = new AllocatePortRequest(
                serviceId, ENVIRONMENT, PortType.HTTP_API, 9090, "TCP", null);

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(service));
        when(portAllocationRepository.findByTeamIdAndEnvironmentAndPortNumber(TEAM_ID, ENVIRONMENT, 9090))
                .thenReturn(Optional.of(existingAllocation));

        assertThatThrownBy(() -> portAllocationService.manualAllocate(request, USER_ID))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Port 9090 is already allocated to service owner-service");

        verify(portAllocationRepository, never()).save(any());
    }

    // ──────────────────────────────────────────────────────────────
    // releasePort
    // ──────────────────────────────────────────────────────────────

    @Test
    void releasePort_success() {
        UUID allocationId = UUID.randomUUID();
        ServiceRegistration service = buildService(UUID.randomUUID(), "my-service", "my-service");
        PortAllocation allocation = buildPortAllocation(allocationId, service, ENVIRONMENT, PortType.HTTP_API, 8080);

        when(portAllocationRepository.findById(allocationId)).thenReturn(Optional.of(allocation));

        portAllocationService.releasePort(allocationId);

        verify(portAllocationRepository).delete(allocation);
    }

    @Test
    void releasePort_notFound_throwsNotFound() {
        UUID allocationId = UUID.randomUUID();

        when(portAllocationRepository.findById(allocationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> portAllocationService.releasePort(allocationId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("PortAllocation")
                .hasMessageContaining(allocationId.toString());

        verify(portAllocationRepository, never()).delete(any());
    }

    // ──────────────────────────────────────────────────────────────
    // checkAvailability
    // ──────────────────────────────────────────────────────────────

    @Test
    void checkAvailability_available_returnsTrue() {
        when(portAllocationRepository.findByTeamIdAndEnvironmentAndPortNumber(TEAM_ID, ENVIRONMENT, 8080))
                .thenReturn(Optional.empty());

        PortCheckResponse response = portAllocationService.checkAvailability(TEAM_ID, 8080, ENVIRONMENT);

        assertThat(response.available()).isTrue();
        assertThat(response.portNumber()).isEqualTo(8080);
        assertThat(response.environment()).isEqualTo(ENVIRONMENT);
        assertThat(response.currentOwnerServiceId()).isNull();
        assertThat(response.currentOwnerServiceName()).isNull();
        assertThat(response.currentOwnerPortType()).isNull();
    }

    @Test
    void checkAvailability_taken_returnsFalseWithOwner() {
        UUID ownerServiceId = UUID.randomUUID();
        ServiceRegistration ownerService = buildService(ownerServiceId, "owner-service", "owner-service");
        PortAllocation existing = buildPortAllocation(UUID.randomUUID(), ownerService, ENVIRONMENT, PortType.HTTP_API, 8080);

        when(portAllocationRepository.findByTeamIdAndEnvironmentAndPortNumber(TEAM_ID, ENVIRONMENT, 8080))
                .thenReturn(Optional.of(existing));

        PortCheckResponse response = portAllocationService.checkAvailability(TEAM_ID, 8080, ENVIRONMENT);

        assertThat(response.available()).isFalse();
        assertThat(response.portNumber()).isEqualTo(8080);
        assertThat(response.currentOwnerServiceId()).isEqualTo(ownerServiceId);
        assertThat(response.currentOwnerServiceName()).isEqualTo("owner-service");
        assertThat(response.currentOwnerPortType()).isEqualTo(PortType.HTTP_API);
    }

    // ──────────────────────────────────────────────────────────────
    // getPortsForService
    // ──────────────────────────────────────────────────────────────

    @Test
    void getPortsForService_withEnvironment() {
        UUID serviceId = UUID.randomUUID();
        ServiceRegistration service = buildService(serviceId, "my-service", "my-service");
        PortAllocation allocation = buildPortAllocation(UUID.randomUUID(), service, ENVIRONMENT, PortType.HTTP_API, 8080);

        when(portAllocationRepository.findByServiceIdAndEnvironment(serviceId, ENVIRONMENT))
                .thenReturn(List.of(allocation));

        List<PortAllocationResponse> responses = portAllocationService.getPortsForService(serviceId, ENVIRONMENT);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).portNumber()).isEqualTo(8080);
        assertThat(responses.get(0).environment()).isEqualTo(ENVIRONMENT);
        verify(portAllocationRepository).findByServiceIdAndEnvironment(serviceId, ENVIRONMENT);
        verify(portAllocationRepository, never()).findByServiceId(any());
    }

    @Test
    void getPortsForService_allEnvironments() {
        UUID serviceId = UUID.randomUUID();
        ServiceRegistration service = buildService(serviceId, "my-service", "my-service");
        PortAllocation localAlloc = buildPortAllocation(UUID.randomUUID(), service, "local", PortType.HTTP_API, 8080);
        PortAllocation devAlloc = buildPortAllocation(UUID.randomUUID(), service, "dev", PortType.HTTP_API, 8081);

        when(portAllocationRepository.findByServiceId(serviceId))
                .thenReturn(List.of(localAlloc, devAlloc));

        List<PortAllocationResponse> responses = portAllocationService.getPortsForService(serviceId, null);

        assertThat(responses).hasSize(2);
        verify(portAllocationRepository).findByServiceId(serviceId);
        verify(portAllocationRepository, never()).findByServiceIdAndEnvironment(any(), any());
    }

    // ──────────────────────────────────────────────────────────────
    // getPortsForTeam
    // ──────────────────────────────────────────────────────────────

    @Test
    void getPortsForTeam_returnsList() {
        ServiceRegistration service1 = buildService(UUID.randomUUID(), "service-a", "service-a");
        ServiceRegistration service2 = buildService(UUID.randomUUID(), "service-b", "service-b");
        PortAllocation alloc1 = buildPortAllocation(UUID.randomUUID(), service1, ENVIRONMENT, PortType.HTTP_API, 8080);
        PortAllocation alloc2 = buildPortAllocation(UUID.randomUUID(), service2, ENVIRONMENT, PortType.DATABASE, 5432);

        when(portAllocationRepository.findByTeamIdAndEnvironment(TEAM_ID, ENVIRONMENT))
                .thenReturn(List.of(alloc1, alloc2));

        List<PortAllocationResponse> responses = portAllocationService.getPortsForTeam(TEAM_ID, ENVIRONMENT);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).serviceName()).isEqualTo("service-a");
        assertThat(responses.get(1).serviceName()).isEqualTo("service-b");
    }

    // ──────────────────────────────────────────────────────────────
    // getPortMap
    // ──────────────────────────────────────────────────────────────

    @Test
    void getPortMap_assemblesCorrectly() {
        PortRange httpRange = buildPortRange(UUID.randomUUID(), TEAM_ID, PortType.HTTP_API, 8080, 8089, ENVIRONMENT);
        ServiceRegistration service = buildService(UUID.randomUUID(), "my-service", "my-service");
        PortAllocation alloc1 = buildPortAllocation(UUID.randomUUID(), service, ENVIRONMENT, PortType.HTTP_API, 8080);
        PortAllocation alloc2 = buildPortAllocation(UUID.randomUUID(), service, ENVIRONMENT, PortType.HTTP_API, 8081);

        when(portRangeRepository.findByTeamIdAndEnvironment(TEAM_ID, ENVIRONMENT))
                .thenReturn(List.of(httpRange));
        when(portAllocationRepository.findByTeamIdAndEnvironment(TEAM_ID, ENVIRONMENT))
                .thenReturn(List.of(alloc1, alloc2));

        PortMapResponse response = portAllocationService.getPortMap(TEAM_ID, ENVIRONMENT);

        assertThat(response.teamId()).isEqualTo(TEAM_ID);
        assertThat(response.environment()).isEqualTo(ENVIRONMENT);
        assertThat(response.ranges()).hasSize(1);

        PortRangeWithAllocationsResponse rangeResp = response.ranges().get(0);
        assertThat(rangeResp.portType()).isEqualTo(PortType.HTTP_API);
        assertThat(rangeResp.rangeStart()).isEqualTo(8080);
        assertThat(rangeResp.rangeEnd()).isEqualTo(8089);
        assertThat(rangeResp.totalCapacity()).isEqualTo(10);
        assertThat(rangeResp.allocated()).isEqualTo(2);
        assertThat(rangeResp.available()).isEqualTo(8);
        assertThat(rangeResp.allocations()).hasSize(2);

        assertThat(response.totalAllocated()).isEqualTo(2);
        assertThat(response.totalAvailable()).isEqualTo(8);
    }

    @Test
    void getPortMap_multipleRanges_groupsAllocations() {
        PortRange httpRange = buildPortRange(UUID.randomUUID(), TEAM_ID, PortType.HTTP_API, 8080, 8089, ENVIRONMENT);
        PortRange dbRange = buildPortRange(UUID.randomUUID(), TEAM_ID, PortType.DATABASE, 5432, 5439, ENVIRONMENT);

        ServiceRegistration service = buildService(UUID.randomUUID(), "my-service", "my-service");
        PortAllocation httpAlloc = buildPortAllocation(UUID.randomUUID(), service, ENVIRONMENT, PortType.HTTP_API, 8080);
        PortAllocation dbAlloc = buildPortAllocation(UUID.randomUUID(), service, ENVIRONMENT, PortType.DATABASE, 5432);

        when(portRangeRepository.findByTeamIdAndEnvironment(TEAM_ID, ENVIRONMENT))
                .thenReturn(List.of(httpRange, dbRange));
        when(portAllocationRepository.findByTeamIdAndEnvironment(TEAM_ID, ENVIRONMENT))
                .thenReturn(List.of(httpAlloc, dbAlloc));

        PortMapResponse response = portAllocationService.getPortMap(TEAM_ID, ENVIRONMENT);

        assertThat(response.ranges()).hasSize(2);

        PortRangeWithAllocationsResponse httpRangeResp = response.ranges().get(0);
        assertThat(httpRangeResp.portType()).isEqualTo(PortType.HTTP_API);
        assertThat(httpRangeResp.allocations()).hasSize(1);
        assertThat(httpRangeResp.allocations().get(0).portNumber()).isEqualTo(8080);

        PortRangeWithAllocationsResponse dbRangeResp = response.ranges().get(1);
        assertThat(dbRangeResp.portType()).isEqualTo(PortType.DATABASE);
        assertThat(dbRangeResp.allocations()).hasSize(1);
        assertThat(dbRangeResp.allocations().get(0).portNumber()).isEqualTo(5432);

        assertThat(response.totalAllocated()).isEqualTo(2);
        assertThat(response.totalAvailable()).isEqualTo(10 + 8 - 2);
    }

    @Test
    void getPortMap_emptyTeam_returnsEmptyRanges() {
        when(portRangeRepository.findByTeamIdAndEnvironment(TEAM_ID, ENVIRONMENT))
                .thenReturn(Collections.emptyList());
        when(portRangeRepository.findByTeamIdAndEnvironment(TEAM_ID, "local"))
                .thenReturn(Collections.emptyList());
        when(portAllocationRepository.findByTeamIdAndEnvironment(TEAM_ID, ENVIRONMENT))
                .thenReturn(Collections.emptyList());

        PortMapResponse response = portAllocationService.getPortMap(TEAM_ID, ENVIRONMENT);

        assertThat(response.ranges()).isEmpty();
        assertThat(response.totalAllocated()).isZero();
        assertThat(response.totalAvailable()).isZero();
    }

    // ──────────────────────────────────────────────────────────────
    // detectConflicts
    // ──────────────────────────────────────────────────────────────

    @Test
    void detectConflicts_noConflicts_returnsEmpty() {
        when(portAllocationRepository.findConflictingPorts(TEAM_ID))
                .thenReturn(Collections.emptyList());

        List<PortConflictResponse> conflicts = portAllocationService.detectConflicts(TEAM_ID);

        assertThat(conflicts).isEmpty();
    }

    @Test
    void detectConflicts_foundConflicts_returnsDetails() {
        Object[] conflictRow = new Object[]{8080, ENVIRONMENT, 2L};

        ServiceRegistration service1 = buildService(UUID.randomUUID(), "service-a", "service-a");
        ServiceRegistration service2 = buildService(UUID.randomUUID(), "service-b", "service-b");
        PortAllocation alloc1 = buildPortAllocation(UUID.randomUUID(), service1, ENVIRONMENT, PortType.HTTP_API, 8080);
        PortAllocation alloc2 = buildPortAllocation(UUID.randomUUID(), service2, ENVIRONMENT, PortType.HTTP_API, 8080);

        List<Object[]> conflictRows = new java.util.ArrayList<>();
        conflictRows.add(conflictRow);
        when(portAllocationRepository.findConflictingPorts(TEAM_ID))
                .thenReturn(conflictRows);
        when(portAllocationRepository.findByTeamIdAndEnvironment(TEAM_ID, ENVIRONMENT))
                .thenReturn(List.of(alloc1, alloc2));

        List<PortConflictResponse> conflicts = portAllocationService.detectConflicts(TEAM_ID);

        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.get(0).portNumber()).isEqualTo(8080);
        assertThat(conflicts.get(0).environment()).isEqualTo(ENVIRONMENT);
        assertThat(conflicts.get(0).conflictingAllocations()).hasSize(2);
        assertThat(conflicts.get(0).conflictingAllocations().get(0).serviceName()).isEqualTo("service-a");
        assertThat(conflicts.get(0).conflictingAllocations().get(1).serviceName()).isEqualTo("service-b");
    }

    // ──────────────────────────────────────────────────────────────
    // getPortRanges
    // ──────────────────────────────────────────────────────────────

    @Test
    void getPortRanges_returnsList() {
        UUID rangeId1 = UUID.randomUUID();
        UUID rangeId2 = UUID.randomUUID();
        PortRange range1 = buildPortRange(rangeId1, TEAM_ID, PortType.HTTP_API, 8080, 8199, ENVIRONMENT);
        PortRange range2 = buildPortRange(rangeId2, TEAM_ID, PortType.DATABASE, 5432, 5499, ENVIRONMENT);

        when(portRangeRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(range1, range2));

        List<PortRangeResponse> responses = portAllocationService.getPortRanges(TEAM_ID);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).id()).isEqualTo(rangeId1);
        assertThat(responses.get(0).portType()).isEqualTo(PortType.HTTP_API);
        assertThat(responses.get(0).rangeStart()).isEqualTo(8080);
        assertThat(responses.get(0).rangeEnd()).isEqualTo(8199);
        assertThat(responses.get(1).id()).isEqualTo(rangeId2);
        assertThat(responses.get(1).portType()).isEqualTo(PortType.DATABASE);
    }

    // ──────────────────────────────────────────────────────────────
    // updatePortRange
    // ──────────────────────────────────────────────────────────────

    @Test
    void updatePortRange_success() {
        UUID rangeId = UUID.randomUUID();
        PortRange range = buildPortRange(rangeId, TEAM_ID, PortType.HTTP_API, 8080, 8199, ENVIRONMENT);
        UpdatePortRangeRequest request = new UpdatePortRangeRequest(8080, 8299, "Extended HTTP range");

        when(portRangeRepository.findById(rangeId)).thenReturn(Optional.of(range));
        when(portAllocationRepository.findByTeamIdAndEnvironmentAndPortType(TEAM_ID, ENVIRONMENT, PortType.HTTP_API))
                .thenReturn(Collections.emptyList());
        when(portRangeRepository.save(any(PortRange.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PortRangeResponse response = portAllocationService.updatePortRange(rangeId, request);

        assertThat(response.rangeStart()).isEqualTo(8080);
        assertThat(response.rangeEnd()).isEqualTo(8299);
        assertThat(response.description()).isEqualTo("Extended HTTP range");
        verify(portRangeRepository).save(range);
    }

    @Test
    void updatePortRange_startGreaterThanEnd_throwsValidation() {
        UUID rangeId = UUID.randomUUID();
        PortRange range = buildPortRange(rangeId, TEAM_ID, PortType.HTTP_API, 8080, 8199, ENVIRONMENT);
        UpdatePortRangeRequest request = new UpdatePortRangeRequest(9000, 8000, null);

        when(portRangeRepository.findById(rangeId)).thenReturn(Optional.of(range));

        assertThatThrownBy(() -> portAllocationService.updatePortRange(rangeId, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Range start must be less than range end");

        verify(portRangeRepository, never()).save(any());
    }

    @Test
    void updatePortRange_shrinksWithExistingAllocations_throwsValidation() {
        UUID rangeId = UUID.randomUUID();
        PortRange range = buildPortRange(rangeId, TEAM_ID, PortType.HTTP_API, 8080, 8199, ENVIRONMENT);

        ServiceRegistration service = buildService(UUID.randomUUID(), "my-service", "my-service");
        PortAllocation outsideAlloc = buildPortAllocation(UUID.randomUUID(), service, ENVIRONMENT, PortType.HTTP_API, 8150);

        UpdatePortRangeRequest request = new UpdatePortRangeRequest(8080, 8099, null);

        when(portRangeRepository.findById(rangeId)).thenReturn(Optional.of(range));
        when(portAllocationRepository.findByTeamIdAndEnvironmentAndPortType(TEAM_ID, ENVIRONMENT, PortType.HTTP_API))
                .thenReturn(List.of(outsideAlloc));

        assertThatThrownBy(() -> portAllocationService.updatePortRange(rangeId, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Cannot shrink range")
                .hasMessageContaining("port 8150")
                .hasMessageContaining("my-service");

        verify(portRangeRepository, never()).save(any());
    }

    // ──────────────────────────────────────────────────────────────
    // seedDefaultRanges
    // ──────────────────────────────────────────────────────────────

    @Test
    void seedDefaultRanges_newTeam_createsAll() {
        when(portRangeRepository.existsByTeamId(TEAM_ID)).thenReturn(false);
        when(portRangeRepository.saveAll(any()))
                .thenAnswer(invocation -> {
                    List<PortRange> ranges = invocation.getArgument(0);
                    ranges.forEach(r -> {
                        r.setId(UUID.randomUUID());
                        r.setCreatedAt(Instant.now());
                        r.setUpdatedAt(Instant.now());
                    });
                    return ranges;
                });

        List<PortRangeResponse> responses = portAllocationService.seedDefaultRanges(TEAM_ID, ENVIRONMENT);

        assertThat(responses).hasSize(12);
        verify(portRangeRepository).saveAll(any());
    }

    @Test
    void seedDefaultRanges_existingTeam_returnsExisting() {
        PortRange existingRange = buildPortRange(UUID.randomUUID(), TEAM_ID, PortType.HTTP_API, 8080, 8199, ENVIRONMENT);

        when(portRangeRepository.existsByTeamId(TEAM_ID)).thenReturn(true);
        when(portRangeRepository.findByTeamId(TEAM_ID)).thenReturn(List.of(existingRange));

        List<PortRangeResponse> responses = portAllocationService.seedDefaultRanges(TEAM_ID, ENVIRONMENT);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).portType()).isEqualTo(PortType.HTTP_API);
        verify(portRangeRepository, never()).saveAll(any());
    }

    @Test
    void seedDefaultRanges_correctRangeValues() {
        when(portRangeRepository.existsByTeamId(TEAM_ID)).thenReturn(false);
        when(portRangeRepository.saveAll(any()))
                .thenAnswer(invocation -> {
                    List<PortRange> ranges = invocation.getArgument(0);
                    ranges.forEach(r -> {
                        r.setId(UUID.randomUUID());
                        r.setCreatedAt(Instant.now());
                        r.setUpdatedAt(Instant.now());
                    });
                    return ranges;
                });

        List<PortRangeResponse> responses = portAllocationService.seedDefaultRanges(TEAM_ID, ENVIRONMENT);

        PortRangeResponse httpApi = responses.stream()
                .filter(r -> r.portType() == PortType.HTTP_API).findFirst().orElseThrow();
        assertThat(httpApi.rangeStart()).isEqualTo(AppConstants.HTTP_API_RANGE_START);
        assertThat(httpApi.rangeEnd()).isEqualTo(AppConstants.HTTP_API_RANGE_END);

        PortRangeResponse frontendDev = responses.stream()
                .filter(r -> r.portType() == PortType.FRONTEND_DEV).findFirst().orElseThrow();
        assertThat(frontendDev.rangeStart()).isEqualTo(AppConstants.FRONTEND_DEV_RANGE_START);
        assertThat(frontendDev.rangeEnd()).isEqualTo(AppConstants.FRONTEND_DEV_RANGE_END);

        PortRangeResponse database = responses.stream()
                .filter(r -> r.portType() == PortType.DATABASE).findFirst().orElseThrow();
        assertThat(database.rangeStart()).isEqualTo(AppConstants.DATABASE_RANGE_START);
        assertThat(database.rangeEnd()).isEqualTo(AppConstants.DATABASE_RANGE_END);

        PortRangeResponse redis = responses.stream()
                .filter(r -> r.portType() == PortType.REDIS).findFirst().orElseThrow();
        assertThat(redis.rangeStart()).isEqualTo(AppConstants.REDIS_RANGE_START);
        assertThat(redis.rangeEnd()).isEqualTo(AppConstants.REDIS_RANGE_END);

        PortRangeResponse kafka = responses.stream()
                .filter(r -> r.portType() == PortType.KAFKA).findFirst().orElseThrow();
        assertThat(kafka.rangeStart()).isEqualTo(AppConstants.KAFKA_RANGE_START);
        assertThat(kafka.rangeEnd()).isEqualTo(AppConstants.KAFKA_RANGE_END);

        PortRangeResponse kafkaInternal = responses.stream()
                .filter(r -> r.portType() == PortType.KAFKA_INTERNAL).findFirst().orElseThrow();
        assertThat(kafkaInternal.rangeStart()).isEqualTo(AppConstants.KAFKA_INTERNAL_RANGE_START);
        assertThat(kafkaInternal.rangeEnd()).isEqualTo(AppConstants.KAFKA_INTERNAL_RANGE_END);

        PortRangeResponse zookeeper = responses.stream()
                .filter(r -> r.portType() == PortType.ZOOKEEPER).findFirst().orElseThrow();
        assertThat(zookeeper.rangeStart()).isEqualTo(AppConstants.ZOOKEEPER_RANGE_START);
        assertThat(zookeeper.rangeEnd()).isEqualTo(AppConstants.ZOOKEEPER_RANGE_END);

        PortRangeResponse grpc = responses.stream()
                .filter(r -> r.portType() == PortType.GRPC).findFirst().orElseThrow();
        assertThat(grpc.rangeStart()).isEqualTo(AppConstants.GRPC_RANGE_START);
        assertThat(grpc.rangeEnd()).isEqualTo(AppConstants.GRPC_RANGE_END);

        PortRangeResponse websocket = responses.stream()
                .filter(r -> r.portType() == PortType.WEBSOCKET).findFirst().orElseThrow();
        assertThat(websocket.rangeStart()).isEqualTo(AppConstants.WEBSOCKET_RANGE_START);
        assertThat(websocket.rangeEnd()).isEqualTo(AppConstants.WEBSOCKET_RANGE_END);

        PortRangeResponse debug = responses.stream()
                .filter(r -> r.portType() == PortType.DEBUG).findFirst().orElseThrow();
        assertThat(debug.rangeStart()).isEqualTo(AppConstants.DEBUG_RANGE_START);
        assertThat(debug.rangeEnd()).isEqualTo(AppConstants.DEBUG_RANGE_END);

        PortRangeResponse actuator = responses.stream()
                .filter(r -> r.portType() == PortType.ACTUATOR).findFirst().orElseThrow();
        assertThat(actuator.rangeStart()).isEqualTo(AppConstants.ACTUATOR_RANGE_START);
        assertThat(actuator.rangeEnd()).isEqualTo(AppConstants.ACTUATOR_RANGE_END);

        PortRangeResponse custom = responses.stream()
                .filter(r -> r.portType() == PortType.CUSTOM).findFirst().orElseThrow();
        assertThat(custom.rangeStart()).isEqualTo(9100);
        assertThat(custom.rangeEnd()).isEqualTo(9199);
    }

    // ──────────────────────────────────────────────────────────────
    // mapToResponse
    // ──────────────────────────────────────────────────────────────

    @Test
    void mapToResponse_includesServiceNameAndSlug() {
        UUID serviceId = UUID.randomUUID();
        UUID allocationId = UUID.randomUUID();
        ServiceRegistration service = buildService(serviceId, "api-gateway", "api-gateway");
        PortAllocation allocation = buildPortAllocation(allocationId, service, ENVIRONMENT, PortType.HTTP_API, 8080);
        allocation.setDescription("Gateway port");
        allocation.setProtocol("TCP");

        PortAllocationResponse response = portAllocationService.mapToResponse(allocation);

        assertThat(response.id()).isEqualTo(allocationId);
        assertThat(response.serviceId()).isEqualTo(serviceId);
        assertThat(response.serviceName()).isEqualTo("api-gateway");
        assertThat(response.serviceSlug()).isEqualTo("api-gateway");
        assertThat(response.environment()).isEqualTo(ENVIRONMENT);
        assertThat(response.portType()).isEqualTo(PortType.HTTP_API);
        assertThat(response.portNumber()).isEqualTo(8080);
        assertThat(response.protocol()).isEqualTo("TCP");
        assertThat(response.description()).isEqualTo("Gateway port");
        assertThat(response.isAutoAllocated()).isTrue();
        assertThat(response.allocatedByUserId()).isEqualTo(USER_ID);
        assertThat(response.createdAt()).isNotNull();
    }
}
