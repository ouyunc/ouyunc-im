package com.ouyunc.message.cluster.lease;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.LuaScriptEnum;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.cache.config.CacheFactory;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    private AppKeyConnQuotaSupport() {
    }

    public static boolean tryReserve(String appKey, long maxConnections) {
        if (StringUtils.isBlank(appKey)) {
            return false;
        }
        Long result = eval(RESERVE_SCRIPT, appKey, NodeLeaseKeeper.localNodeId(),
                String.valueOf(maxConnections),
                String.valueOf(MessageConstant.IM_APP_KEY_CONN_QUOTA_TTL_SECONDS));
        return result != null && result == MessageConstant.IM_APP_KEY_CONN_QUOTA_LUA_OK;
    }

    public static void release(String appKey) {
        if (StringUtils.isBlank(appKey)) {
            return;
        }
        try {
            eval(RELEASE_SCRIPT, appKey, NodeLeaseKeeper.localNodeId());
        } catch (Exception e) {
            log.warn("释放 appKey 连接配额失败 appKey={}", appKey, e);
        }
    }

    /**
     * 关连钩子跑在 EventLoop 上，Lua 释放必须离开 IO 线程。
     * 提交失败时本机计数已减，下一次心跳 SYNC 会按快照把 Redis field 写回去。
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
     * 快照过期或被新一拍替换即停止清理，禁止用历史成员集合删除有效配额。
     * 无本机连接时不扫描整个 Redis；无活节点维护的孤儿 HASH 由原有 TTL 自然回收。
     */
    public static void syncAfterHeartbeat(NodeLeaseSnapshot snapshot) {
        if (!NodeLeaseKeeper.isCurrentSnapshot(snapshot)) {
            return;
        }
        Map<String, String> local = LocalNodeConnCounter.snapshot();
        if (local.isEmpty()) {
            return;
        }
        List<String> liveNodes = new ArrayList<>(snapshot.leases().keySet());
        List<QuotaSyncItem> batch = new ArrayList<>(MessageConstant.IM_NODE_QUOTA_SYNC_BATCH);
        for (Map.Entry<String, String> entry : local.entrySet()) {
            batch.add(new QuotaSyncItem(entry.getKey(), entry.getValue()));
            if (batch.size() >= MessageConstant.IM_NODE_QUOTA_SYNC_BATCH) {
                if (!syncBatch(snapshot, liveNodes, batch)) {
                    return;
                }
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            syncBatch(snapshot, liveNodes, batch);
        }
    }

    /** 每个 appKey 的 Lua 仍保持同槽原子性；管道仅合并网络往返，不作为跨 key 事务。 */
    private static boolean syncBatch(NodeLeaseSnapshot snapshot, List<String> liveNodes, List<QuotaSyncItem> batch) {
        if (!NodeLeaseKeeper.isCurrentSnapshot(snapshot)) {
            return false;
        }
        try {
            StringRedisTemplate redis = CacheFactory.STRING_REDIS.instance();
            String nodeId = NodeLeaseKeeper.localNodeId();
            String ttl = String.valueOf(MessageConstant.IM_APP_KEY_CONN_QUOTA_TTL_SECONDS);
            List<Object> results = redis.executePipelined((RedisCallback<Object>) connection -> {
                for (QuotaSyncItem item : batch) {
                    List<byte[]> keysAndArgs = new ArrayList<>();
                    keysAndArgs.add(CacheConstant.buildAppKeyConnQuotaHashCacheKey(item.appKey())
                            .getBytes(StandardCharsets.UTF_8));
                    keysAndArgs.add(nodeId.getBytes(StandardCharsets.UTF_8));
                    keysAndArgs.add(item.count().getBytes(StandardCharsets.UTF_8));
                    keysAndArgs.add(ttl.getBytes(StandardCharsets.UTF_8));
                    liveNodes.forEach(node -> keysAndArgs.add(node.getBytes(StandardCharsets.UTF_8)));
                    connection.scriptingCommands().eval(SYNC_SCRIPT_BYTES, ReturnType.INTEGER,
                            MessageConstant.IM_NODE_QUOTA_SCRIPT_KEY_COUNT, keysAndArgs.toArray(byte[][]::new));
                }
                return null;
            });
            for (int index = 0; index < batch.size(); index++) {
                QuotaSyncItem item = batch.get(index);
                if (results != null && index < results.size()
                        && Long.valueOf(MessageConstant.IM_APP_KEY_CONN_QUOTA_LUA_OK).equals(results.get(index))
                        && Long.parseLong(item.count()) == 0L) {
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
    private record QuotaSyncItem(String appKey, String count) {
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
