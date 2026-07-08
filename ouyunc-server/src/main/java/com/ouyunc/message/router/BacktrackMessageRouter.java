package com.ouyunc.message.router;

import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.model.RoutingTable;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.utils.MapUtil;
import com.ouyunc.message.cluster.routing.ClusterNextHopSelector;
import com.ouyunc.message.cluster.routing.FlatNextHopSelector;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.thread.MessageClusterRouteFailureThread;
import io.netty.channel.pool.ChannelPool;

import java.util.*;

/**
 * @Description: 回溯路由算法
 **/
public class BacktrackMessageRouter extends AbstractMessageRouter {

    private final ClusterNextHopSelector nextHopSelector;

    public BacktrackMessageRouter() {
        this(FlatNextHopSelector.INSTANCE);
    }

    public BacktrackMessageRouter(ClusterNextHopSelector nextHopSelector) {
        this.nextHopSelector = nextHopSelector;
    }

    /**
     * @Description 回溯路由, 找出一个有效的路由, 这里的算法有点绕
     */
    @Override
    public String route(Packet packet, String toServerAddress) {
        Metadata metadata = packet.getMessage().getMetadata();
        List<RoutingTable> routingTables = metadata.getRoutingTables();
        Iterator<RoutingTable> routedTableIterator = routingTables.iterator();
        boolean isContain = false;
        RoutingTable localRoutingTable = null;
        while (routedTableIterator.hasNext()) {
            RoutingTable routingTable = routedTableIterator.next();
            if (routingTable.getServerAddress().equals(MessageServerContext.serverProperties().getLocalServerAddress())) {
                routingTable.getRoutedServerAddresses().add(toServerAddress);
                localRoutingTable = routingTable;
                isContain = true;
                break;
            }
        }
        if (localRoutingTable == null || !toServerAddress.equals(localRoutingTable.getPreServerAddress())) {
            if (!isContain) {
                Set<String> routedServerAddresses = new HashSet<>();
                routedServerAddresses.add(toServerAddress);
                localRoutingTable = new RoutingTable(MessageServerContext.serverProperties().getLocalServerAddress(), metadata.getFromServerAddress(), routedServerAddresses);
                routingTables.add(localRoutingTable);
                metadata.setRoutingTables(routingTables);
            }
            Set<String> candidates = collectEligibleCandidates(localRoutingTable, routingTables);
            String nextHop = nextHopSelector.select(toServerAddress, routingTables, candidates);
            if (nextHop != null) {
                return nextHop;
            }
            if (localRoutingTable.getPreServerAddress() != null) {
                for (RoutingTable preRoutingTable : routingTables) {
                    if (localRoutingTable.getPreServerAddress().equals(preRoutingTable.getServerAddress())) {
                        preRoutingTable.getRoutedServerAddresses().add(MessageServerContext.serverProperties().getLocalServerAddress());
                        break;
                    }
                }
                return localRoutingTable.getPreServerAddress();
            }
        }
        routerExecutor().execute(new MessageClusterRouteFailureThread(packet));
        return null;
    }

    /**
     * 从注册表中收集符合防环规则的候选下一跳。
     */
    Set<String> collectEligibleCandidates(RoutingTable localRoutingTable, List<RoutingTable> routingTables) {
        Set<String> candidates = new LinkedHashSet<>();
        Iterator<Map.Entry<String, ChannelPool>> allSocketAddressIterator = MapUtil.mergerMaps(
                MessageServerContext.clusterActiveServerRegistryTableCache.asMap(),
                MessageServerContext.clusterGlobalServerRegistryTableCache.asMap()
        ).entrySet().iterator();
        while (allSocketAddressIterator.hasNext()) {
            Map.Entry<String, ChannelPool> next = allSocketAddressIterator.next();
            String nextServerAddress = next.getKey();
            boolean isExists = false;
            for (RoutingTable routingTable : routingTables) {
                if (routingTable.getServerAddress().equals(nextServerAddress)) {
                    isExists = true;
                    break;
                }
            }
            if (!isExists
                    && !MessageServerContext.serverProperties().getLocalServerAddress().equals(nextServerAddress)
                    && !localRoutingTable.getRoutedServerAddresses().contains(nextServerAddress)) {
                candidates.add(nextServerAddress);
            }
        }
        return candidates;
    }
}
