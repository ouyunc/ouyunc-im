package com.ouyunc.message.cluster.routing;

import com.ouyunc.base.model.RoutingTable;
import com.ouyunc.message.cluster.topology.ClusterTopologyView;
import org.apache.commons.collections4.CollectionUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * zone-aware 模式：区内任意中转，跨区优先经本区/目标区 gateway。
 */
public final class ZoneAwareNextHopSelector implements ClusterNextHopSelector {

    private final ClusterTopologyView topology;

    public ZoneAwareNextHopSelector(ClusterTopologyView topology) {
        this.topology = topology;
    }

    @Override
    public String select(String targetServerAddress, List<RoutingTable> routingTables, Set<String> candidates) {
        if (CollectionUtils.isEmpty(candidates)) {
            return null;
        }
        String targetZoneId = topology.resolveZone(targetServerAddress);
        String localZoneId = topology.getLocalZoneId();

        if (topology.isSameZone(localZoneId, targetZoneId)) {
            return FlatNextHopSelector.INSTANCE.select(
                    targetServerAddress,
                    routingTables,
                    intersect(candidates, topology.getZoneNodes(localZoneId))
            );
        }

        if (topology.getCrossZoneVia() == CrossZoneVia.ANY) {
            return FlatNextHopSelector.INSTANCE.select(targetServerAddress, routingTables, candidates);
        }

        if (!topology.isLocalGateway()) {
            return FlatNextHopSelector.INSTANCE.select(
                    targetServerAddress,
                    routingTables,
                    intersect(candidates, topology.getZoneGateways(localZoneId))
            );
        }

        Set<String> targetZoneGateways = intersect(candidates, topology.getZoneGateways(targetZoneId));
        if (!targetZoneGateways.isEmpty()) {
            return FlatNextHopSelector.INSTANCE.select(targetServerAddress, routingTables, targetZoneGateways);
        }

        Set<String> targetZoneNodes = intersect(candidates, topology.getZoneNodes(targetZoneId));
        if (!targetZoneNodes.isEmpty()) {
            return FlatNextHopSelector.INSTANCE.select(targetServerAddress, routingTables, targetZoneNodes);
        }

        return FlatNextHopSelector.INSTANCE.select(
                targetServerAddress,
                routingTables,
                intersect(candidates, topology.getZoneGateways(localZoneId))
        );
    }

    private static Set<String> intersect(Set<String> candidates, Set<String> scope) {
        Set<String> result = new LinkedHashSet<>();
        for (String candidate : candidates) {
            if (scope.contains(candidate)) {
                result.add(candidate);
            }
        }
        return result;
    }
}
