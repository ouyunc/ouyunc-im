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

    public static void decrement(String appKey) {
        if (StringUtils.isBlank(appKey)) {
            return;
        }
        BY_APP_KEY.compute(appKey, (key, counter) -> {
            if (counter == null) {
                return null;
            }
            long left = counter.decrementAndGet();
            return left <= 0L ? null : counter;
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
            long value = counter.get();
            if (value > 0L) {
                snapshot.put(appKey, String.valueOf(value));
            }
        });
        return snapshot;
    }
}
