package com.ouyunc.message.cluster.routing;

import com.ouyunc.base.model.RoutingTable;

import java.util.List;
import java.util.Set;

/**
 * 集群消息下一跳选择策略。
 */
public interface ClusterNextHopSelector {

    /**
     * 从候选集中选出下一跳；无可用节点时返回 null。
     */
    String select(String targetServerAddress, List<RoutingTable> routingTables, Set<String> candidates);
}
