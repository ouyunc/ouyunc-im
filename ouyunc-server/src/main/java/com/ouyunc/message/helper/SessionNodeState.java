package com.ouyunc.message.helper;

import com.ouyunc.message.cluster.lease.NodeLeaseKeeper;
import com.ouyunc.message.cluster.lease.NodeLeaseSnapshot;

import java.util.Map;

/**
 * 登录目录共用的节点状态入口。集群使用 Redis 租约；单机只使用本地进程代次。
 * 共享登录/在线查询调用本类，避免单机误启动集群发现与续租任务。
 */
public final class SessionNodeState {

    private SessionNodeState() {
    }

    public static String localNodeId() {
        return NodeLeaseKeeper.localNodeId();
    }

    public static boolean isReady() {
        return NodeLeaseKeeper.isReady();
    }

    public static long currentEpoch() {
        return NodeLeaseKeeper.currentEpoch();
    }

    public static boolean isLive(String nodeId, long epoch) {
        return NodeLeaseKeeper.isLive(nodeId, epoch);
    }

    public static NodeLeaseSnapshot currentSnapshot() {
        return NodeLeaseKeeper.currentSnapshot();
    }

    public static Map<String, Long> snapshot() {
        return currentSnapshot().epochs();
    }

    public static boolean isCurrentSnapshot(NodeLeaseSnapshot snapshot) {
        return NodeLeaseKeeper.isCurrentSnapshot(snapshot);
    }

    public static void scheduleConnPublish() {
        NodeLeaseKeeper.scheduleConnPublish();
    }
}
