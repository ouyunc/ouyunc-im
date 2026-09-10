package com.ouyunc.message.cluster.lease;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.model.NodeLeasePayload;
import com.ouyunc.base.utils.ImSessionPresence;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.cache.distributed.redis.RedisPipelineSupport;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.schedule.ScheduleTimer;
import com.ouyunc.message.schedule.TimerTaskWrapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 本机进程租约：启动生成 epoch，定时 SET PX；读路径用快照判断路由是否仍挂在活节点上。
 */
public final class NodeLeaseKeeper {

    private static final Logger log = LoggerFactory.getLogger(NodeLeaseKeeper.class);

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    private static volatile long epoch;

    private static volatile Map<String, NodeLeasePayload> liveLeases = Map.of();

    private static volatile String leaseSha;

    private static final Object SHA_LOCK = new Object();

    /**
     * KEYS: lease, connHash；ARGV: epoch, ttlSeconds, 其后成对 appKey/count。与登录 Lua 分槽，本脚本只碰 {nodeId}。
     */
    private static final byte[] LEASE_AND_CONN_LUA = (
            "redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2]) "
                    + "redis.call('DEL', KEYS[2]) "
                    + "local i = 3 "
                    + "while i <= #ARGV do "
                    + "redis.call('HSET', KEYS[2], ARGV[i], ARGV[i + 1]) "
                    + "i = i + 2 "
                    + "end "
                    + "if redis.call('EXISTS', KEYS[2]) == 1 then redis.call('EXPIRE', KEYS[2], ARGV[2]) end "
                    + "return 1"
    ).getBytes(StandardCharsets.UTF_8);

    private static final AtomicBoolean CONN_PUBLISH_PENDING = new AtomicBoolean(false);

    private static final AtomicBoolean CONN_PUBLISH_DIRTY = new AtomicBoolean(false);

    private static final AtomicInteger REDIS_FAIL_STREAK = new AtomicInteger(0);

    private NodeLeaseKeeper() {
    }

