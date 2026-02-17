package com.codeops.registry.service;

import com.codeops.registry.config.AppConstants;
import com.codeops.registry.dto.request.CreateDependencyRequest;
import com.codeops.registry.dto.response.*;
import com.codeops.registry.entity.ServiceDependency;
import com.codeops.registry.entity.ServiceRegistration;
import com.codeops.registry.entity.enums.HealthStatus;
import com.codeops.registry.exception.NotFoundException;
import com.codeops.registry.exception.ValidationException;
import com.codeops.registry.repository.ServiceDependencyRepository;
import com.codeops.registry.repository.ServiceRegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service for managing the directed dependency graph between services.
 *
 * <p>Provides dependency CRUD, graph visualization data, BFS impact analysis,
 * Kahn's topological sort for startup ordering, and DFS-based cycle detection.
 * Cycle prevention is enforced when adding new edges via {@link #hasPath}.</p>
 *
 * @see ServiceDependency
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DependencyGraphService {

    private final ServiceDependencyRepository dependencyRepository;
    private final ServiceRegistrationRepository serviceRepository;

    /**
     * Creates a directed dependency edge between two services.
     *
     * <p>Validates that both services exist and belong to the same team, that the
     * source is not the same as the target, that a duplicate edge does not exist,
     * that the per-service dependency limit is not exceeded, and that adding the
     * edge would not create a cycle in the dependency graph.</p>
     *
     * @param request the dependency creation request
     * @return the created dependency response
     * @throws NotFoundException   if either service does not exist
     * @throws ValidationException if any validation rule is violated
     */
    @Transactional
    public ServiceDependencyResponse createDependency(CreateDependencyRequest request) {
        if (request.sourceServiceId().equals(request.targetServiceId())) {
            throw new ValidationException("A service cannot depend on itself");
        }

        ServiceRegistration source = serviceRepository.findById(request.sourceServiceId())
                .orElseThrow(() -> new NotFoundException("ServiceRegistration", request.sourceServiceId()));

        ServiceRegistration target = serviceRepository.findById(request.targetServiceId())
                .orElseThrow(() -> new NotFoundException("ServiceRegistration", request.targetServiceId()));

        if (!source.getTeamId().equals(target.getTeamId())) {
            throw new ValidationException(
                    "Cannot create dependency between services in different teams");
        }

        if (dependencyRepository.findBySourceServiceIdAndTargetServiceIdAndDependencyType(
                request.sourceServiceId(), request.targetServiceId(), request.dependencyType()).isPresent()) {
            throw new ValidationException(
                    "Dependency already exists from " + source.getName() + " to " + target.getName()
                            + " with type " + request.dependencyType());
        }

        long depCount = dependencyRepository.countBySourceServiceId(request.sourceServiceId());
        if (depCount >= AppConstants.MAX_DEPENDENCIES_PER_SERVICE) {
            throw new ValidationException(
                    "Service " + source.getName() + " has reached the maximum of "
                            + AppConstants.MAX_DEPENDENCIES_PER_SERVICE + " dependencies");
        }

        // Cycle detection: check if adding target → source path would create a cycle
        List<ServiceDependency> teamDeps = dependencyRepository.findAllByTeamId(source.getTeamId());
        if (hasPath(request.targetServiceId(), request.sourceServiceId(), teamDeps)) {
            throw new ValidationException(
                    "Adding this dependency would create a cycle in the dependency graph");
        }

        ServiceDependency entity = ServiceDependency.builder()
                .sourceService(source)
                .targetService(target)
                .dependencyType(request.dependencyType())
                .description(request.description())
                .isRequired(request.isRequired() != null ? request.isRequired() : true)
                .targetEndpoint(request.targetEndpoint())
                .build();

        entity = dependencyRepository.save(entity);
        log.info("Dependency created: {} → {} ({})", source.getName(), target.getName(), request.dependencyType());

        return mapToResponse(entity);
    }

    /**
     * Removes a dependency edge by ID.
     *
     * @param dependencyId the dependency ID
     * @throws NotFoundException if the dependency does not exist
     */
    @Transactional
    public void removeDependency(UUID dependencyId) {
        ServiceDependency entity = dependencyRepository.findById(dependencyId)
                .orElseThrow(() -> new NotFoundException("ServiceDependency", dependencyId));

        dependencyRepository.delete(entity);
        log.info("Dependency removed: {} → {} ({})",
                entity.getSourceService().getName(),
                entity.getTargetService().getName(),
                entity.getDependencyType());
    }

    /**
     * Builds the complete dependency graph for a team for visualization.
     *
     * <p>Returns all services as nodes and all dependencies as edges.</p>
     *
     * @param teamId the team ID
     * @return the dependency graph response with nodes and edges
     */
    public DependencyGraphResponse getDependencyGraph(UUID teamId) {
        List<ServiceRegistration> services = serviceRepository.findByTeamId(teamId);
        List<ServiceDependency> dependencies = dependencyRepository.findAllByTeamId(teamId);

        List<DependencyNodeResponse> nodes = services.stream()
                .map(svc -> new DependencyNodeResponse(
                        svc.getId(),
                        svc.getName(),
                        svc.getSlug(),
                        svc.getServiceType(),
                        svc.getStatus(),
                        svc.getLastHealthStatus() != null ? svc.getLastHealthStatus() : HealthStatus.UNKNOWN))
                .toList();

        List<DependencyEdgeResponse> edges = dependencies.stream()
                .map(dep -> new DependencyEdgeResponse(
                        dep.getSourceService().getId(),
                        dep.getTargetService().getId(),
                        dep.getDependencyType(),
                        dep.getIsRequired(),
                        dep.getTargetEndpoint()))
                .toList();

        return new DependencyGraphResponse(teamId, nodes, edges);
    }

    /**
     * Performs BFS impact analysis from a source service.
     *
     * <p>Starting from the given service, follows reverse dependency edges (who depends
     * on me) to find all transitively impacted services. Each impacted service is annotated
     * with its BFS depth, the connection type, and whether the dependency is required.</p>
     *
     * @param serviceId the source service ID
     * @return the impact analysis response
     * @throws NotFoundException if the service does not exist
     */
    public ImpactAnalysisResponse getImpactAnalysis(UUID serviceId) {
        ServiceRegistration source = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new NotFoundException("ServiceRegistration", serviceId));

        List<ServiceDependency> teamDeps = dependencyRepository.findAllByTeamId(source.getTeamId());

        // Build reverse adjacency: targetId → list of (sourceService, dep)
        Map<UUID, List<ServiceDependency>> reverseAdj = new HashMap<>();
        for (ServiceDependency dep : teamDeps) {
            reverseAdj.computeIfAbsent(dep.getTargetService().getId(), k -> new ArrayList<>()).add(dep);
        }

        // BFS from source service following reverse edges
        List<ImpactedServiceResponse> impacted = new ArrayList<>();
        Set<UUID> visited = new HashSet<>();
        visited.add(serviceId);

        Queue<UUID> queue = new LinkedList<>();
        Map<UUID, Integer> depthMap = new HashMap<>();

        // Seed BFS with direct dependents
        List<ServiceDependency> directDependents = reverseAdj.getOrDefault(serviceId, List.of());
        for (ServiceDependency dep : directDependents) {
            UUID dependentId = dep.getSourceService().getId();
            if (visited.add(dependentId)) {
                queue.add(dependentId);
                depthMap.put(dependentId, 1);
                impacted.add(new ImpactedServiceResponse(
                        dependentId,
                        dep.getSourceService().getName(),
                        dep.getSourceService().getSlug(),
                        1,
                        dep.getDependencyType(),
                        dep.getIsRequired()));
            }
        }

        // Continue BFS
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            int currentDepth = depthMap.get(current);

            List<ServiceDependency> dependents = reverseAdj.getOrDefault(current, List.of());
            for (ServiceDependency dep : dependents) {
                UUID dependentId = dep.getSourceService().getId();
                if (visited.add(dependentId)) {
                    int newDepth = currentDepth + 1;
                    queue.add(dependentId);
                    depthMap.put(dependentId, newDepth);
                    impacted.add(new ImpactedServiceResponse(
                            dependentId,
                            dep.getSourceService().getName(),
                            dep.getSourceService().getSlug(),
                            newDepth,
                            dep.getDependencyType(),
                            dep.getIsRequired()));
                }
            }
        }

        return new ImpactAnalysisResponse(
                serviceId,
                source.getName(),
                impacted,
                impacted.size());
    }

    /**
     * Computes topological startup order using Kahn's algorithm.
     *
     * <p>Services with no dependencies start first. Returns service IDs in the order
     * they should be started. If the graph has a cycle (which should be prevented by
     * {@link #createDependency}), returns a partial order of non-cyclic services.</p>
     *
     * @param teamId the team ID
     * @return list of dependency node responses in topological (startup) order
     */
    public List<DependencyNodeResponse> getStartupOrder(UUID teamId) {
        List<ServiceRegistration> services = serviceRepository.findByTeamId(teamId);
        List<ServiceDependency> dependencies = dependencyRepository.findAllByTeamId(teamId);

        // Build adjacency list and in-degree map
        // Edge direction: source depends on target, so target must start before source
        // This means edges go target → source in the startup graph
        Map<UUID, List<UUID>> adj = new HashMap<>();
        Map<UUID, Integer> inDegree = new HashMap<>();

        for (ServiceRegistration svc : services) {
            adj.put(svc.getId(), new ArrayList<>());
            inDegree.put(svc.getId(), 0);
        }

        for (ServiceDependency dep : dependencies) {
            UUID targetId = dep.getTargetService().getId();
            UUID sourceId = dep.getSourceService().getId();
            // target must start before source
            adj.get(targetId).add(sourceId);
            inDegree.merge(sourceId, 1, Integer::sum);
        }

        // Kahn's algorithm
        Queue<UUID> queue = new LinkedList<>();
        for (Map.Entry<UUID, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<UUID> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            order.add(current);
            for (UUID neighbor : adj.getOrDefault(current, List.of())) {
                int newDegree = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, newDegree);
                if (newDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }

        // Build lookup for quick service resolution
        Map<UUID, ServiceRegistration> serviceMap = new HashMap<>();
        for (ServiceRegistration svc : services) {
            serviceMap.put(svc.getId(), svc);
        }

        return order.stream()
                .map(serviceMap::get)
                .filter(Objects::nonNull)
                .map(svc -> new DependencyNodeResponse(
                        svc.getId(),
                        svc.getName(),
                        svc.getSlug(),
                        svc.getServiceType(),
                        svc.getStatus(),
                        svc.getLastHealthStatus() != null ? svc.getLastHealthStatus() : HealthStatus.UNKNOWN))
                .toList();
    }

    /**
     * Detects cycles in the team's dependency graph using DFS with three-color marking.
     *
     * <p>WHITE (unvisited) → GRAY (in progress) → BLACK (complete). A back-edge to a
     * GRAY node indicates a cycle. Returns the list of service IDs involved in cycles.</p>
     *
     * @param teamId the team ID
     * @return list of service IDs participating in cycles (empty if acyclic)
     */
    public List<UUID> detectCycles(UUID teamId) {
        List<ServiceDependency> dependencies = dependencyRepository.findAllByTeamId(teamId);
        List<ServiceRegistration> services = serviceRepository.findByTeamId(teamId);

        // Build adjacency: source → targets (source depends on targets)
        Map<UUID, List<UUID>> adj = new HashMap<>();
        Set<UUID> allNodes = new HashSet<>();

        for (ServiceRegistration svc : services) {
            allNodes.add(svc.getId());
            adj.putIfAbsent(svc.getId(), new ArrayList<>());
        }

        for (ServiceDependency dep : dependencies) {
            adj.computeIfAbsent(dep.getSourceService().getId(), k -> new ArrayList<>())
                    .add(dep.getTargetService().getId());
        }

        // Three-color DFS
        // 0 = WHITE (unvisited), 1 = GRAY (in progress), 2 = BLACK (complete)
        Map<UUID, Integer> color = new HashMap<>();
        for (UUID node : allNodes) {
            color.put(node, 0);
        }

        Set<UUID> cycleNodes = new LinkedHashSet<>();

        for (UUID node : allNodes) {
            if (color.get(node) == 0) {
                dfs(node, adj, color, cycleNodes, new ArrayList<>());
            }
        }

        return new ArrayList<>(cycleNodes);
    }

    /**
     * Checks if a directed path exists from {@code from} to {@code to} in the dependency graph.
     *
     * <p>Used to detect if adding a new edge would create a cycle: before adding
     * source → target, check if target → source path already exists.</p>
     *
     * @param from     the starting node
     * @param to       the target node
     * @param teamDeps all dependencies in the team
     * @return true if a path from {@code from} to {@code to} exists
     */
    boolean hasPath(UUID from, UUID to, List<ServiceDependency> teamDeps) {
        // Build adjacency: source → targets
        Map<UUID, List<UUID>> adj = new HashMap<>();
        for (ServiceDependency dep : teamDeps) {
            adj.computeIfAbsent(dep.getSourceService().getId(), k -> new ArrayList<>())
                    .add(dep.getTargetService().getId());
        }

        // BFS from 'from' to see if we can reach 'to'
        Set<UUID> visited = new HashSet<>();
        Queue<UUID> queue = new LinkedList<>();
        queue.add(from);
        visited.add(from);

        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            if (current.equals(to)) {
                return true;
            }
            for (UUID neighbor : adj.getOrDefault(current, List.of())) {
                if (visited.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }

        return false;
    }

    // ──────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────

    /**
     * DFS helper for cycle detection with three-color marking.
     */
    private void dfs(UUID node, Map<UUID, List<UUID>> adj, Map<UUID, Integer> color,
                     Set<UUID> cycleNodes, List<UUID> path) {
        color.put(node, 1); // GRAY
        path.add(node);

        for (UUID neighbor : adj.getOrDefault(node, List.of())) {
            if (color.get(neighbor) == null) {
                continue; // Node not in our graph (shouldn't happen but safety check)
            }
            if (color.get(neighbor) == 1) {
                // Back edge found — collect cycle nodes from the cycle start to current
                int cycleStart = path.indexOf(neighbor);
                for (int i = cycleStart; i < path.size(); i++) {
                    cycleNodes.add(path.get(i));
                }
            } else if (color.get(neighbor) == 0) {
                dfs(neighbor, adj, color, cycleNodes, path);
            }
        }

        path.remove(path.size() - 1);
        color.put(node, 2); // BLACK
    }

    private ServiceDependencyResponse mapToResponse(ServiceDependency dep) {
        return new ServiceDependencyResponse(
                dep.getId(),
                dep.getSourceService().getId(),
                dep.getSourceService().getName(),
                dep.getSourceService().getSlug(),
                dep.getTargetService().getId(),
                dep.getTargetService().getName(),
                dep.getTargetService().getSlug(),
                dep.getDependencyType(),
                dep.getDescription(),
                dep.getIsRequired(),
                dep.getTargetEndpoint(),
                dep.getCreatedAt());
    }
}
