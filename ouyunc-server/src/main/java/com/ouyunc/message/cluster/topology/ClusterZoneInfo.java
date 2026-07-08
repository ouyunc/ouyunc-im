package com.ouyunc.message.cluster.topology;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 单个分区的拓扑定义。
 */
public final class ClusterZoneInfo {

    private final String zoneId;
    private final Set<String> nodes;
    private final Set<String> gateways;

    public ClusterZoneInfo(String zoneId, Set<String> nodes, Set<String> gateways) {
        this.zoneId = zoneId;
        this.nodes = Collections.unmodifiableSet(new LinkedHashSet<>(nodes));
        this.gateways = Collections.unmodifiableSet(new LinkedHashSet<>(gateways));
    }

    public String getZoneId() {
        return zoneId;
    }

    public Set<String> getNodes() {
        return nodes;
    }

    public Set<String> getGateways() {
        return gateways;
    }
}
