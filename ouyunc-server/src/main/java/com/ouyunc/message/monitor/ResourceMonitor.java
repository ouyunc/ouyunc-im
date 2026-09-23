package com.ouyunc.message.monitor;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.google.common.collect.Lists;
import com.ouyunc.base.executor.ThreadPoolId;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.executor.ThreadPoolManager.ThreadPoolMetrics;
import com.ouyunc.core.listener.MessageEventMulticaster;
import com.ouyunc.core.listener.metrics.DisruptorListenerExecSnapshot;
import com.ouyunc.core.listener.metrics.DisruptorRingMetrics;
import com.ouyunc.message.cluster.lease.NodeLeaseKeeper;
import com.ouyunc.message.context.MessageServerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 资源监控管理器
 * 统一监控线程池、Caffeine 缓存及事件 Disruptor（RingBuffer + 监听器耗时）的运行状态
 *
 * @author fzx
 */
public final class ResourceMonitor {

    private static final Logger log = LoggerFactory.getLogger(ResourceMonitor.class);

    /**
     * 注册的 Caffeine 基础缓存实例，同时支持普通 Cache 和 LoadingCache
     */
    private static final Map<String, Cache<?, ?>> REGISTERED_CACHES = new ConcurrentHashMap<>();

    /**
     * 监控任务调度器
     */
    private static volatile ScheduledExecutorService scheduler;

    /**
     * 是否已启动监控
     */
    private static volatile boolean monitoring = false;

    /**
     * Disruptor RingBuffer 指标（由 {@link #registerDisruptorMetrics(MessageEventMulticaster)} 注入，默认空）
     */
    private static volatile Supplier<List<DisruptorRingMetrics>> disruptorMetricsSupplier = Collections::emptyList;

    /** QoS 定时重试任务占用超过容量该比例时打预警 */
    private static final double QOS_RETRY_UTIL_WARN = 0.80;
    private static final double QOS_RETRY_UTIL_CRITICAL = 0.95;

    private ResourceMonitor() {
        throw new AssertionError("Instantiation not supported");
    }

    /**
     * 注册 Caffeine 缓存实例用于监控
     *
     * @param cacheName 缓存名称
     * @param cache     缓存实例
     */
    public static void registerCache(String cacheName, Cache<?, ?> cache) {
        if (cacheName == null || cache == null) {
            log.warn("注册缓存失败：cacheName 或 cache 不能为空");
            return;
        }
        REGISTERED_CACHES.put(cacheName, cache);
        log.debug("已注册缓存监控: {}", cacheName);
    }

    /**
     * 注册缓存实例：项目缓存优先通过接口解包，其他封装兼容 instance() 方法。
     * 
     * @param cache 原生 Caffeine Cache，或 instance() 返回该类型的缓存封装
     */
    public static void registerCache(Object cache) {
        if (cache == null) {
            return;
        }
        String cacheName = resolveCacheName(cache);
        try {
            Cache<?, ?> nativeCache = resolveCaffeineCache(cache);
            if (nativeCache == null) {
                log.warn("无法从缓存对象获取 Caffeine Cache 实例: name={} type={}",
                        cacheName, cache.getClass().getName());
                return;
            }
            registerCache(cacheName, nativeCache);
        } catch (Exception e) {
            log.warn("注册缓存失败: name={} type={}", cacheName, cache.getClass().getName(), e);
        }
    }

    private static String resolveCacheName(Object cache) {
        try {
            java.lang.reflect.Method getNameMethod = cache.getClass().getMethod("getCacheName");
            return (String) getNameMethod.invoke(cache);
        } catch (Exception e) {
            return cache.getClass().getSimpleName();
        }
    }

