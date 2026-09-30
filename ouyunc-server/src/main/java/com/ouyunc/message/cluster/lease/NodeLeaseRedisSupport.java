package com.ouyunc.message.cluster.lease;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 租约 Redis 原子操作。每个脚本只操作同槽 key；DefaultRedisScript 负责 SHA 缓存和 NOSCRIPT 回退。
 * 发现索引是单个 ZSET。停机不按跨槽 GET 的结果删成员，过期由注册脚本按 score 回收。
 */
final class NodeLeaseRedisSupport {

    private static final DefaultRedisScript<Long> PUBLISH_LEASE = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if current and current ~= ARGV[1] then return 0 end
            redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
            return 1
            """, Long.class);

    /** 只在连接计数变化或低频续期时重建 HASH，并校验当前租约所有权。 */
    private static final DefaultRedisScript<Long> PUBLISH_CONNECTIONS = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            redis.call('DEL', KEYS[2])
            for i = 3, #ARGV, 2 do
                redis.call('HSET', KEYS[2], ARGV[i], ARGV[i + 1])
            end
            if redis.call('EXISTS', KEYS[2]) == 1 then
                redis.call('EXPIRE', KEYS[2], ARGV[2])
            end
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            redis.call('DEL', KEYS[1], KEYS[2])
            return 1
            """, Long.class);

    /**
     * Redis TIME 为发现索引提供统一时钟。先刷新本成员 score，再按 score 删除已到期成员：
     * 本轮刚续期的节点 score 已在 now 之后，不会被同一脚本删掉。
     * 停机不主动 ZREM，剩余索引由后续注册到期回收；租约删除后读路径立即忽略它。
     */
    private static final DefaultRedisScript<Long> REGISTER = new DefaultRedisScript<>("""
            local clock = redis.call('TIME')
            local now = tonumber(clock[1]) + tonumber(clock[2]) / 1000000
            redis.call('ZADD', KEYS[1], now + tonumber(ARGV[2]), ARGV[1])
            local expired = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', now,
                'LIMIT', 0, tonumber(ARGV[3]))
            if #expired > 0 then
                redis.call('ZREM', KEYS[1], unpack(expired))
            end
            return 1
            """, Long.class);

    private NodeLeaseRedisSupport() {
    }

    /** 相同地址只能由当前所有者续租；冲突实例必须等待旧租约释放或到期。 */
    static void publishLease(StringRedisTemplate redis, String nodeId, String payload) {
        Long result = redis.execute(PUBLISH_LEASE, List.of(CacheConstant.buildImNodeLeaseCacheKey(nodeId)),
                payload, String.valueOf(MessageConstant.IM_NODE_LEASE_TTL_SECONDS));
        requireOwned(result, nodeId);
    }

    static void publishConnections(StringRedisTemplate redis, String nodeId, String payload,
                                   Map<String, String> counts) {
        List<String> args = new ArrayList<>();
        args.add(payload);
        args.add(String.valueOf(MessageConstant.IM_NODE_CONN_COUNT_TTL_SECONDS));
        counts.forEach((appKey, count) -> {
            args.add(appKey);
            args.add(count);
        });
        Long result = redis.execute(PUBLISH_CONNECTIONS, leaseKeys(nodeId), args.toArray());
        requireOwned(result, nodeId);
    }

    private static void requireOwned(Long result, String nodeId) {
        if (result == null || result != MessageConstant.IM_NODE_LEASE_LUA_OK) {
            throw new IllegalStateException("节点租约被其他实例持有 nodeId=" + nodeId);
        }
    }

    static void register(StringRedisTemplate redis, String nodeId) {
        Long result = redis.execute(REGISTER, List.of(CacheConstant.buildImNodeRegistryCacheKey()), nodeId,
                String.valueOf(MessageConstant.IM_NODE_LEASE_TTL_SECONDS),
                String.valueOf(MessageConstant.IM_NODE_REGISTRY_CLEANUP_BATCH));
        if (result == null || result != MessageConstant.IM_NODE_LEASE_LUA_OK) {
            throw new IllegalStateException("节点注册索引刷新失败 nodeId=" + nodeId);
        }
    }

    /** 完整 payload 含 ownerToken，旧实例不能删除新实例的数据。 */
    static void release(StringRedisTemplate redis, String nodeId, String payload) {
        redis.execute(RELEASE, leaseKeys(nodeId), payload);
    }

    private static List<String> leaseKeys(String nodeId) {
        return List.of(CacheConstant.buildImNodeLeaseCacheKey(nodeId),
                CacheConstant.buildImNodeConnHashCacheKey(nodeId));
    }
}
