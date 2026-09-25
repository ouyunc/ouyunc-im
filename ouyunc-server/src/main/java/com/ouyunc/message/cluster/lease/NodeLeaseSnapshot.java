package com.ouyunc.message.cluster.lease;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.model.NodeLeasePayload;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 成员快照携带读取起点和权威性，避免将 Redis 不可用产生的空集合当作节点死亡证据。
 * 路由清理必须保留此对象并再次检查有效性，不能只传递丢失状态信息的 epoch Map。
 */
public final class NodeLeaseSnapshot {

    private final Map<String, NodeLeasePayload> leases;
    private final Map<String, Long> epochs;
    private final long observedAtNanos;
    private final boolean authoritative;

    public NodeLeaseSnapshot(Map<String, NodeLeasePayload> leases, long observedAtNanos, boolean authoritative) {
        this.leases = Map.copyOf(leases);
        this.observedAtNanos = observedAtNanos;
        this.authoritative = authoritative;
        // 每拍只转换一次，批量查询多个用户时复用，避免按用户反复遍历全部集群节点。
        Map<String, Long> values = new HashMap<>(leases.size());
        leases.forEach((node, payload) -> values.put(node, payload.getEpoch()));
        this.epochs = Map.copyOf(values);
    }

    /** 单调时钟不受系统时间回拨影响；有效期从 Redis 读取前开始计算。 */
    public boolean isFresh() {
        return authoritative && System.nanoTime() - observedAtNanos
                < TimeUnit.SECONDS.toNanos(MessageConstant.IM_NODE_LEASE_SNAPSHOT_TTL_SECONDS);
    }

    public Map<String, NodeLeasePayload> leases() {
        return leases;
    }

    public Map<String, Long> epochs() {
        return epochs;
    }
}
