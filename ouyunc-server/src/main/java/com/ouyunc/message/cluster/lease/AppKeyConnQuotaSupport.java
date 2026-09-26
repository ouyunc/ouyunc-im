package com.ouyunc.message.cluster.lease;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.LuaScriptEnum;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.message.helper.SessionNodeState;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * appKey 连接上限：同槽 HASH（field=nodeId）Lua 求和后再 HINCRBY，避免读远端租约 HASH 再本机 CAS 的窗口。
 * 节点宕机残留 field 由心跳 SYNC 按存活租约剔除。
 */
public final class AppKeyConnQuotaSupport {

    private static final Logger log = LoggerFactory.getLogger(AppKeyConnQuotaSupport.class);

    private static final DefaultRedisScript<Long> RESERVE_SCRIPT = script(
            LuaScriptEnum.APP_KEY_CONN_RESERVE_SCRIPT);
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = script(
            LuaScriptEnum.APP_KEY_CONN_RELEASE_SCRIPT);
    private static final byte[] SYNC_SCRIPT_BYTES = LuaScriptEnum.APP_KEY_CONN_SYNC_SCRIPT.getScript()
            .getBytes(StandardCharsets.UTF_8);
    private static final ReentrantLock[] QUOTA_LOCKS = createQuotaLocks();

    private AppKeyConnQuotaSupport() {
    }

