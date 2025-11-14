package com.ouyunc.base.monitor;

import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.ouyunc.base.executor.ThreadPoolId;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.executor.ThreadPoolManager.ThreadPoolMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 资源监控管理器
 * 统一监控线程池和 Caffeine 缓存的运行状态
 *
 * @author fzx
 */
public final class ResourceMonitor {

    private static final Logger log = LoggerFactory.getLogger(ResourceMonitor.class);

    /**
     * 注册的 Caffeine 缓存实例
     */
    private static final Map<String, LoadingCache<?, ?>> REGISTERED_CACHES = new ConcurrentHashMap<>();

    /**
     * 监控任务调度器
     */
    private static volatile ScheduledExecutorService scheduler;

    /**
     * 是否已启动监控
     */
    private static volatile boolean monitoring = false;

    private ResourceMonitor() {
        throw new AssertionError("Instantiation not supported");
    }

    /**
     * 注册 Caffeine 缓存实例用于监控
     *
     * @param cacheName 缓存名称
     * @param cache     缓存实例
     */
    public static void registerCache(String cacheName, LoadingCache<?, ?> cache) {
        if (cacheName == null || cache == null) {
            log.warn("注册缓存失败：cacheName 或 cache 不能为空");
            return;
        }
        REGISTERED_CACHES.put(cacheName, cache);
        log.debug("已注册缓存监控: {}", cacheName);
    }

