package com.ouyunc.message.cluster.lease;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.LuaScriptEnum;
import com.ouyunc.cache.config.CacheFactory;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private static final DefaultRedisScript<Long> SYNC_SCRIPT = script(
            LuaScriptEnum.APP_KEY_CONN_SYNC_SCRIPT);

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
     * 心跳：把本机计数写回配额 HASH，并删掉已不在租约里的节点 field。
     * 本机连接为 0 时仍 SCAN 配额 key，避免死节点残留要等 TTL。
     */
    public static void syncAfterHeartbeat(Collection<String> liveNodeIds) {
        String nodeId = NodeLeaseKeeper.localNodeId();
        String ttl = String.valueOf(MessageConstant.IM_APP_KEY_CONN_QUOTA_TTL_SECONDS);
        List<String> liveArgs = new ArrayList<>();
        if (liveNodeIds != null) {
            for (String id : liveNodeIds) {
                if (StringUtils.isNotBlank(id)) {
                    liveArgs.add(id);
                }
            }
        }
        if (!liveArgs.contains(nodeId)) {
            liveArgs.add(nodeId);
        }
        Map<String, String> local = LocalNodeConnCounter.snapshot();
        if (local.isEmpty()) {
            syncOrphanQuotaHashes(nodeId, ttl, liveArgs);
            return;
        }
        for (Map.Entry<String, String> entry : local.entrySet()) {
            try {
                eval(SYNC_SCRIPT, entry.getKey(), syncArgs(nodeId, entry.getValue(), ttl, liveArgs));
                if ("0".equals(entry.getValue())) {
                    LocalNodeConnCounter.removeIfZero(entry.getKey());
                }
            } catch (Exception e) {
                log.warn("同步 appKey 连接配额失败 appKey={}", entry.getKey(), e);
            }
        }
    }

    private static String[] syncArgs(String nodeId, String count, String ttl, List<String> liveArgs) {
        List<String> args = new ArrayList<>(
                MessageConstant.IM_APP_KEY_CONN_QUOTA_SYNC_FIXED_ARGV + liveArgs.size());
        args.add(nodeId);
        args.add(count);
        args.add(ttl);
        args.addAll(liveArgs);
        return args.toArray(new String[0]);
    }

    /**
     * 本机无连接时仍要清死节点 field：SCAN 配额 HASH，以 count=0 跑 SYNC。
     */
    private static void syncOrphanQuotaHashes(String nodeId, String ttl, List<String> liveArgs) {
        String[] args = syncArgs(nodeId, "0", ttl, liveArgs);
        StringRedisTemplate redis = CacheFactory.STRING_REDIS.instance();
        for (String key : scanQuotaKeys(redis)) {
            try {
                redis.execute(SYNC_SCRIPT, List.of(key), (Object[]) args);
            } catch (Exception e) {
                log.warn("空节点同步配额失败 key={}", key, e);
            }
        }
    }

    private static Set<String> scanQuotaKeys(StringRedisTemplate redis) {
        Set<String> keys = new HashSet<>();
        String pattern = CacheConstant.appKeyConnQuotaKeyPattern();
        try {
            redis.execute((RedisCallback<Void>) connection -> {
                ScanOptions options = ScanOptions.scanOptions().match(pattern).count(200).build();
                try (Cursor<byte[]> cursor = connection.scan(options)) {
                    while (cursor.hasNext()) {
                        keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                    }
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("扫描 appKey 配额 key 失败 pattern={}", pattern, e);
        }
        return keys;
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
