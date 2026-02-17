package com.codeops.registry.service;

import com.codeops.registry.config.AppConstants;
import com.codeops.registry.dto.response.*;
import com.codeops.registry.entity.ServiceDependency;
import com.codeops.registry.entity.ServiceRegistration;
import com.codeops.registry.entity.Solution;
import com.codeops.registry.entity.SolutionMember;
import com.codeops.registry.entity.enums.HealthStatus;
import com.codeops.registry.entity.enums.ServiceType;
import com.codeops.registry.exception.NotFoundException;
import com.codeops.registry.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for building topology views of a team's service ecosystem.
 *
 * <p>Provides full topology maps with nodes, edges, solution groupings, layer
 * classification, and aggregate statistics. Supports team-wide, solution-scoped,
 * and neighborhood-scoped views. Does not duplicate dependency graph logic from
 * {@link DependencyGraphService} — instead builds visualization-oriented
 * representations on top of the raw entity data.</p>
 *
 * @see TopologyResponse
 * @see DependencyGraphService
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TopologyService {

    private final ServiceRegistrationRepository serviceRepository;
    private final ServiceDependencyRepository dependencyRepository;
    private final SolutionRepository solutionRepository;
    private final SolutionMemberRepository solutionMemberRepository;
    private final PortAllocationRepository portAllocationRepository;

    /**
     * Builds a complete ecosystem topology map for a team.
     *
     * <p>Assembles all services as nodes (with port counts, dependency counts,
     * solution memberships, and layer classification), all dependencies as edges,
     * solution groupings, layer groupings, and aggregate statistics.</p>
     *
     * @param teamId the team ID
     * @return the complete topology response
     */
    public TopologyResponse getTopology(UUID teamId) {
        List<ServiceRegistration> services = serviceRepository.findByTeamId(teamId);
        List<ServiceDependency> dependencies = dependencyRepository.findAllByTeamId(teamId);
        List<Solution> solutions = solutionRepository.findByTeamId(teamId);

        // Load all solution memberships
        Map<UUID, List<UUID>> serviceSolutionMap = buildServiceSolutionMap(solutions);

        // Build upstream/downstream count maps
        Map<UUID, Integer> upstreamCounts = new HashMap<>();
        Map<UUID, Integer> downstreamCounts = new HashMap<>();
        for (ServiceDependency dep : dependencies) {
            upstreamCounts.merge(dep.getSourceService().getId(), 1, Integer::sum);
            downstreamCounts.merge(dep.getTargetService().getId(), 1, Integer::sum);
        }

        // Build nodes
        Set<UUID> allServiceIds = services.stream()
                .map(ServiceRegistration::getId)
                .collect(Collectors.toSet());
        List<TopologyNodeResponse> nodes = services.stream()
                .map(svc -> buildTopologyNode(svc, upstreamCounts, downstreamCounts, serviceSolutionMap))
                .toList();

        // Build edges
        List<DependencyEdgeResponse> edges = dependencies.stream()
                .map(dep -> new DependencyEdgeResponse(
                        dep.getSourceService().getId(),
                        dep.getTargetService().getId(),
                        dep.getDependencyType(),
                        dep.getIsRequired(),
                        dep.getTargetEndpoint()))
                .toList();

        // Build solution groups
        List<TopologySolutionGroup> solutionGroups = buildSolutionGroups(solutions);

        // Build layers
        List<TopologyLayerResponse> layers = buildLayers(nodes);

        // Build stats
        TopologyStatsResponse stats = buildStats(nodes, dependencies, solutions, allServiceIds);

        return new TopologyResponse(teamId, nodes, edges, solutionGroups, layers, stats);
    }

    /**
     * Builds a topology view filtered to a specific solution's member services.
     *
     * <p>Only includes services that are members of the solution and dependencies
     * where both source and target are members. Layers and stats are scoped accordingly.</p>
     *
     * @param solutionId the solution ID
     * @return the solution-scoped topology response
     * @throws NotFoundException if the solution does not exist
     */
    public TopologyResponse getTopologyForSolution(UUID solutionId) {
        Solution solution = solutionRepository.findById(solutionId)
                .orElseThrow(() -> new NotFoundException("Solution", solutionId));

        List<SolutionMember> members = solutionMemberRepository.findBySolutionId(solutionId);
        Set<UUID> memberServiceIds = members.stream()
                .map(m -> m.getService().getId())
                .collect(Collectors.toSet());

        List<ServiceRegistration> services = memberServiceIds.isEmpty()
                ? List.of()
                : serviceRepository.findByTeamIdAndIdIn(solution.getTeamId(), new ArrayList<>(memberServiceIds));

        List<ServiceDependency> allDeps = dependencyRepository.findAllByTeamId(solution.getTeamId());
        List<ServiceDependency> filteredDeps = allDeps.stream()
                .filter(dep -> memberServiceIds.contains(dep.getSourceService().getId())
                        && memberServiceIds.contains(dep.getTargetService().getId()))
                .toList();

        // Build upstream/downstream count maps (scoped to solution)
        Map<UUID, Integer> upstreamCounts = new HashMap<>();
        Map<UUID, Integer> downstreamCounts = new HashMap<>();
        for (ServiceDependency dep : filteredDeps) {
            upstreamCounts.merge(dep.getSourceService().getId(), 1, Integer::sum);
            downstreamCounts.merge(dep.getTargetService().getId(), 1, Integer::sum);
        }

        // Build service-solution map (scoped)
        Map<UUID, List<UUID>> serviceSolutionMap = new HashMap<>();
        for (UUID svcId : memberServiceIds) {
            serviceSolutionMap.put(svcId, List.of(solutionId));
        }

        List<TopologyNodeResponse> nodes = services.stream()
                .map(svc -> buildTopologyNode(svc, upstreamCounts, downstreamCounts, serviceSolutionMap))
                .toList();

        List<DependencyEdgeResponse> edges = filteredDeps.stream()
                .map(dep -> new DependencyEdgeResponse(
                        dep.getSourceService().getId(),
                        dep.getTargetService().getId(),
                        dep.getDependencyType(),
                        dep.getIsRequired(),
                        dep.getTargetEndpoint()))
                .toList();

        List<TopologySolutionGroup> solutionGroups = List.of(new TopologySolutionGroup(
                solution.getId(), solution.getName(), solution.getSlug(),
                solution.getStatus(), members.size(),
                new ArrayList<>(memberServiceIds)));

        List<TopologyLayerResponse> layers = buildLayers(nodes);
        TopologyStatsResponse stats = buildStats(nodes, filteredDeps, List.of(solution), memberServiceIds);

        return new TopologyResponse(solution.getTeamId(), nodes, edges, solutionGroups, layers, stats);
    }

    /**
     * Builds a topology view for a service and its dependency neighborhood.
     *
     * <p>Performs BFS outward in both directions (upstream and downstream) from the
     * center service up to {@code depth} hops. The depth is capped at
     * {@link AppConstants#TOPOLOGY_MAX_NEIGHBORHOOD_DEPTH}.</p>
     *
     * @param serviceId the center service ID
     * @param depth     the maximum number of hops (capped at 3)
     * @return the neighborhood topology response
     * @throws NotFoundException if the center service does not exist
     */
    public TopologyResponse getServiceNeighborhood(UUID serviceId, int depth) {
        ServiceRegistration center = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new NotFoundException("ServiceRegistration", serviceId));

        int cappedDepth = Math.min(Math.max(depth, 1), AppConstants.TOPOLOGY_MAX_NEIGHBORHOOD_DEPTH);

        List<ServiceDependency> allDeps = dependencyRepository.findAllByTeamId(center.getTeamId());

        // Build adjacency maps for both directions
        Map<UUID, List<UUID>> upstreamAdj = new HashMap<>();   // source → targets
        Map<UUID, List<UUID>> downstreamAdj = new HashMap<>(); // target → sources
        for (ServiceDependency dep : allDeps) {
            upstreamAdj.computeIfAbsent(dep.getSourceService().getId(), k -> new ArrayList<>())
                    .add(dep.getTargetService().getId());
            downstreamAdj.computeIfAbsent(dep.getTargetService().getId(), k -> new ArrayList<>())
                    .add(dep.getSourceService().getId());
        }

        // BFS in both directions
        Set<UUID> collectedIds = new LinkedHashSet<>();
        collectedIds.add(serviceId);

        Queue<UUID> queue = new LinkedList<>();
        Map<UUID, Integer> depthMap = new HashMap<>();
        queue.add(serviceId);
        depthMap.put(serviceId, 0);

        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            int currentDepth = depthMap.get(current);
            if (currentDepth >= cappedDepth) {
                continue;
            }

            // Follow upstream direction (services this one depends on)
            for (UUID neighbor : upstreamAdj.getOrDefault(current, List.of())) {
                if (collectedIds.add(neighbor)) {
                    depthMap.put(neighbor, currentDepth + 1);
                    queue.add(neighbor);
                }
            }
            // Follow downstream direction (services that depend on this one)
            for (UUID neighbor : downstreamAdj.getOrDefault(current, List.of())) {
                if (collectedIds.add(neighbor)) {
                    depthMap.put(neighbor, currentDepth + 1);
                    queue.add(neighbor);
                }
            }
        }

        // Load collected services
        List<ServiceRegistration> services = collectedIds.isEmpty()
                ? List.of()
                : serviceRepository.findByTeamIdAndIdIn(center.getTeamId(), new ArrayList<>(collectedIds));

        // Filter dependencies to subgraph
        List<ServiceDependency> filteredDeps = allDeps.stream()
                .filter(dep -> collectedIds.contains(dep.getSourceService().getId())
                        && collectedIds.contains(dep.getTargetService().getId()))
                .toList();

        Map<UUID, Integer> upstreamCounts = new HashMap<>();
        Map<UUID, Integer> downstreamCounts = new HashMap<>();
        for (ServiceDependency dep : filteredDeps) {
            upstreamCounts.merge(dep.getSourceService().getId(), 1, Integer::sum);
            downstreamCounts.merge(dep.getTargetService().getId(), 1, Integer::sum);
        }

        List<Solution> solutions = solutionRepository.findByTeamId(center.getTeamId());
        Map<UUID, List<UUID>> serviceSolutionMap = buildServiceSolutionMap(solutions);

        List<TopologyNodeResponse> nodes = services.stream()
                .map(svc -> buildTopologyNode(svc, upstreamCounts, downstreamCounts, serviceSolutionMap))
                .toList();

        List<DependencyEdgeResponse> edges = filteredDeps.stream()
                .map(dep -> new DependencyEdgeResponse(
                        dep.getSourceService().getId(),
                        dep.getTargetService().getId(),
                        dep.getDependencyType(),
                        dep.getIsRequired(),
                        dep.getTargetEndpoint()))
                .toList();

        // Solution groups for services in the neighborhood
        List<TopologySolutionGroup> solutionGroups = solutions.stream()
                .filter(sol -> {
                    List<SolutionMember> members = solutionMemberRepository.findBySolutionId(sol.getId());
                    return members.stream().anyMatch(m -> collectedIds.contains(m.getService().getId()));
                })
                .map(sol -> {
                    List<SolutionMember> members = solutionMemberRepository.findBySolutionId(sol.getId());
                    List<UUID> svcIds = members.stream()
                            .map(m -> m.getService().getId())
                            .filter(collectedIds::contains)
                            .toList();
                    return new TopologySolutionGroup(
                            sol.getId(), sol.getName(), sol.getSlug(),
                            sol.getStatus(), svcIds.size(), svcIds);
                })
                .filter(grp -> grp.memberCount() > 0)
                .toList();

        List<TopologyLayerResponse> layers = buildLayers(nodes);
        TopologyStatsResponse stats = buildStats(nodes, filteredDeps,
                solutions.stream().filter(sol -> solutionGroups.stream()
                        .anyMatch(g -> g.solutionId().equals(sol.getId()))).toList(),
                collectedIds);

        return new TopologyResponse(center.getTeamId(), nodes, edges, solutionGroups, layers, stats);
    }

    /**
     * Computes quick aggregate statistics for a team's ecosystem without building
     * the full topology graph.
     *
     * @param teamId the team ID
     * @return the topology stats response
     */
    public TopologyStatsResponse getEcosystemStats(UUID teamId) {
        List<ServiceRegistration> services = serviceRepository.findByTeamId(teamId);
        List<ServiceDependency> dependencies = dependencyRepository.findAllByTeamId(teamId);
        List<Solution> solutions = solutionRepository.findByTeamId(teamId);

        Set<UUID> allServiceIds = services.stream()
                .map(ServiceRegistration::getId)
                .collect(Collectors.toSet());

        Set<UUID> sourcesSet = new HashSet<>();
        Set<UUID> targetsSet = new HashSet<>();
        for (ServiceDependency dep : dependencies) {
            sourcesSet.add(dep.getSourceService().getId());
            targetsSet.add(dep.getTargetService().getId());
        }

        Set<UUID> hasAnyDep = new HashSet<>(sourcesSet);
        hasAnyDep.addAll(targetsSet);

        Set<UUID> inAnySolution = new HashSet<>();
        for (Solution sol : solutions) {
            List<SolutionMember> members = solutionMemberRepository.findBySolutionId(sol.getId());
            for (SolutionMember m : members) {
                inAnySolution.add(m.getService().getId());
            }
        }

        int servicesWithNoDeps = (int) allServiceIds.stream()
                .filter(id -> !sourcesSet.contains(id))
                .count();
        int servicesWithNoConsumers = (int) allServiceIds.stream()
                .filter(id -> !targetsSet.contains(id))
                .count();
        int orphaned = (int) allServiceIds.stream()
                .filter(id -> !hasAnyDep.contains(id) && !inAnySolution.contains(id))
                .count();

        int maxDepth = computeMaxDepth(dependencies, allServiceIds);

        return new TopologyStatsResponse(
                services.size(),
                dependencies.size(),
                solutions.size(),
                servicesWithNoDeps,
                servicesWithNoConsumers,
                orphaned,
                maxDepth);
    }

    // ──────────────────────────────────────────────
    // Package-private helpers
    // ──────────────────────────────────────────────

    /**
     * Classifies a service type into an architectural layer.
     *
     * @param serviceType the service type
     * @return the layer name constant
     */
    String classifyLayer(ServiceType serviceType) {
        return switch (serviceType) {
            case DATABASE_SERVICE, CACHE_SERVICE, MESSAGE_BROKER -> AppConstants.TOPOLOGY_LAYER_INFRASTRUCTURE;
            case SPRING_BOOT_API, EXPRESS_API, FASTAPI, DOTNET_API, GO_API, WORKER, MCP_SERVER ->
                    AppConstants.TOPOLOGY_LAYER_BACKEND;
            case REACT_SPA, VUE_SPA, NEXT_JS, FLUTTER_WEB, FLUTTER_DESKTOP, FLUTTER_MOBILE ->
                    AppConstants.TOPOLOGY_LAYER_FRONTEND;
            case GATEWAY -> AppConstants.TOPOLOGY_LAYER_GATEWAY;
            case LIBRARY, CLI_TOOL, OTHER -> AppConstants.TOPOLOGY_LAYER_STANDALONE;
        };
    }

    /**
     * Builds a topology node from a service entity and pre-computed dependency/membership data.
     *
     * @param service            the service entity
     * @param upstreamCounts     map of serviceId → upstream dependency count
     * @param downstreamCounts   map of serviceId → downstream dependency count
     * @param serviceSolutionMap map of serviceId → list of solution IDs
     * @return the topology node response
     */
    TopologyNodeResponse buildTopologyNode(ServiceRegistration service,
                                           Map<UUID, Integer> upstreamCounts,
                                           Map<UUID, Integer> downstreamCounts,
                                           Map<UUID, List<UUID>> serviceSolutionMap) {
        int portCount = (int) portAllocationRepository.countByServiceId(service.getId());
        int upstream = upstreamCounts.getOrDefault(service.getId(), 0);
        int downstream = downstreamCounts.getOrDefault(service.getId(), 0);
        List<UUID> solutionIds = serviceSolutionMap.getOrDefault(service.getId(), List.of());
        String layer = classifyLayer(service.getServiceType());

        return new TopologyNodeResponse(
                service.getId(),
                service.getName(),
                service.getSlug(),
                service.getServiceType(),
                service.getStatus(),
                service.getLastHealthStatus() != null ? service.getLastHealthStatus() : HealthStatus.UNKNOWN,
                portCount,
                upstream,
                downstream,
                solutionIds,
                layer);
    }

    /**
     * Computes the maximum dependency depth via BFS from root nodes.
     *
     * <p>Root nodes are services with no upstream dependencies (not a source in any
     * dependency edge). The maximum BFS depth across all reachable nodes is returned.</p>
     *
     * @param dependencies  all dependencies in scope
     * @param allServiceIds all service IDs in scope
     * @return the maximum depth (0 if no dependencies exist)
     */
    int computeMaxDepth(List<ServiceDependency> dependencies, Set<UUID> allServiceIds) {
        if (dependencies.isEmpty()) {
            return 0;
        }

        // Build adjacency: source depends on target → target must start first
        // For depth calculation: edges go target → source (start order)
        Map<UUID, List<UUID>> adj = new HashMap<>();
        Set<UUID> sources = new HashSet<>();

        for (ServiceDependency dep : dependencies) {
            UUID targetId = dep.getTargetService().getId();
            UUID sourceId = dep.getSourceService().getId();
            if (allServiceIds.contains(targetId) && allServiceIds.contains(sourceId)) {
                adj.computeIfAbsent(targetId, k -> new ArrayList<>()).add(sourceId);
                sources.add(sourceId);
            }
        }

        // Roots = services that are not sources (no upstream deps)
        Set<UUID> roots = new HashSet<>(allServiceIds);
        roots.removeAll(sources);

        if (roots.isEmpty()) {
            return 0;
        }

        // BFS from all roots
        int maxDepth = 0;
        Queue<UUID> queue = new LinkedList<>(roots);
        Map<UUID, Integer> depthMap = new HashMap<>();
        for (UUID root : roots) {
            depthMap.put(root, 0);
        }

        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            int currentDepth = depthMap.get(current);

            for (UUID neighbor : adj.getOrDefault(current, List.of())) {
                if (!depthMap.containsKey(neighbor) || depthMap.get(neighbor) < currentDepth + 1) {
                    depthMap.put(neighbor, currentDepth + 1);
                    maxDepth = Math.max(maxDepth, currentDepth + 1);
                    queue.add(neighbor);
                }
            }
        }

        return maxDepth;
    }

    // ──────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────

    /**
     * Builds a map of serviceId → list of solution IDs that contain the service.
     */
    private Map<UUID, List<UUID>> buildServiceSolutionMap(List<Solution> solutions) {
        Map<UUID, List<UUID>> map = new HashMap<>();
        for (Solution sol : solutions) {
            List<SolutionMember> members = solutionMemberRepository.findBySolutionId(sol.getId());
            for (SolutionMember m : members) {
                map.computeIfAbsent(m.getService().getId(), k -> new ArrayList<>()).add(sol.getId());
            }
        }
        return map;
    }

    /**
     * Builds solution groups from solutions and their members.
     */
    private List<TopologySolutionGroup> buildSolutionGroups(List<Solution> solutions) {
        return solutions.stream()
                .map(sol -> {
                    List<SolutionMember> members = solutionMemberRepository.findBySolutionId(sol.getId());
                    List<UUID> svcIds = members.stream()
                            .map(m -> m.getService().getId())
                            .toList();
                    return new TopologySolutionGroup(
                            sol.getId(), sol.getName(), sol.getSlug(),
                            sol.getStatus(), members.size(), svcIds);
                })
                .toList();
    }

    /**
     * Groups nodes by their computed layer and builds layer responses.
     */
    private List<TopologyLayerResponse> buildLayers(List<TopologyNodeResponse> nodes) {
        Map<String, List<UUID>> layerMap = new LinkedHashMap<>();
        for (TopologyNodeResponse node : nodes) {
            layerMap.computeIfAbsent(node.layer(), k -> new ArrayList<>()).add(node.serviceId());
        }
        return layerMap.entrySet().stream()
                .map(e -> new TopologyLayerResponse(e.getKey(), e.getValue().size(), e.getValue()))
                .toList();
    }

    /**
     * Builds aggregate stats from pre-computed node, dependency, and solution data.
     */
    private TopologyStatsResponse buildStats(List<TopologyNodeResponse> nodes,
                                              List<ServiceDependency> dependencies,
                                              List<Solution> solutions,
                                              Set<UUID> allServiceIds) {
        int servicesWithNoDeps = (int) nodes.stream()
                .filter(n -> n.upstreamDependencyCount() == 0)
                .count();
        int servicesWithNoConsumers = (int) nodes.stream()
                .filter(n -> n.downstreamDependencyCount() == 0)
                .count();
        int orphaned = (int) nodes.stream()
                .filter(n -> n.upstreamDependencyCount() == 0
                        && n.downstreamDependencyCount() == 0
                        && n.solutionIds().isEmpty())
                .count();

        int maxDepth = computeMaxDepth(dependencies, allServiceIds);

        return new TopologyStatsResponse(
                nodes.size(),
                dependencies.size(),
                solutions.size(),
                servicesWithNoDeps,
                servicesWithNoConsumers,
                orphaned,
                maxDepth);
    }
}
