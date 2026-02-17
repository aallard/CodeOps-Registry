package com.codeops.registry.dto.response;

import java.util.List;
import java.util.UUID;

/** A topology layer grouping services by their architectural role. */
public record TopologyLayerResponse(
    String layer,
    int serviceCount,
    List<UUID> serviceIds
) {}
