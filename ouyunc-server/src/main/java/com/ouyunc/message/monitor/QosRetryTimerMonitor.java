package com.ouyunc.message.monitor;

import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.cache.Cache;
import com.ouyunc.message.schedule.TimerTaskWrapper;

/**
 * QoS 定时重试任务监控（基于 {@link TimerTaskWrapper#timerTaskCaffeine}）。
 */
public final class QosRetryTimerMonitor {

    private QosRetryTimerMonitor() {
        throw new AssertionError("Instantiation not supported");
    }

    public static QosRetryTimerMetrics snapshot() {
        LoadingCache<String, TimerTaskWrapper> cache = resolveLoadingCache(TimerTaskWrapper.timerTaskCaffeine);
        if (cache == null) {
            return emptyMetrics();
        }
        int maxCapacity = MessageConstant.TIMER_TASK_CACHE_MAX_SIZE;
        long activeTasks = cache.estimatedSize();
        double utilization = maxCapacity > 0 ? (double) activeTasks / maxCapacity : 0.0;
        CacheStats stats = cache.stats();
        return new QosRetryTimerMetrics(
                activeTasks,
                maxCapacity,
                utilization,
                TimerTaskWrapper.sizeEvictionCount(),
                stats.hitCount(),
                stats.missCount(),
                stats.evictionCount(),
                stats.requestCount()
        );
    }

    @SuppressWarnings("unchecked")
    private static LoadingCache<String, TimerTaskWrapper> resolveLoadingCache(Cache<String, TimerTaskWrapper> cache) {
        if (cache == null) {
            return null;
        }
        Object instance = cache.instance();
        if (instance instanceof LoadingCache<?, ?> loadingCache) {
            return (LoadingCache<String, TimerTaskWrapper>) loadingCache;
        }
        return null;
    }

    private static QosRetryTimerMetrics emptyMetrics() {
        return new QosRetryTimerMetrics(0, 0, 0.0, 0, 0, 0, 0, 0);
    }
}