    /**
     * 监控只读取 stats/estimatedSize，不依赖自动加载能力，也不触发 loader。
     * 普通 Cache 与 LoadingCache 均实现 Caffeine Cache 接口。
     */
    private static Cache<?, ?> resolveCaffeineCache(Object cache) {
        if (cache instanceof Cache<?, ?> nativeCache) {
            return nativeCache;
        }
        if (cache instanceof com.ouyunc.cache.Cache<?, ?> wrapped) {
            Object instance = wrapped.instance();
            return instance instanceof Cache<?, ?> nativeCache ? nativeCache : null;
        }
        try {
            java.lang.reflect.Method instanceMethod = cache.getClass().getMethod("instance");
            Object instance = instanceMethod.invoke(cache);
            if (instance instanceof Cache<?, ?> nativeCache) {
                return nativeCache;
            }
        } catch (ReflectiveOperationException ignored) {
            // 不支持的封装由调用方携带缓存名称统一记录，避免重复告警。
        }
        return null;
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
     * 注册事件多播器，用于定期输出 Disruptor 指标（RingBuffer 快照 + 各监听器累计耗时/错误；监听器侧仅在异步消费路径增加 nanoTime 与原子累加）。
     */
    public static void registerDisruptorMetrics(MessageEventMulticaster multicaster) {
        if (multicaster == null) {
            disruptorMetricsSupplier = Collections::emptyList;
            return;
        }
        disruptorMetricsSupplier = multicaster::snapshotDisruptorMetrics;
        log.debug("已注册 Disruptor 事件环监控");
    }

    /**
     * 启动定期监控（默认每5分钟输出一次）
     */
    public static void start() {
        start(1, TimeUnit.MINUTES);
    }

    /**
     * 启动定期监控
     *
     * @param period 监控周期
     * @param unit   时间单位
     */
    public static void start(long period, TimeUnit unit) {
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
     * 停止监控（不再输出指标；调度器由 {@link ThreadPoolManager#shutdownAll()} 统一关闭）
     */
    public static void stopMonitoring() {
        monitoring = false;
        scheduler = null;
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
     * QoS SERVER 定时重试任务监控快照。
     */
    public static QosRetryTimerMetrics getQosRetryTimerMetrics() {
        return QosRetryTimerMonitor.snapshot();
    }

    /**
     * 获取所有资源指标（线程池 + 缓存 + Disruptor）
     */
    public static ResourceMetricsSnapshot getAllMetrics() {
        return new ResourceMetricsSnapshot(
                getThreadPoolMetrics(),
                getCacheMetrics(),
                getDisruptorMetrics()
        );
    }

    /**
     * 当前已懒加载创建的 Disruptor 环指标快照
     */
    public static List<DisruptorRingMetrics> getDisruptorMetrics() {
        try {
            List<DisruptorRingMetrics> list = disruptorMetricsSupplier.get();
            return list == null ? Lists.newArrayList() : List.copyOf(list);
        } catch (Exception e) {
            log.warn("获取 Disruptor 指标失败: {}", e.getMessage());
            return Lists.newArrayList();
        }
    }

    /**
     * 输出所有指标到日志
     */
    public static void logAllMetrics() {
        if (!monitoring) {
            return;
        }
        log.info("========== 资源监控报告 ==========");
        logThreadPoolMetrics();
        logCacheMetrics();
        logClusterTopologyMetrics();
        logQosRetryTimerMetrics();
        logQosRetryCancelMetrics();
        logDisruptorMetrics();
        log.info("==================================");
    }

    /** pending 超过约 80% 槽位视为背压偏高（与 {@link #collectDisruptorWarnings} 一致） */
    private static final int RING_PENDING_WARN_NUMERATOR = 8;
    private static final int RING_PENDING_WARN_DENOMINATOR = 10;
    /** 监听器平均耗时超过该值（毫秒）打预警 */
    private static final double LISTENER_AVG_MS_WARN = 2_000.0;
    /** 监听器单次最大耗时超过该值（毫秒）打预警 */
    private static final double LISTENER_MAX_MS_WARN = 30_000.0;

    /**
     * 输出 Disruptor RingBuffer 指标（仅已创建的多播路由），并对背压/失败/耗时打 {@link Logger#warn} 级别预警行。
     */
    public static void logDisruptorMetrics() {
        List<DisruptorRingMetrics> metrics = getDisruptorMetrics();
        if (metrics.isEmpty()) {
            log.info("【事件 Disruptor】暂无已创建的 Ring（未异步发布过对应事件或尚未懒加载）");
            return;
        }
        log.info("【事件 Disruptor】");
        int warnCount = 0;
        for (DisruptorRingMetrics m : metrics) {
            log.info(
                    "  [环] eventType={}, ring={}, bufferSize={}槽, cursor={}序号, minGating={}序号, pending≈{}序号, remaining={}槽, published={}次, dropped={}次, started={}",
                    m.eventTypeName(),
                    m.ringName(),
                    m.bufferSize(),
                    m.cursor(),
                    m.minimumGatingSequence(),
                    m.pendingSequences(),
                    m.remainingCapacity(),
                    m.publishedEvents(),
                    m.droppedEvents(),
                    m.disruptorStarted()
            );
            List<DisruptorListenerExecSnapshot> listeners = m.listenerStats();
            if (listeners == null || listeners.isEmpty()) {
                log.info("    （无监听器统计）");
            } else {
                boolean any = false;
                for (DisruptorListenerExecSnapshot s : listeners) {
                    if (s.invocations() == 0) {
                        continue;
                    }
                    any = true;
                    log.info(
                            "    [监听器] order={}, class={}, invocations={}次, errors={}次, avg={}ms, max={}ms, total={}ms",
                            s.order(),
                            s.listenerClassName(),
                            s.invocations(),
                            s.errors(),
                            String.format("%.3f", s.avgMs()),
                            String.format("%.3f", s.maxMs()),
                            String.format("%.3f", s.totalNanos() / 1_000_000.0)
                    );
                }
                if (!any) {
                    log.info("    （本周期各监听器尚无消费次数，或事件尚未异步发布到本环）");
                }
            }
            List<String> ringWarnings = new ArrayList<>();
            collectDisruptorWarnings(m, ringWarnings::add);
            for (String msg : ringWarnings) {
                log.warn("[事件Disruptor预警] {}", msg);
            }
            warnCount += ringWarnings.size();
        }
        if (warnCount > 0) {
            log.warn("【事件 Disruptor】本周期共 {} 条预警（关键字 [事件Disruptor预警] 便于检索）", warnCount);
        }
    }

    /**
     * 事件环与监听器预警规则（与 {@link #checkHealth()} 共用）。
     */
    private static void collectDisruptorWarnings(DisruptorRingMetrics m, Consumer<String> out) {
        int buf = m.bufferSize();
        long pending = m.pendingSequences();
        long rem = m.remainingCapacity();
        if (m.droppedEvents() > 0) {
            out.accept(String.format(
                    "[丢弃] eventType=%s ring=%s 异常类事件因环满被非阻塞丢弃=%d",
                    m.eventTypeName(), m.ringName(), m.droppedEvents()));
        }
        if (!m.disruptorStarted() && (m.publishedEvents() > 0 || m.cursor() > 0)) {
            out.accept(String.format(
                    "[严重] eventType=%s ring=%s Disruptor 未处于 started 状态但已有 cursor/published",
                    m.eventTypeName(), m.ringName()));
        }
        if (buf > 0) {
            if (rem <= 0) {
                out.accept(String.format(
                        "[严重] eventType=%s ring=%s Ring 已满或剩余容量=%d publish 可能阻塞 pending≈%d buffer=%d",
                        m.eventTypeName(), m.ringName(), rem, pending, buf));
            } else {
                if (pending * RING_PENDING_WARN_DENOMINATOR > buf * (long) RING_PENDING_WARN_NUMERATOR) {
                    out.accept(String.format(
                            "[背压] eventType=%s ring=%s pending≈%d 已超过约 %d%% 槽位 buffer=%d remaining=%d",
                            m.eventTypeName(), m.ringName(), pending,
                            RING_PENDING_WARN_NUMERATOR * 100 / RING_PENDING_WARN_DENOMINATOR, buf, rem));
                } else if (rem < buf / 10L) {
                    out.accept(String.format(
                            "[背压] eventType=%s ring=%s 剩余槽位不足 10%% remaining=%d buffer=%d pending≈%d",
                            m.eventTypeName(), m.ringName(), rem, buf, pending));
                }
            }
        }
        List<DisruptorListenerExecSnapshot> listeners = m.listenerStats();
        if (listeners == null) {
            return;
        }
        for (DisruptorListenerExecSnapshot s : listeners) {
            if (s.invocations() == 0) {
                continue;
            }
            if (s.errors() > 0) {
                out.accept(String.format(
                        "[失败] eventType=%s ring=%s order=%d class=%s errors=%d / invocations=%d",
                        m.eventTypeName(), m.ringName(), s.order(), s.listenerClassName(), s.errors(), s.invocations()));
            }
            if (s.avgMs() > LISTENER_AVG_MS_WARN) {
                out.accept(String.format(
                        "[耗时] eventType=%s ring=%s order=%d class=%s 平均耗时 %.2fms > %.0fms",
                        m.eventTypeName(), m.ringName(), s.order(), s.listenerClassName(), s.avgMs(), LISTENER_AVG_MS_WARN));
            }
            if (s.maxMs() > LISTENER_MAX_MS_WARN) {
                out.accept(String.format(
                        "[耗时] eventType=%s ring=%s order=%d class=%s 单次最大耗时 %.2fms > %.0fms",
                        m.eventTypeName(), m.ringName(), s.order(), s.listenerClassName(), s.maxMs(), LISTENER_MAX_MS_WARN));
            }
        }
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
                log.info("  {}: 活跃线程={}个, 池大小={}个, 已完成任务={}个, 总任务={}个, 队列大小={}, 拒绝={}, 状态={}",
                        id.getConfigKey(),
                        m.activeThreads(),
                        m.poolSize(),
                        m.completedTaskCount(),
                        m.taskCount(),
                        m.queueSize() >= 0 ? m.queueSize() + "个" : "N/A",
                        m.rejectedCount(),
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
            if (QosRetryTimerMetrics.CACHE_NAME.equals(name)) {
                return;
            }
            double hitRate = m.hitRate();
            log.info("  {}: 大小={}条, 命中率={}%, 命中={}次, 未命中={}次, 加载={}次, 淘汰={}次, 加载耗时={}ms",
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
     * 集群拓扑统一随资源监控周期输出，避免 SYN/ACK 高频任务每拍打印节点列表。
     */
    public static void logClusterTopologyMetrics() {
        if (MessageServerContext.serverProperties() == null
                || !MessageServerContext.serverProperties().isClusterEnable()) {
            return;
        }
        Set<String> leaseNodes = new TreeSet<>(NodeLeaseKeeper.snapshot().keySet());
        Set<String> activePools = new TreeSet<>(
                MessageServerContext.clusterActiveServerRegistryTableCache.asMap().keySet());
        Set<String> globalPools = new TreeSet<>(
                MessageServerContext.clusterGlobalServerRegistryTableCache.asMap().keySet());
        log.info("【集群拓扑】租约存活={}个 {}，活动连接池={}个 {}，全局连接池={}个 {}",
                leaseNodes.size(), leaseNodes,
                activePools.size(), activePools,
                globalPools.size(), globalPools);
    }

    /**
     * 输出 QoS 定时重试任务数量及容量占用；接近上限时打 {@link Logger#warn} 预警。
     */
    public static void logQosRetryTimerMetrics() {
        QosRetryTimerMetrics m = getQosRetryTimerMetrics();
        if (m.maxCapacity() <= 0) {
            log.info("【QoS 定时重试】当前活跃任务={} 条（未配置容量上限）", m.activeTasks());
            return;
        }
        log.info(
                "【QoS 定时重试】活跃任务={}/{} 条 (占用 {}%), 剩余容量={} 条, 容量淘汰累计={} 次, 缓存淘汰={} 次, 命中={} 次, 未命中={} 次, 时间轮滞后avg={}ms/max={}ms(样本={}), 执行器拒绝={}",
                m.activeTasks(),
                m.maxCapacity(),
                String.format("%.1f", m.utilization() * 100),
                m.remainingCapacity(),
                m.sizeEvictionCount(),
                m.evictionCount(),
                m.hitCount(),
                m.missCount(),
                String.format("%.1f", m.triggerDelayAvgMs()),
                String.format("%.1f", m.triggerDelayMaxMs()),
                m.triggerDelayCount(),
                m.executorRejectCount()
        );
        collectQosRetryTimerWarnings(m).forEach(msg -> log.warn("[QoS定时重试预警] {}", msg));
    }

    public static void logQosRetryCancelMetrics() {
        QosRetryCancelMetrics.Snapshot s = QosRetryCancelMetrics.snapshot();
        log.info(
                "【QoS 取消】本机取消={} 跨节点命中={} 跨节点任务不存在={} 归属定位失败={} 转发失败={} 转发重试={} 非法控制包={} ACK调度拒绝={}",
                s.localCancel(),
                s.clusterCancelHit(),
                s.clusterCancelMiss(),
                s.originLocateFail(),
                s.forwardFail(),
                s.forwardRetry(),
                s.invalidPacket(),
                s.ackDispatchReject());
    }

    private static List<String> collectQosRetryTimerWarnings(QosRetryTimerMetrics m) {
        List<String> warnings = new ArrayList<>();
        if (m.maxCapacity() <= 0) {
            return warnings;
        }
        if (m.utilization() >= QOS_RETRY_UTIL_CRITICAL) {
            warnings.add(String.format(
                    "[严重] 活跃重试任务 %d/%d (%.1f%%)，接近或达到上限，QoS 推送重试可能被提前终止",
                    m.activeTasks(), m.maxCapacity(), m.utilization() * 100));
        } else if (m.utilization() >= QOS_RETRY_UTIL_WARN) {
            warnings.add(String.format(
                    "[背压] 活跃重试任务 %d/%d (%.1f%%)，超过 %d%% 容量",
                    m.activeTasks(), m.maxCapacity(), m.utilization() * 100,
                    (int) (QOS_RETRY_UTIL_WARN * 100)));
        }
        if (m.sizeEvictionCount() > 0) {
            warnings.add(String.format(
                    "[淘汰] 累计 %d 次因容量满被淘汰（RemovalCause.SIZE），未 ACK 消息可能停止重推",
                    m.sizeEvictionCount()));
        }
        if (m.executorRejectCount() > 0) {
            warnings.add(String.format(
                    "[拒绝] 累计 %d 次向有界执行器提交失败，重试/租约可能延迟",
                    m.executorRejectCount()));
        }
        if (m.triggerDelayMaxMs() >= 1_000) {
            warnings.add(String.format(
                    "[滞后] 时间轮最大触发滞后 %.0fms（avg=%.1fms），Timer-Worker 可能曾被阻塞或积压",
                    m.triggerDelayMaxMs(), m.triggerDelayAvgMs()));
        }
        return warnings;
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
            if (m.rejectedCount() > 0) {
                result.addWarning("线程池 [" + id.getConfigKey() + "] 累计拒绝: " + m.rejectedCount());
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

        collectQosRetryTimerWarnings(getQosRetryTimerMetrics()).forEach(result::addWarning);

        for (DisruptorRingMetrics m : getDisruptorMetrics()) {
            collectDisruptorWarnings(m, result::addWarning);
        }

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
        public static CacheMetrics from(String cacheName, Cache<?, ?> cache) {
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
            Map<String, CacheMetrics> caches,
            List<DisruptorRingMetrics> disruptorRings
    ) {
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("ResourceMetricsSnapshot{\n");
            sb.append("  threadPools=").append(threadPools.size()).append("个\n");
            sb.append("  caches=").append(caches.size()).append("个\n");
            sb.append("  disruptorRings=").append(disruptorRings.size()).append("个\n");
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

