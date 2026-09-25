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
 * 注册集合是发现索引，不能根据一次跨槽 GET 的结果直接 SREM。
 */
final class NodeLeaseRedisSupport {

    private static final DefaultRedisScript<Long> PUBLISH = new DefaultRedisScript<>("""
            local current = redis.call('GET', KEYS[1])
            if current and current ~= ARGV[1] then return 0 end
            redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
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
     * Redis TIME 为注册索引提供统一时钟。注册/过期判断/移除同槽串行执行：
     * 新一轮注册已经刷新 score 时，旧清理无法再删除这个成员。
     * 停机不主动 SREM，剩余索引由到期回收，实际租约删除后读路径立即忽略它。
     */
    private static final DefaultRedisScript<Long> REGISTER = new DefaultRedisScript<>("""
            local clock = redis.call('TIME')
            local now = tonumber(clock[1]) + tonumber(clock[2]) / 1000000
            redis.call('SADD', KEYS[1], ARGV[1])
            redis.call('ZADD', KEYS[2], now + tonumber(ARGV[2]), ARGV[1])
            local expired = redis.call('ZRANGEBYSCORE', KEYS[2], '-inf', now,
                'LIMIT', 0, tonumber(ARGV[3]))
            for _, node in ipairs(expired) do
                redis.call('SREM', KEYS[1], node)
                redis.call('ZREM', KEYS[2], node)
            end
            return 1
            """, Long.class);

    private NodeLeaseRedisSupport() {
    }

    /** 相同地址只能由当前所有者续租；冲突实例必须等待旧租约释放或到期。 */
    static void publish(StringRedisTemplate redis, String nodeId, String payload, Map<String, String> counts) {
        List<String> args = new ArrayList<>();
        args.add(payload);
        args.add(String.valueOf(MessageConstant.IM_NODE_LEASE_TTL_SECONDS));
        counts.forEach((appKey, count) -> {
            args.add(appKey);
            args.add(count);
        });
        Long result = redis.execute(PUBLISH, leaseKeys(nodeId), args.toArray());
        if (result == null || result != MessageConstant.IM_NODE_LEASE_LUA_OK) {
            throw new IllegalStateException("节点租约被其他实例持有 nodeId=" + nodeId);
        }
    }

    static void register(StringRedisTemplate redis, String nodeId) {
        Long result = redis.execute(REGISTER, List.of(CacheConstant.buildImNodeSetCacheKey(),
                        CacheConstant.buildImNodeRegistryExpiryCacheKey()), nodeId,
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