    /**
     * 注册缓存实例（通过反射获取 CaffeineLocalCache 的实例）
     * 
     * @param cache 实现了 instance() 方法返回 LoadingCache 的缓存对象
     */
    public static void registerCache(Object cache) {
        if (cache == null) {
            return;
        }
        try {
            // 尝试通过反射获取缓存名称和实例
            String cacheName = null;
            LoadingCache<?, ?> loadingCache = null;
            
            // 尝试获取 getCacheName() 方法
            try {
                java.lang.reflect.Method getNameMethod = cache.getClass().getMethod("getCacheName");
                cacheName = (String) getNameMethod.invoke(cache);
            } catch (Exception e) {
                // 如果没有 getCacheName 方法，使用类名
                cacheName = cache.getClass().getSimpleName();
            }
            
            // 尝试获取 instance() 方法
            try {
                java.lang.reflect.Method instanceMethod = cache.getClass().getMethod("instance");
                Object instance = instanceMethod.invoke(cache);
                if (instance instanceof LoadingCache) {
                    loadingCache = (LoadingCache<?, ?>) instance;
                }
            } catch (Exception e) {
                log.warn("无法从缓存对象获取 LoadingCache 实例: {}", cache.getClass().getName());
                return;
            }
            
            if (loadingCache != null) {
                registerCache(cacheName, loadingCache);
            }
        } catch (Exception e) {
            log.warn("注册缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 取消注册缓存
     *
     * @param cacheName 缓存名称
     */
    public static void unregisterCache(String cacheName) {
        REGISTERED_CACHES.remove(cacheName);
        log.debug("已取消注册缓存监控: {}", cacheName);
    }

    /**
     * 启动定期监控（默认每5分钟输出一次）
     */
    public static void startMonitoring() {
        startMonitoring(5, TimeUnit.MINUTES);
    }

    /**
     * 启动定期监控
     *
     * @param period 监控周期
     * @param unit   时间单位
     */
    public static void startMonitoring(long period, TimeUnit unit) {
        if (monitoring) {
            log.warn("监控已启动，无需重复启动");
            return;
        }
        scheduler = ThreadPoolManager.systemClockScheduler();
        scheduler.scheduleAtFixedRate(
                ResourceMonitor::logAllMetrics,
                0,
                period,
                unit
        );
        monitoring = true;
        log.info("资源监控已启动，监控周期: {} {}", period, unit);
    }

    /**
     * 停止监控
     */
    public static void stopMonitoring() {
        monitoring = false;
        log.info("资源监控已停止");
    }

    /**
     * 获取所有线程池指标
     */
    public static Map<ThreadPoolId, ThreadPoolMetrics> getThreadPoolMetrics() {
        return ThreadPoolManager.metrics();
    }

    /**
     * 获取所有缓存指标
     */
    public static Map<String, CacheMetrics> getCacheMetrics() {
        Map<String, CacheMetrics> metrics = new HashMap<>();
        REGISTERED_CACHES.forEach((name, cache) -> {
            try {
                metrics.put(name, CacheMetrics.from(name, cache));
            } catch (Exception e) {
                log.warn("获取缓存 [{}] 指标失败: {}", name, e.getMessage());
            }
        });
        return metrics;
    }

    /**
     * 获取已注册的缓存名称列表
     */
    public static Set<String> getRegisteredCacheNames() {
        return Collections.unmodifiableSet(REGISTERED_CACHES.keySet());
    }

    /**
     * 获取所有资源指标（线程池 + 缓存）
     */
    public static ResourceMetricsSnapshot getAllMetrics() {
        return new ResourceMetricsSnapshot(
                getThreadPoolMetrics(),
                getCacheMetrics()
        );
    }

    /**
     * 输出所有指标到日志
     */
    public static void logAllMetrics() {
        log.info("========== 资源监控报告 ==========");
        logThreadPoolMetrics();
        logCacheMetrics();
        log.info("==================================");
    }

    /**
     * 输出线程池指标
     */
    public static void logThreadPoolMetrics() {
        Map<ThreadPoolId, ThreadPoolMetrics> metrics = getThreadPoolMetrics();
        if (metrics.isEmpty()) {
            log.info("【线程池】无可用指标");
            return;
        }
        log.info("【线程池监控】");
        metrics.forEach((id, m) -> {
            if (m.activeThreads() >= 0) {
                log.info("  {}: 活跃线程={}, 池大小={}, 已完成任务={}, 总任务={}, 队列大小={}, 状态={}",
                        id.getConfigKey(),
                        m.activeThreads(),
                        m.poolSize(),
                        m.completedTaskCount(),
                        m.taskCount(),
                        m.queueSize() >= 0 ? m.queueSize() : "N/A",
                        m.shutdown() ? "已关闭" : (m.terminated() ? "已终止" : "运行中")
                );
            } else {
                log.info("  {}: 状态={}", id.getConfigKey(), m.shutdown() ? "已关闭" : "运行中");
            }
        });
    }

    /**
     * 输出缓存指标
     */
    public static void logCacheMetrics() {
        Map<String, CacheMetrics> metrics = getCacheMetrics();
        if (metrics.isEmpty()) {
            log.info("【缓存】无可用指标");
            return;
        }
        log.info("【缓存监控】");
        metrics.forEach((name, m) -> {
            double hitRate = m.hitRate();
            log.info("  {}: 大小={}, 命中率={:.2f}%, 命中={}, 未命中={}, 加载={}, 淘汰={}, 加载耗时={}ms",
                    name,
                    m.size(),
                    hitRate * 100,
                    m.hitCount(),
                    m.missCount(),
                    m.loadCount(),
                    m.evictionCount(),
                    m.totalLoadTime() / 1_000_000.0  // 纳秒转毫秒
            );
        });
    }

    /**
     * 检查线程池健康状态
     *
     * @return 健康检查结果
     */
    public static HealthCheckResult checkHealth() {
        HealthCheckResult result = new HealthCheckResult();
        Map<ThreadPoolId, ThreadPoolMetrics> poolMetrics = getThreadPoolMetrics();

        // 检查线程池
        poolMetrics.forEach((id, m) -> {
            if (m.shutdown() || m.terminated()) {
                result.addIssue("线程池 [" + id.getConfigKey() + "] 已关闭或终止");
            }
            // 检查队列是否接近满载（仅对有队列的线程池）
            if (m.queueSize() > 0) {
                // 如果队列大小超过1000，可能存在问题
                if (m.queueSize() > 1000) {
                    result.addWarning("线程池 [" + id.getConfigKey() + "] 队列积压: " + m.queueSize());
                }
            }
        });

        // 检查缓存命中率
        Map<String, CacheMetrics> cacheMetrics = getCacheMetrics();
        cacheMetrics.forEach((name, m) -> {
            double hitRate = m.hitRate();
            // 命中率低于50%可能存在问题
            if (m.requestCount() > 100 && hitRate < 0.5) {
                result.addWarning("缓存 [" + name + "] 命中率过低: " + String.format("%.2f%%", hitRate * 100));
            }
        });

        return result;
    }

    /**
     * 缓存指标
     */
    public record CacheMetrics(
            String cacheName,
            long size,
            long hitCount,
            long missCount,
            long loadCount,
            long evictionCount,
            long totalLoadTime,
            long requestCount,
            double hitRate
    ) {
        public static CacheMetrics from(String cacheName, LoadingCache<?, ?> cache) {
            CacheStats stats = cache.stats();
            long requestCount = stats.requestCount();
            double hitRate = requestCount > 0 ? stats.hitRate() : 0.0;

            return new CacheMetrics(
                    cacheName,
                    cache.estimatedSize(),
                    stats.hitCount(),
                    stats.missCount(),
                    stats.loadCount(),
                    stats.evictionCount(),
                    stats.totalLoadTime(),
                    requestCount,
                    hitRate
            );
        }
    }

    /**
     * 资源指标快照
     */
    public record ResourceMetricsSnapshot(
            Map<ThreadPoolId, ThreadPoolMetrics> threadPools,
            Map<String, CacheMetrics> caches
    ) {
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("ResourceMetricsSnapshot{\n");
            sb.append("  threadPools=").append(threadPools.size()).append("个\n");
            sb.append("  caches=").append(caches.size()).append("个\n");
            sb.append("}");
            return sb.toString();
        }
    }

    /**
     * 健康检查结果
     */
    public static class HealthCheckResult {
        private final List<String> issues = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        public void addIssue(String issue) {
            issues.add(issue);
        }

        public void addWarning(String warning) {
            warnings.add(warning);
        }

        public List<String> getIssues() {
            return Collections.unmodifiableList(issues);
        }

        public List<String> getWarnings() {
            return Collections.unmodifiableList(warnings);
        }

        public boolean isHealthy() {
            return issues.isEmpty() && warnings.isEmpty();
        }

        @Override
        public String toString() {
            if (isHealthy()) {
                return "健康检查通过";
            }
            StringBuilder sb = new StringBuilder();
            if (!issues.isEmpty()) {
                sb.append("问题: ").append(issues).append("\n");
            }
            if (!warnings.isEmpty()) {
                sb.append("警告: ").append(warnings);
            }
            return sb.toString();
        }
    }
}