    public static void start() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }
        epoch = TimeUtil.currentTimeMillis();
        String nodeId = localNodeId();
        StringRedisTemplate stringRedis = CacheFactory.STRING_REDIS.instance();
        stringRedis.delete(CacheConstant.buildImNodeConnHashCacheKey(nodeId));
        heartbeatOnce();
        ScheduleTimer.scheduleAtFixedRate(
                MessageConstant.IM_NODE_LEASE_TASK_ID,
                task -> heartbeatOnce(),
                MessageConstant.IM_NODE_LEASE_REFRESH_SECONDS,
                MessageConstant.IM_NODE_LEASE_REFRESH_SECONDS,
                TimeUnit.SECONDS);
        log.info("IM 节点租约已启动 nodeId={} epoch={}", nodeId, epoch);
    }

    public static void stop() {
        if (!STARTED.compareAndSet(true, false)) {
            return;
        }
        TimerTaskWrapper task = TimerTaskWrapper.timerTaskCaffeine.get(MessageConstant.IM_NODE_LEASE_TASK_ID);
        if (task != null) {
            task.cancel();
        }
        String nodeId = localNodeId();
        StringRedisTemplate stringRedis = CacheFactory.STRING_REDIS.instance();
        try {
            stringRedis.delete(CacheConstant.buildImNodeLeaseCacheKey(nodeId));
            stringRedis.opsForSet().remove(CacheConstant.buildImNodeSetCacheKey(), nodeId);
            stringRedis.delete(CacheConstant.buildImNodeConnHashCacheKey(nodeId));
        } catch (Exception e) {
            log.warn("停止节点租约清理 Redis 失败 nodeId={}: {}", nodeId, e.getMessage());
        }
        liveLeases = Map.of();
        log.info("IM 节点租约已停止 nodeId={}", nodeId);
    }

    public static long currentEpoch() {
        if (epoch <= 0L) {
            start();
        }
        return epoch;
    }

    public static String localNodeId() {
        return MessageServerContext.serverProperties().getLocalServerAddress();
    }

    /**
     * 本机当前 epoch 永远视为存活，避免心跳写入前的登录窗口误判。
     */
    public static boolean isLive(String nodeId, long nodeEpoch) {
        if (nodeEpoch <= 0L || nodeId == null) {
            return false;
        }
        if (nodeId.equals(localNodeId()) && nodeEpoch == epoch) {
            return true;
        }
        Long live = epochOf(nodeId);
        return live != null && live == nodeEpoch;
    }

    public static Map<String, Long> snapshot() {
        Map<String, Long> epochs = new HashMap<>();
        for (Map.Entry<String, NodeLeasePayload> entry : liveLeases.entrySet()) {
            if (entry.getValue() != null) {
                epochs.put(entry.getKey(), entry.getValue().getEpoch());
            }
        }
        return Map.copyOf(epochs);
    }

    public static Map<String, NodeLeasePayload> liveLeases() {
        return liveLeases;
    }

    /**
     * 租约集合中是否仍有该节点（不校验 epoch，供建池/投递发现）。
     */
    public static boolean hasLiveLease(String nodeId) {
        if (StringUtils.isBlank(nodeId)) {
            return false;
        }
        if (nodeId.equals(localNodeId())) {
            return true;
        }
        return liveLeases.containsKey(nodeId);
    }

    private static Long epochOf(String nodeId) {
        NodeLeasePayload payload = liveLeases.get(nodeId);
        return payload == null ? null : payload.getEpoch();
    }

    /**
     * 从 Redis 拉取当前租约仍在的节点。本机当前 epoch 始终并入结果。
     */
    public static Map<String, NodeLeasePayload> loadLiveLeases() {
        StringRedisTemplate stringRedis = CacheFactory.STRING_REDIS.instance();
        Set<String> nodeIds = stringRedis.opsForSet().members(CacheConstant.buildImNodeSetCacheKey());
        Map<String, NodeLeasePayload> latest;
        if (nodeIds == null || nodeIds.isEmpty()) {
            latest = new HashMap<>();
        } else {
            List<String> ids = nodeIds.stream().filter(StringUtils::isNotBlank).toList();
            List<String> keys = ids.stream().map(CacheConstant::buildImNodeLeaseCacheKey).toList();
            List<String> values = RedisPipelineSupport.getStrings(stringRedis, keys);
            latest = ImSessionPresence.parseLiveLeases(ids, values);
            latest.put(localNodeId(), localPayload());
            for (String id : ids) {
                if (!latest.containsKey(id)) {
                    stringRedis.opsForSet().remove(CacheConstant.buildImNodeSetCacheKey(), id);
                }
            }
            return latest;
        }
        latest.put(localNodeId(), localPayload());
        return latest;
    }

    private static NodeLeasePayload localPayload() {
        String zoneId = MessageServerContext.serverProperties().getClusterZoneId();
        return new NodeLeasePayload(epoch, zoneId, localNodeId());
    }

    /**
     * 本机计数刚变：合并到下一拍写入 Redis，供其它节点配额读取。登录 Lua 仍不同槽。
     */
    public static void scheduleConnPublish() {
        CONN_PUBLISH_DIRTY.set(true);
        if (!STARTED.get() || !CONN_PUBLISH_PENDING.compareAndSet(false, true)) {
            return;
        }
        ScheduleTimer.scheduleOnce(() -> {
            CONN_PUBLISH_PENDING.set(false);
            CONN_PUBLISH_DIRTY.set(false);
            if (!STARTED.get()) {
                return;
            }
            try {
                publishLeaseAndConnCounts(CacheFactory.STRING_REDIS.instance(), localNodeId());
            } catch (Exception e) {
                log.warn("合并发布本机连接数失败 nodeId={}", localNodeId(), e);
            }
            if (CONN_PUBLISH_DIRTY.get()) {
                scheduleConnPublish();
            }
        }, MessageConstant.IM_NODE_CONN_PUBLISH_DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
    }

    private static void heartbeatOnce() {
        String nodeId = localNodeId();
        StringRedisTemplate stringRedis = CacheFactory.STRING_REDIS.instance();
        try {
            publishLeaseAndConnCounts(stringRedis, nodeId);
            stringRedis.opsForSet().add(CacheConstant.buildImNodeSetCacheKey(), nodeId);
            liveLeases = Map.copyOf(loadLiveLeases());
            REDIS_FAIL_STREAK.set(0);
            if (MessageServerContext.REDIS_ISOLATION_DRAINING.get()) {
                MessageServerContext.exitRedisIsolationDrain();
            }
            ClusterMembershipReconciler.reconcile(liveLeases);
        } catch (Exception e) {
            log.error("刷新 IM 节点租约失败 nodeId={}，沿用上一拍快照（本机 epoch 仍视为存活）", nodeId, e);
            onLeaseRedisFailure();
        }
    }

    private static void onLeaseRedisFailure() {
        int streak = REDIS_FAIL_STREAK.incrementAndGet();
        String action = StringUtils.trimToEmpty(
                MessageServerContext.serverProperties().getClusterIsolationAction());
        int threshold = MessageServerContext.serverProperties().getClusterIsolationRedisFailThreshold();
        if (threshold <= 0) {
            threshold = 3;
        }
        if (MessageConstant.CLUSTER_ISOLATION_ACTION_DRAIN_ON_REDIS_LOSS.equalsIgnoreCase(action)
                && streak >= threshold
                && !MessageServerContext.REDIS_ISOLATION_DRAINING.get()) {
            MessageServerContext.enterRedisIsolationDrain();
        }
    }

    /**
     * 租约与连接 HASH 同 {nodeId} 槽一次 Lua；节点集合 SET 无标签，只能另发。
     */
    private static void publishLeaseAndConnCounts(StringRedisTemplate stringRedis, String nodeId) {
        Map<String, String> counts = LocalNodeConnCounter.snapshot();
        RedisSerializer<String> serializer = stringRedis.getStringSerializer();
        List<byte[]> keysAndArgs = new ArrayList<>(4 + counts.size() * 2);
        keysAndArgs.add(utf8(serializer, CacheConstant.buildImNodeLeaseCacheKey(nodeId)));
        keysAndArgs.add(utf8(serializer, CacheConstant.buildImNodeConnHashCacheKey(nodeId)));
        keysAndArgs.add(utf8(serializer, localPayload().toJson()));
        keysAndArgs.add(utf8(serializer, String.valueOf(MessageConstant.IM_NODE_LEASE_TTL_SECONDS)));
        counts.forEach((appKey, count) -> {
            keysAndArgs.add(utf8(serializer, appKey));
            keysAndArgs.add(utf8(serializer, count));
        });
        evalLeaseScript(stringRedis, keysAndArgs.toArray(byte[][]::new));
    }

    /**
     * 心跳 2s 一次，EVALSHA 避免反复传脚本正文。
     */
    private static void evalLeaseScript(StringRedisTemplate stringRedis, byte[][] keysAndArgs) {
        String sha = leaseShaOf(stringRedis);
        try {
            evalSha(stringRedis, sha, keysAndArgs);
            return;
        } catch (Exception first) {
            if (!isNoScript(first)) {
                throw first;
            }
        }
        synchronized (SHA_LOCK) {
            leaseSha = null;
        }
        sha = leaseShaOf(stringRedis);
        try {
            evalSha(stringRedis, sha, keysAndArgs);
        } catch (Exception second) {
            if (!isNoScript(second)) {
                throw second;
            }
            log.warn("租约 EVALSHA 仍 NOSCRIPT，回退 EVAL");
            stringRedis.execute((RedisCallback<Object>) connection ->
                    connection.scriptingCommands().eval(LEASE_AND_CONN_LUA, ReturnType.VALUE, 2, keysAndArgs));
        }
    }

    private static String leaseShaOf(StringRedisTemplate stringRedis) {
        String sha = leaseSha;
        if (sha != null) {
            return sha;
        }
        synchronized (SHA_LOCK) {
            if (leaseSha != null) {
                return leaseSha;
            }
            leaseSha = stringRedis.execute((RedisCallback<String>) connection ->
                    connection.scriptingCommands().scriptLoad(LEASE_AND_CONN_LUA));
            if (leaseSha == null) {
                throw new IllegalStateException("租约 SCRIPT LOAD 返回空 SHA");
            }
            return leaseSha;
        }
    }

    private static void evalSha(StringRedisTemplate stringRedis, String sha, byte[][] keysAndArgs) {
        stringRedis.execute((RedisCallback<Object>) connection ->
                connection.scriptingCommands().evalSha(sha, ReturnType.VALUE, 2, keysAndArgs));
    }

    private static boolean isNoScript(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            String name = cursor.getClass().getName();
            String message = cursor.getMessage();
            if (name.contains("NoScript") || (message != null && message.contains("NOSCRIPT"))) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private static byte[] utf8(RedisSerializer<String> serializer, String value) {
        byte[] raw = serializer.serialize(value);
        if (raw == null) {
            throw new IllegalStateException("Redis 字符串序列化失败");
        }
        return raw;
    }
}
