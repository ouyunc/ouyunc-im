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

    /**
     * 按当前存活租约把集群连接池收成应连集合。
     * 应连节点补池，租约里已不在、或拓扑不再要求连接的节点拆池。单机模式直接返回。
     *
     * @param liveLeases 当前仍持有租约的节点，key 为节点地址
     */
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

    /**
     * 从存活租约里选出本机应该建池的远端节点。
     * 跳过本机和空白地址；allowlist 模式下不在配置或拓扑名单里的租约只记日志；
     * 拓扑判定不应连接的节点也不纳入。数量达到硬顶后停止继续加入。
     *
     * @param liveLeases 当前仍持有租约的节点
     * @return 应保留连接池的远端节点地址
     */
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

    /**
     * allowlist 的合法节点：静态配置、拓扑里已配置的节点，再加上本机地址。
     *
     * @param properties 当前消息服务配置
     * @param topology   集群拓扑；为空时只采用静态配置和本机地址
     * @return 允许建池的节点地址
     */
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

    /**
     * 本机已经建出的集群连接池节点，活跃表和全局表合并。
     *
     * @return 当前持有 ChannelPool 的节点地址
     */
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
