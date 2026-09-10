package com.ouyunc.message.cluster.lease;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.model.NodeLeasePayload;
import com.ouyunc.message.cluster.client.pool.MessageClientPool;
import com.ouyunc.message.cluster.topology.ClusterTopologyView;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.properties.MessageServerProperties;
import io.netty.channel.pool.ChannelPool;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 以 Redis 租约为成员权威，把 ChannelPool 收敛成派生连接缓存。
 */
public final class ClusterMembershipReconciler {

    private static final Logger log = LoggerFactory.getLogger(ClusterMembershipReconciler.class);

    private ClusterMembershipReconciler() {
    }

    public static void reconcile(Map<String, NodeLeasePayload> liveLeases) {
        if (!MessageServerContext.serverProperties().isClusterEnable()) {
            return;
        }
        Set<String> desired = selectDesired(liveLeases);
        Set<String> existing = existingPoolNodes();
        for (String nodeId : desired) {
            MessageClientPool.ensurePool(nodeId);
        }
        for (String nodeId : existing) {
            if (!desired.contains(nodeId)) {
                MessageClientPool.evictPool(nodeId);
            }
        }
    }

    static Set<String> selectDesired(Map<String, NodeLeasePayload> liveLeases) {
        Set<String> desired = new HashSet<>();
        if (liveLeases == null || liveLeases.isEmpty()) {
            return desired;
        }
        MessageServerProperties properties = MessageServerContext.serverProperties();
        ClusterTopologyView topology = MessageServerContext.clusterTopologyView;
        String local = properties.getLocalServerAddress();
        boolean allowlist = MessageConstant.CLUSTER_MEMBERSHIP_MODE_ALLOWLIST
                .equalsIgnoreCase(StringUtils.trimToEmpty(properties.getClusterMembershipMode()));
        Set<String> allowed = allowlist ? allowedNodes(properties, topology) : Set.of();
        for (Map.Entry<String, NodeLeasePayload> entry : liveLeases.entrySet()) {
            String nodeId = entry.getKey();
            if (StringUtils.isBlank(nodeId) || nodeId.equals(local)) {
                continue;
            }
            if (allowlist && !allowed.contains(nodeId)) {
                log.warn("租约节点不在 allowlist，忽略建池: {}", nodeId);
                continue;
            }
            NodeLeasePayload payload = entry.getValue();
            String zoneId = payload == null ? "" : payload.getZoneId();
            if (topology != null && !topology.shouldConnect(nodeId, zoneId)) {
                continue;
            }
            desired.add(nodeId);
            if (desired.size() >= MessageConstant.CLUSTER_MEMBERSHIP_MAX_NODES) {
                log.error("集群发现节点数达到硬顶 {}，停止纳入新节点", MessageConstant.CLUSTER_MEMBERSHIP_MAX_NODES);
                break;
            }
        }
        return desired;
    }

    private static Set<String> allowedNodes(MessageServerProperties properties, ClusterTopologyView topology) {
        Set<String> allowed = new HashSet<>();
        if (properties.getNodes() != null) {
            allowed.addAll(properties.getNodes());
        }
        if (topology != null) {
            allowed.addAll(topology.getAllConfiguredNodes());
        }
        allowed.add(properties.getLocalServerAddress());
        return allowed;
    }

    private static Set<String> existingPoolNodes() {
        Set<String> existing = new HashSet<>();
        Map<String, ChannelPool> active = MessageServerContext.clusterActiveServerRegistryTableCache.asMap();
        Map<String, ChannelPool> global = MessageServerContext.clusterGlobalServerRegistryTableCache.asMap();
        if (active != null) {
            existing.addAll(active.keySet());
        }
        if (global != null) {
            existing.addAll(global.keySet());
        }
        return existing;
    }
}