    /**
     * 登录路径原子预占：同槽 HASH 上 Lua 求和后再 HINCRBY，成功后增加本机计数。
     *
     * @param appKey          租户标识；空白时直接拒绝
     * @param maxConnections  该 appKey 允许的连接上限
     * @return true 表示已预占；达到上限、脚本失败或 appKey 空白时返回 false
     */
    public static boolean tryReserve(String appKey, long maxConnections) {
        if (StringUtils.isBlank(appKey)) {
            return false;
        }
        ReentrantLock lock = quotaLock(appKey);
        lock.lock();
        try {
            Long result = eval(RESERVE_SCRIPT, appKey, SessionNodeState.localNodeId(),
                    String.valueOf(maxConnections),
                    String.valueOf(MessageConstant.IM_APP_KEY_CONN_QUOTA_TTL_SECONDS));
            if (result == null || result != MessageConstant.IM_APP_KEY_CONN_QUOTA_LUA_OK) {
                return false;
            }
            LocalNodeConnCounter.increment(appKey);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 同步释放一次预占：先减本机计数，再执行释放脚本。
     * 脚本失败只记日志，本机计数已经减去；下一次心跳 SYNC 会按本机快照把 Redis field 写回。
     * EventLoop 上的关连钩子应走 {@link #releaseAsync(String)}，不要在 IO 线程直接调用。
     *
     * @param appKey 租户标识；空白时忽略
     */
    public static void release(String appKey) {
        if (StringUtils.isBlank(appKey)) {
            return;
        }
        try {
            ReentrantLock lock = quotaLock(appKey);
            lock.lock();
            try {
                LocalNodeConnCounter.decrement(appKey);
                eval(RELEASE_SCRIPT, appKey, SessionNodeState.localNodeId());
            } finally {
                lock.unlock();
            }
        } catch (Exception e) {
            log.warn("释放 appKey 连接配额失败 appKey={}", appKey, e);
        }
    }

    /**
     * 关连钩子跑在 EventLoop 上，Lua 释放必须离开 IO 线程。
     * 提交失败时本机计数已减，下一次心跳 SYNC 会按快照把 Redis field 写回去。
     *
     * @param appKey 租户标识；空白时忽略
     */
    public static void releaseAsync(String appKey) {
        if (StringUtils.isBlank(appKey)) {
            return;
        }
        try {
            ThreadPoolManager.messageProcessorExecutor().execute(() -> release(appKey));
        } catch (RuntimeException e) {
            log.warn("提交 appKey 连接配额释放失败，等待心跳 SYNC 对齐 appKey={}", appKey, e);
        }
    }

    /**
     * 只读求和，供 HTTP 校验。失败时抛给调用方按拒绝处理。
     *
     * @param appKey 租户标识；空白时返回 0
     * @return 各节点 field 的连接数之和；无法解析的脏 field 不计入
     */
    public static long current(String appKey) {
        if (StringUtils.isBlank(appKey)) {
            return 0L;
        }
        StringRedisTemplate redis = CacheFactory.STRING_REDIS.instance();
        List<Object> values = redis.opsForHash().values(CacheConstant.buildAppKeyConnQuotaHashCacheKey(appKey));
        long sum = 0L;
        if (values == null) {
            return 0L;
        }
        for (Object raw : values) {
            if (raw == null) {
                continue;
            }
            try {
                sum += Long.parseLong(String.valueOf(raw));
            } catch (NumberFormatException ignored) {
                // 脏 field 不计
            }
        }
        return sum;
    }

    /**
     * 租约维护任务：同一时刻仅一个任务执行，Lua 按有限批次走管道，不再占用核心续租路径。
     * 本机字段刷新不依赖快照对象身份；远端字段只有在成员缺失且自身时间戳超过宽限期后才删除。
     * 无本机连接时不扫描整个 Redis；无活节点维护的孤儿 HASH 由原有 TTL 自然回收。
     *
     * @param snapshot 当前存活节点租约，用于判断远端 field 是否仍有维护者
     */
    public static void syncAfterHeartbeat(NodeLeaseSnapshot snapshot) {
        Map<String, String> local = LocalNodeConnCounter.snapshot();
        if (local.isEmpty()) {
            return;
        }
        List<String> liveNodes = new ArrayList<>(snapshot.leases().keySet());
        List<QuotaSyncItem> batch = new ArrayList<>(MessageConstant.IM_NODE_QUOTA_SYNC_BATCH);
        for (Map.Entry<String, String> entry : local.entrySet()) {
            batch.add(new QuotaSyncItem(entry.getKey()));
            if (batch.size() >= MessageConstant.IM_NODE_QUOTA_SYNC_BATCH) {
                syncBatch(liveNodes, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            syncBatch(liveNodes, batch);
        }
    }

    /** 每个 appKey 的 Lua 仍保持同槽原子性；管道仅合并网络往返，不作为跨 key 事务。 */
    private static boolean syncBatch(List<String> liveNodes, List<QuotaSyncItem> batch) {
        try {
            StringRedisTemplate redis = CacheFactory.STRING_REDIS.instance();
            String nodeId = SessionNodeState.localNodeId();
            String ttl = String.valueOf(MessageConstant.IM_APP_KEY_CONN_QUOTA_TTL_SECONDS);
            List<ReentrantLock> locks = batchLocks(batch);
            locks.forEach(ReentrantLock::lock);
            List<Object> results;
            try {
                results = redis.executePipelined((RedisCallback<Object>) connection -> {
                    for (QuotaSyncItem item : batch) {
                        String currentCount = String.valueOf(LocalNodeConnCounter.get(item.appKey()));
                        List<byte[]> keysAndArgs = new ArrayList<>();
                        keysAndArgs.add(CacheConstant.buildAppKeyConnQuotaHashCacheKey(item.appKey())
                                .getBytes(StandardCharsets.UTF_8));
                        keysAndArgs.add(CacheConstant.buildAppKeyConnQuotaSeenHashCacheKey(item.appKey())
                                .getBytes(StandardCharsets.UTF_8));
                        keysAndArgs.add(nodeId.getBytes(StandardCharsets.UTF_8));
                        keysAndArgs.add(currentCount.getBytes(StandardCharsets.UTF_8));
                        keysAndArgs.add(ttl.getBytes(StandardCharsets.UTF_8));
                        keysAndArgs.add(String.valueOf(MessageConstant.IM_APP_KEY_CONN_QUOTA_STALE_SECONDS)
                                .getBytes(StandardCharsets.UTF_8));
                        liveNodes.forEach(node -> keysAndArgs.add(node.getBytes(StandardCharsets.UTF_8)));
                        connection.scriptingCommands().eval(SYNC_SCRIPT_BYTES, ReturnType.INTEGER,
                                MessageConstant.IM_NODE_QUOTA_SCRIPT_KEY_COUNT, keysAndArgs.toArray(byte[][]::new));
                    }
                    return null;
                });
            } finally {
                for (int index = locks.size() - 1; index >= 0; index--) {
                    locks.get(index).unlock();
                }
            }
            for (int index = 0; index < batch.size(); index++) {
                QuotaSyncItem item = batch.get(index);
                if (results != null && index < results.size()
                        && Long.valueOf(MessageConstant.IM_APP_KEY_CONN_QUOTA_LUA_OK).equals(results.get(index))
                        && LocalNodeConnCounter.get(item.appKey()) == 0L) {
                    LocalNodeConnCounter.removeIfZero(item.appKey());
                }
            }
            return true;
        } catch (Exception e) {
            log.warn("批量同步 appKey 配额失败，等待后续维护 batchSize={}", batch.size(), e);
            return false;
        }
    }

    /** 单条配额维护参数，避免业务方法使用无类型的 Map 传递参数。 */
    private record QuotaSyncItem(String appKey) {
    }

    private static ReentrantLock quotaLock(String appKey) {
        return QUOTA_LOCKS[(appKey.hashCode() & Integer.MAX_VALUE) % QUOTA_LOCKS.length];
    }

    private static ReentrantLock[] createQuotaLocks() {
        ReentrantLock[] locks = new ReentrantLock[MessageConstant.IM_APP_KEY_CONN_QUOTA_LOCK_STRIPES];
        for (int index = 0; index < locks.length; index++) {
            locks[index] = new ReentrantLock();
        }
        return locks;
    }

    /** 按固定序号获取批次涉及的分段锁，避免多个批次交叉时产生锁顺序反转。 */
    private static List<ReentrantLock> batchLocks(List<QuotaSyncItem> batch) {
        LinkedHashSet<ReentrantLock> unique = new LinkedHashSet<>();
        batch.stream().map(QuotaSyncItem::appKey).map(AppKeyConnQuotaSupport::quotaLock)
                .sorted(Comparator.comparingInt(lock -> lockIndex(lock))).forEach(unique::add);
        return new ArrayList<>(unique);
    }

    private static int lockIndex(ReentrantLock target) {
        for (int index = 0; index < QUOTA_LOCKS.length; index++) {
            if (QUOTA_LOCKS[index] == target) {
                return index;
            }
        }
        throw new IllegalStateException("配额分段锁不属于当前锁表");
    }
    private static DefaultRedisScript<Long> script(LuaScriptEnum lua) {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(lua.getScript());
        redisScript.setResultType(Long.class);
        return redisScript;
    }

    private static Long eval(DefaultRedisScript<Long> script, String appKey, String... args) {
        StringRedisTemplate redis = CacheFactory.STRING_REDIS.instance();
        String key = CacheConstant.buildAppKeyConnQuotaHashCacheKey(appKey);
        return redis.execute(script, List.of(key), (Object[]) args);
    }
}
