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
import com.codeops.registry.exception.NotFoundException;
import com.codeops.registry.exception.ValidationException;
import com.codeops.registry.repository.PortAllocationRepository;
import com.codeops.registry.repository.PortRangeRepository;
import com.codeops.registry.repository.ServiceRegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing port allocations, ranges, and the port map.
 *
 * <p>Implements the port allocation engine: auto-allocation from configurable ranges,
 * conflict detection, and the port map assembly. Ports are allocated within team-scoped
 * ranges per environment and port type.</p>
 *
 * @see PortAllocation
 * @see PortRange
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PortAllocationService {

    private final PortAllocationRepository portAllocationRepository;
    private final PortRangeRepository portRangeRepository;
    private final ServiceRegistrationRepository serviceRepository;

    /**
     * Auto-allocates the next available port for a service from the team's configured range.
     *
     * <p>Looks up the port range for the team, port type, and environment. Falls back to the
     * "local" environment range if no range exists for the specified environment. Iterates from
     * {@code rangeStart} to {@code rangeEnd} to find the first unused port number.</p>
     *
     * @param serviceId     the service to allocate a port for
     * @param environment   the target environment
     * @param portType      the type of port to allocate
     * @param currentUserId the user performing the allocation
     * @return the created port allocation response
     * @throws NotFoundException   if the service does not exist
     * @throws ValidationException if no range is configured or the range is full
     */
    @Transactional
    public PortAllocationResponse autoAllocate(UUID serviceId, String environment, PortType portType, UUID currentUserId) {
        ServiceRegistration service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new NotFoundException("ServiceRegistration", serviceId));

        UUID teamId = service.getTeamId();

        PortRange range = portRangeRepository.findByTeamIdAndPortTypeAndEnvironment(teamId, portType, environment)
                .or(() -> portRangeRepository.findByTeamIdAndPortTypeAndEnvironment(teamId, portType, "local"))
                .orElseThrow(() -> new ValidationException(
                        "No port range configured for type " + portType + " in environment " + environment
                                + ". Seed default ranges first."));

        List<PortAllocation> existing = portAllocationRepository
                .findByTeamIdAndEnvironmentAndPortType(teamId, environment, portType);
        Set<Integer> usedPorts = existing.stream()
                .map(PortAllocation::getPortNumber)
                .collect(Collectors.toSet());

        Integer allocatedPort = null;
        for (int port = range.getRangeStart(); port <= range.getRangeEnd(); port++) {
            if (!usedPorts.contains(port)) {
                allocatedPort = port;
                break;
            }
        }

        if (allocatedPort == null) {
            throw new ValidationException(
                    "No available ports in range " + range.getRangeStart() + "-" + range.getRangeEnd()
                            + " for type " + portType);
        }

        PortAllocation allocation = PortAllocation.builder()
                .service(service)
                .environment(environment)
                .portType(portType)
                .portNumber(allocatedPort)
                .protocol("TCP")
                .isAutoAllocated(true)
                .allocatedByUserId(currentUserId)
                .build();

        allocation = portAllocationRepository.save(allocation);
        log.info("Auto-allocated port {} ({}) for service {} in environment {}",
                allocatedPort, portType, service.getName(), environment);

        return mapToResponse(allocation);
    }

    /**
     * Auto-allocates ports for multiple port types in a single call.
     *
     * @param serviceId     the service to allocate ports for
     * @param environment   the target environment
     * @param portTypes     the list of port types to allocate
     * @param currentUserId the user performing the allocation
     * @return the list of created port allocation responses
     */
    @Transactional
    public List<PortAllocationResponse> autoAllocateAll(UUID serviceId, String environment,
                                                         List<PortType> portTypes, UUID currentUserId) {
        List<PortAllocationResponse> results = new ArrayList<>();
        for (PortType portType : portTypes) {
            results.add(autoAllocate(serviceId, environment, portType, currentUserId));
        }
        return results;
    }

    /**
     * Manually allocates a specific port number to a service.
     *
     * @param request       the allocation request containing service, environment, port details
     * @param currentUserId the user performing the allocation
     * @return the created port allocation response
     * @throws NotFoundException   if the service does not exist
     * @throws ValidationException if the port is already allocated within the team and environment
     */
    @Transactional
    public PortAllocationResponse manualAllocate(AllocatePortRequest request, UUID currentUserId) {
        ServiceRegistration service = serviceRepository.findById(request.serviceId())
                .orElseThrow(() -> new NotFoundException("ServiceRegistration", request.serviceId()));

        UUID teamId = service.getTeamId();

        Optional<PortAllocation> conflict = portAllocationRepository
                .findByTeamIdAndEnvironmentAndPortNumber(teamId, request.environment(), request.portNumber());
        if (conflict.isPresent()) {
            String ownerName = conflict.get().getService().getName();
            throw new ValidationException(
                    "Port " + request.portNumber() + " is already allocated to service " + ownerName
                            + " in environment " + request.environment());
        }

        PortAllocation allocation = PortAllocation.builder()
                .service(service)
                .environment(request.environment())
                .portType(request.portType())
                .portNumber(request.portNumber())
                .protocol(request.protocol() != null ? request.protocol() : "TCP")
                .description(request.description())
                .isAutoAllocated(false)
                .allocatedByUserId(currentUserId)
                .build();

        allocation = portAllocationRepository.save(allocation);
        log.info("Manually allocated port {} ({}) for service {} in environment {}",
                request.portNumber(), request.portType(), service.getName(), request.environment());

        return mapToResponse(allocation);
    }

    /**
     * Releases (deletes) a port allocation.
     *
     * @param allocationId the ID of the port allocation to release
     * @throws NotFoundException if the allocation does not exist
     */
    @Transactional
    public void releasePort(UUID allocationId) {
        PortAllocation allocation = portAllocationRepository.findById(allocationId)
                .orElseThrow(() -> new NotFoundException("PortAllocation", allocationId));

        portAllocationRepository.delete(allocation);
        log.info("Released port {} from service {}",
                allocation.getPortNumber(), allocation.getService().getName());
    }

    /**
     * Checks whether a specific port number is available in a team and environment.
     *
     * @param teamId      the team ID
     * @param portNumber  the port number to check
     * @param environment the environment to check in
     * @return the port availability check response
     */
    public PortCheckResponse checkAvailability(UUID teamId, int portNumber, String environment) {
        Optional<PortAllocation> existing = portAllocationRepository
                .findByTeamIdAndEnvironmentAndPortNumber(teamId, environment, portNumber);

        if (existing.isPresent()) {
            PortAllocation owner = existing.get();
            return new PortCheckResponse(
                    portNumber, environment, false,
                    owner.getService().getId(),
                    owner.getService().getName(),
                    owner.getPortType());
        }
        return new PortCheckResponse(portNumber, environment, true, null, null, null);
    }

    /**
     * Lists all port allocations for a service, optionally filtered by environment.
     *
     * @param serviceId   the service ID
     * @param environment the environment filter (null for all environments)
     * @return list of port allocation responses
     */
    public List<PortAllocationResponse> getPortsForService(UUID serviceId, String environment) {
        List<PortAllocation> allocations = (environment == null)
                ? portAllocationRepository.findByServiceId(serviceId)
                : portAllocationRepository.findByServiceIdAndEnvironment(serviceId, environment);
        return allocations.stream().map(this::mapToResponse).toList();
    }

    /**
     * Lists all port allocations for a team in a specific environment.
     *
     * @param teamId      the team ID
     * @param environment the environment
     * @return list of port allocation responses
     */
    public List<PortAllocationResponse> getPortsForTeam(UUID teamId, String environment) {
        return portAllocationRepository.findByTeamIdAndEnvironment(teamId, environment)
                .stream().map(this::mapToResponse).toList();
    }

    /**
     * Assembles the structured port map with ranges and their allocations.
     *
     * <p>For each configured port range, filters allocations that fall within that range,
     * computes capacity/allocated/available counts, and returns the complete map.</p>
     *
     * @param teamId      the team ID
     * @param environment the environment
     * @return the port map response
     */
    public PortMapResponse getPortMap(UUID teamId, String environment) {
        List<PortRange> ranges = portRangeRepository.findByTeamIdAndEnvironment(teamId, environment);
        if (ranges.isEmpty()) {
            ranges = portRangeRepository.findByTeamIdAndEnvironment(teamId, "local");
        }

        List<PortAllocation> allAllocations = portAllocationRepository
                .findByTeamIdAndEnvironment(teamId, environment);

        List<PortRangeWithAllocationsResponse> rangeResponses = new ArrayList<>();
        int totalAllocated = 0;
        int totalAvailable = 0;

        for (PortRange range : ranges) {
            List<PortAllocationResponse> matching = allAllocations.stream()
                    .filter(pa -> pa.getPortNumber() >= range.getRangeStart()
                            && pa.getPortNumber() <= range.getRangeEnd())
                    .map(this::mapToResponse)
                    .toList();

            int capacity = range.getRangeEnd() - range.getRangeStart() + 1;
            int allocated = matching.size();
            int available = capacity - allocated;

            rangeResponses.add(new PortRangeWithAllocationsResponse(
                    range.getPortType(), range.getRangeStart(), range.getRangeEnd(),
                    capacity, allocated, available, matching));

            totalAllocated += allocated;
            totalAvailable += available;
        }

        return new PortMapResponse(teamId, environment, rangeResponses, totalAllocated, totalAvailable);
    }

    /**
     * Detects port conflicts within a team (same port number allocated to multiple services).
     *
     * @param teamId the team ID
     * @return list of port conflict responses (empty if no conflicts)
     */
    public List<PortConflictResponse> detectConflicts(UUID teamId) {
        List<Object[]> conflictRows = portAllocationRepository.findConflictingPorts(teamId);
        List<PortConflictResponse> conflicts = new ArrayList<>();

        for (Object[] row : conflictRows) {
            Integer portNumber = (Integer) row[0];
            String environment = (String) row[1];

            List<PortAllocation> conflicting = portAllocationRepository
                    .findByTeamIdAndEnvironment(teamId, environment)
                    .stream()
                    .filter(pa -> pa.getPortNumber().equals(portNumber))
                    .toList();

            conflicts.add(new PortConflictResponse(
                    portNumber, environment,
                    conflicting.stream().map(this::mapToResponse).toList()));
        }

        return conflicts;
    }

    /**
     * Lists all port ranges for a team.
     *
     * @param teamId the team ID
     * @return list of port range responses
     */
    public List<PortRangeResponse> getPortRanges(UUID teamId) {
        return portRangeRepository.findByTeamId(teamId).stream()
                .map(this::mapToRangeResponse)
                .toList();
    }

    /**
     * Updates a port range's start and end values.
     *
     * @param rangeId the port range ID
     * @param request the update request
     * @return the updated port range response
     * @throws NotFoundException   if the range does not exist
     * @throws ValidationException if rangeStart >= rangeEnd or shrinking would exclude existing allocations
     */
    @Transactional
    public PortRangeResponse updatePortRange(UUID rangeId, UpdatePortRangeRequest request) {
        PortRange range = portRangeRepository.findById(rangeId)
                .orElseThrow(() -> new NotFoundException("PortRange", rangeId));

        if (request.rangeStart() >= request.rangeEnd()) {
            throw new ValidationException("Range start must be less than range end");
        }

        List<PortAllocation> existing = portAllocationRepository
                .findByTeamIdAndEnvironmentAndPortType(range.getTeamId(), range.getEnvironment(), range.getPortType());
        for (PortAllocation pa : existing) {
            if (pa.getPortNumber() < request.rangeStart() || pa.getPortNumber() > request.rangeEnd()) {
                throw new ValidationException(
                        "Cannot shrink range: port " + pa.getPortNumber()
                                + " (service " + pa.getService().getName()
                                + ") would fall outside the new range "
                                + request.rangeStart() + "-" + request.rangeEnd());
            }
        }

        range.setRangeStart(request.rangeStart());
        range.setRangeEnd(request.rangeEnd());
        if (request.description() != null) {
            range.setDescription(request.description());
        }

        range = portRangeRepository.save(range);
        return mapToRangeResponse(range);
    }

    /**
     * Seeds default port ranges for a team using values from {@link AppConstants}.
     *
     * <p>If ranges already exist for the team, returns the existing ranges without modification.
     * Creates one range per {@link PortType} for the specified environment.</p>
     *
     * @param teamId      the team ID
     * @param environment the environment to seed ranges for
     * @return the list of created or existing port range responses
     */
    @Transactional
    public List<PortRangeResponse> seedDefaultRanges(UUID teamId, String environment) {
        if (portRangeRepository.existsByTeamId(teamId)) {
            return portRangeRepository.findByTeamId(teamId).stream()
                    .map(this::mapToRangeResponse)
                    .toList();
        }

        List<PortRange> ranges = new ArrayList<>();
        ranges.add(buildRange(teamId, environment, PortType.HTTP_API, AppConstants.HTTP_API_RANGE_START, AppConstants.HTTP_API_RANGE_END));
        ranges.add(buildRange(teamId, environment, PortType.FRONTEND_DEV, AppConstants.FRONTEND_DEV_RANGE_START, AppConstants.FRONTEND_DEV_RANGE_END));
        ranges.add(buildRange(teamId, environment, PortType.DATABASE, AppConstants.DATABASE_RANGE_START, AppConstants.DATABASE_RANGE_END));
        ranges.add(buildRange(teamId, environment, PortType.REDIS, AppConstants.REDIS_RANGE_START, AppConstants.REDIS_RANGE_END));
        ranges.add(buildRange(teamId, environment, PortType.KAFKA, AppConstants.KAFKA_RANGE_START, AppConstants.KAFKA_RANGE_END));
        ranges.add(buildRange(teamId, environment, PortType.KAFKA_INTERNAL, AppConstants.KAFKA_INTERNAL_RANGE_START, AppConstants.KAFKA_INTERNAL_RANGE_END));
        ranges.add(buildRange(teamId, environment, PortType.ZOOKEEPER, AppConstants.ZOOKEEPER_RANGE_START, AppConstants.ZOOKEEPER_RANGE_END));
        ranges.add(buildRange(teamId, environment, PortType.GRPC, AppConstants.GRPC_RANGE_START, AppConstants.GRPC_RANGE_END));
        ranges.add(buildRange(teamId, environment, PortType.WEBSOCKET, AppConstants.WEBSOCKET_RANGE_START, AppConstants.WEBSOCKET_RANGE_END));
        ranges.add(buildRange(teamId, environment, PortType.DEBUG, AppConstants.DEBUG_RANGE_START, AppConstants.DEBUG_RANGE_END));
        ranges.add(buildRange(teamId, environment, PortType.ACTUATOR, AppConstants.ACTUATOR_RANGE_START, AppConstants.ACTUATOR_RANGE_END));
        ranges.add(buildRange(teamId, environment, PortType.CUSTOM, 9100, 9199));

        ranges = portRangeRepository.saveAll(ranges);
        log.info("Seeded {} default port ranges for team {} in environment {}", ranges.size(), teamId, environment);

        return ranges.stream().map(this::mapToRangeResponse).toList();
    }

    /**
     * Maps a {@link PortAllocation} entity to a {@link PortAllocationResponse}.
     *
     * @param entity the port allocation entity
     * @return the mapped response including service name and slug
     */
    PortAllocationResponse mapToResponse(PortAllocation entity) {
        ServiceRegistration svc = entity.getService();
        return new PortAllocationResponse(
                entity.getId(),
                svc.getId(),
                svc.getName(),
                svc.getSlug(),
                entity.getEnvironment(),
                entity.getPortType(),
                entity.getPortNumber(),
                entity.getProtocol(),
                entity.getDescription(),
                entity.getIsAutoAllocated(),
                entity.getAllocatedByUserId(),
                entity.getCreatedAt());
    }

    private PortRangeResponse mapToRangeResponse(PortRange entity) {
        return new PortRangeResponse(
                entity.getId(),
                entity.getTeamId(),
                entity.getPortType(),
                entity.getRangeStart(),
                entity.getRangeEnd(),
                entity.getEnvironment(),
                entity.getDescription());
    }

    private PortRange buildRange(UUID teamId, String environment, PortType portType, int start, int end) {
        return PortRange.builder()
                .teamId(teamId)
                .environment(environment)
                .portType(portType)
                .rangeStart(start)
                .rangeEnd(end)
                .build();
    }
}
