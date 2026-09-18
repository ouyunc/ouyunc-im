package com.ouyunc.message.cluster.lease;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 本机连接计数：与本地注册表同步增减，随节点租约写入 {@code {nodeId}} 槽。
 * 不跟登录 Lua 同槽，避免 Cluster CROSSSLOT，也不再 bind 后再 HINCRBY。
 */
public final class LocalNodeConnCounter {

    private static final ConcurrentHashMap<String, AtomicLong> BY_APP_KEY = new ConcurrentHashMap<>();

    private LocalNodeConnCounter() {
    }

    public static void increment(String appKey) {
        if (StringUtils.isBlank(appKey)) {
            return;
        }
        BY_APP_KEY.compute(appKey, (key, counter) -> {
            if (counter == null) {
                return new AtomicLong(1L);
            }
            counter.incrementAndGet();
            return counter;
        });
    }

    /**
     * B5：原子预占。当且仅当当前本机计数 &lt; exclusiveMax 时 +1。
     *
     * @param exclusiveMax 本机允许的上限（通常为 maxConnections - 其它节点计数）
     * @return true 表示已 +1
     */
    public static boolean tryIncrementIfBelow(String appKey, long exclusiveMax) {
        if (StringUtils.isBlank(appKey) || exclusiveMax <= 0L) {
            return false;
        }
        final boolean[] ok = {false};
        BY_APP_KEY.compute(appKey, (key, counter) -> {
            long current = counter == null ? 0L : Math.max(0L, counter.get());
            if (current >= exclusiveMax) {
                return counter;
            }
            ok[0] = true;
            if (counter == null) {
                return new AtomicLong(1L);
            }
            counter.incrementAndGet();
            return counter;
        });
        return ok[0];
    }

    public static void decrement(String appKey) {
        if (StringUtils.isBlank(appKey)) {
            return;
        }
        BY_APP_KEY.compute(appKey, (key, counter) -> {
            if (counter == null) {
                return null;
            }
            long left = counter.decrementAndGet();
            if (left <= 0L) {
                counter.set(0L);
            }
            // 保留 0 计数，心跳 SYNC 才能 HDEL 本节点 field；SYNC 后再 prune
            return counter;
        });
    }

    public static long get(String appKey) {
        if (StringUtils.isBlank(appKey)) {
            return 0L;
        }
        AtomicLong counter = BY_APP_KEY.get(appKey);
        if (counter == null) {
            return 0L;
        }
        return Math.max(0L, counter.get());
    }

    public static long total() {
        long sum = 0L;
        for (AtomicLong counter : BY_APP_KEY.values()) {
            sum += Math.max(0L, counter.get());
        }
        return sum;
    }

    /**
     * 供租约心跳写入 Redis HASH（field=appKey）。
     */
    public static Map<String, String> snapshot() {
        Map<String, String> snapshot = new HashMap<>();
        BY_APP_KEY.forEach((appKey, counter) -> {
            long value = Math.max(0L, counter.get());
            snapshot.put(appKey, String.valueOf(value));
        });
        return snapshot;
    }

    /**
     * SYNC 把 0 写回 Redis 后摘掉本机空计数，避免历史 appKey 常驻。
     */
    public static void removeIfZero(String appKey) {
        if (StringUtils.isBlank(appKey)) {
            return;
        }
        BY_APP_KEY.compute(appKey, (key, counter) -> {
            if (counter == null || counter.get() <= 0L) {
                return null;
            }
            return counter;
        });
    }
}
