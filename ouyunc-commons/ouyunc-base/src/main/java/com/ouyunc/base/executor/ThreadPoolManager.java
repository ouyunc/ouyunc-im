package com.ouyunc.base.executor;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 消息平台所用所有线程池的集中式管理。
 * 提供以下功能：
 * 基于配置懒加载创建执行器
 * 通过 {@link #initialise (ThreadPoolConfig)} 实现运行时重配置
 * 统一的关闭管理
 */
public final class ThreadPoolManager {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolManager.class);

    private static final AtomicBoolean INITIALISED = new AtomicBoolean(false);
    private static final AtomicBoolean SHUTDOWN_HOOK_REGISTERED = new AtomicBoolean(false);

    private static volatile ThreadPoolConfig currentConfig = ThreadPoolConfig.defaultConfig();

    private static final EnumMap<ThreadPoolId, ManagedExecutor> EXECUTORS = new EnumMap<>(ThreadPoolId.class);

    private ThreadPoolManager() {
        throw new AssertionError("Instantiation not supported");
    }

    /**
     * Initialise thread pool manager with config.
     */
    public static void initialise(ThreadPoolConfig config) {
        if (config == null) {
            config = ThreadPoolConfig.defaultConfig();
        }
        currentConfig = config;
        rebuildExecutors();
        if (SHUTDOWN_HOOK_REGISTERED.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdownAll(true), "thread-pool-manager-shutdown"));
        }
        INITIALISED.set(true);
    }

    private static synchronized void rebuildExecutors() {
        for (Map.Entry<ThreadPoolId, ThreadPoolConfig.PoolConfig> entry : currentConfig.getAll().entrySet()) {
            ThreadPoolId id = entry.getKey();
            ThreadPoolConfig.PoolConfig desiredConfig = entry.getValue();
            ManagedExecutor managed = EXECUTORS.get(id);
            if (managed == null || !Objects.equals(managed.config(), desiredConfig) || managed.executor().isShutdown()) {
                if (managed != null) {
                    shutdownExecutor(id, managed.executor(), false);
                }
                ExecutorService executor = createExecutor(id, desiredConfig);
                EXECUTORS.put(id, new ManagedExecutor(executor, desiredConfig));
                log.debug("线程池 [{}] 已经初始化, 配置: {}", id, desiredConfig);
            }
        }
    }

    private static ExecutorService createExecutor(ThreadPoolId id, ThreadPoolConfig.PoolConfig config) {
        ThreadFactory factory = new BasicThreadFactory.Builder()
                .namingPattern(config.threadNamePrefix() + "-%d")
                .daemon(config.daemon())
                .build();
        return switch (config.type()) {
            case VIRTUAL -> Executors.newVirtualThreadPerTaskExecutor();
            case FIXED -> createFixed(config, factory);
            case CACHED -> new ThreadPoolExecutor(
                    0,
                    config.maxSize() > 0 ? config.maxSize() : Integer.MAX_VALUE,
                    config.keepAliveSeconds(),
                    TimeUnit.SECONDS,
                    new SynchronousQueue<>(),
                    factory,
                    new ThreadPoolExecutor.CallerRunsPolicy());
            case SCHEDULED -> createScheduled(config, factory);
            case SINGLE -> Executors.newSingleThreadExecutor(factory);
        };
    }

    private static ExecutorService createFixed(ThreadPoolConfig.PoolConfig config, ThreadFactory factory) {
        int coreSize = Math.max(1, config.coreSize());
        int maxSize = Math.max(coreSize, config.maxSize());
        BlockingQueue<Runnable> queue;
        if (config.queueCapacity() <= 0) {
            queue = new LinkedBlockingQueue<>();
        } else {
            queue = new LinkedBlockingQueue<>(config.queueCapacity());
        }
        ThreadPoolExecutor executor = new ThreadPoolExecutor(coreSize, maxSize,
                config.keepAliveSeconds(), TimeUnit.SECONDS,
                queue, factory, new ThreadPoolExecutor.CallerRunsPolicy());
        executor.allowCoreThreadTimeOut(config.allowCoreThreadTimeout());
        return executor;
    }

    private static ScheduledExecutorService createScheduled(ThreadPoolConfig.PoolConfig config, ThreadFactory factory) {
        int size = Math.max(1, config.coreSize());
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(size, factory);
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        // SCHEDULED 类型通常不需要核心线程超时，保持线程常驻以提高性能
        return executor;
    }

    // ===== accessors =====

    public static ExecutorService messageSendExecutor() {
        return getExecutor(ThreadPoolId.MESSAGE_SEND);
    }

    public static ExecutorService messageProcessorExecutor() {
        return getExecutor(ThreadPoolId.MESSAGE_PROCESSOR);
    }

    public static ExecutorService qosTaskExecutor() {
        return getExecutor(ThreadPoolId.QOS_TASK);
    }

    public static ExecutorService routerExecutor() {
        return getExecutor(ThreadPoolId.ROUTER);
    }

    public static ExecutorService repositoryExecutor() {
        return getExecutor(ThreadPoolId.REPOSITORY);
    }

    public static ExecutorService eventListenerExecutor() {
        return getExecutor(ThreadPoolId.EVENT_LISTENER);
    }

    public static ScheduledExecutorService clusterClientHeartbeatScheduler() {
        return (ScheduledExecutorService) getExecutor(ThreadPoolId.CLUSTER_CLIENT_HEARTBEAT);
    }

    public static ScheduledExecutorService systemClockScheduler() {
        return (ScheduledExecutorService) getExecutor(ThreadPoolId.SYSTEM_CLOCK);
    }

    public static ExecutorService redisPubSubExecutor() {
        return getExecutor(ThreadPoolId.REDIS_PUBSUB);
    }

    public static ExecutorService httpPushVerifyExecutor() {
        return getExecutor(ThreadPoolId.HTTP_PUSH_VERIFY);
    }

    private static ExecutorService getExecutor(ThreadPoolId id) {
        ManagedExecutor managed = EXECUTORS.get(id);
        if (managed == null) {
            synchronized (ThreadPoolManager.class) {
                managed = EXECUTORS.get(id);
                if (managed == null) {
                    ThreadPoolConfig.PoolConfig config = currentConfig.get(id);
                    ExecutorService executor = createExecutor(id, config);
                    managed = new ManagedExecutor(executor, config);
                    EXECUTORS.put(id, managed);
                }
            }
        }
        return managed.executor();
    }

    // ===== metrics =====

    public static EnumMap<ThreadPoolId, ThreadPoolMetrics> metrics() {
        EnumMap<ThreadPoolId, ThreadPoolMetrics> snapshot = new EnumMap<>(ThreadPoolId.class);
        EXECUTORS.forEach((id, managed) -> snapshot.put(id, ThreadPoolMetrics.from(id, managed.executor())));
        return snapshot;
    }

    public static void logMetrics() {
        metrics().forEach((id, metrics) -> log.info("ThreadPool[{}] metrics: {}", id, metrics));
    }

    // ===== shutdown =====

    public static void shutdownAll() {
        shutdownAll(false);
    }

    private static void shutdownAll(boolean fromHook) {
        EXECUTORS.forEach((id, managed) -> shutdownExecutor(id, managed.executor(), !fromHook));
        EXECUTORS.clear();
        if (!fromHook) {
            INITIALISED.set(false);
        }
    }

    private static void shutdownExecutor(ThreadPoolId id, ExecutorService executor, boolean wait) {
        if (executor == null || executor.isShutdown()) {
            return;
        }
        executor.shutdown();
        if (wait) {
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn("Thread pool [{}] did not terminate within timeout. Forcing shutdown.", id);
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        }
    }

    // ===== helper classes =====

    private record ManagedExecutor(ExecutorService executor, ThreadPoolConfig.PoolConfig config) {
    }

    public record ThreadPoolMetrics(
            ThreadPoolId id,
            boolean shutdown,
            boolean terminated,
            int activeThreads,
            int poolSize,
            long completedTaskCount,
            long taskCount,
            int queueSize
    ) {
        private static ThreadPoolMetrics from(ThreadPoolId id, ExecutorService executor) {
            if (executor instanceof ThreadPoolExecutor tpe) {
                BlockingQueue<Runnable> queue = tpe.getQueue();
                return new ThreadPoolMetrics(
                        id,
                        tpe.isShutdown(),
                        tpe.isTerminated(),
                        tpe.getActiveCount(),
                        tpe.getPoolSize(),
                        tpe.getCompletedTaskCount(),
                        tpe.getTaskCount(),
                        Optional.ofNullable(queue).map(BlockingQueue::size).orElse(-1)
                );
            }
            if (executor instanceof ScheduledThreadPoolExecutor stpe) {
                BlockingQueue<Runnable> queue = stpe.getQueue();
                return new ThreadPoolMetrics(
                        id,
                        stpe.isShutdown(),
                        stpe.isTerminated(),
                        stpe.getActiveCount(),
                        stpe.getPoolSize(),
                        stpe.getCompletedTaskCount(),
                        stpe.getTaskCount(),
                        Optional.ofNullable(queue).map(BlockingQueue::size).orElse(-1)
                );
            }
            return new ThreadPoolMetrics(
                    id,
                    executor.isShutdown(),
                    executor.isTerminated(),
                    -1,
                    -1,
                    -1,
                    -1,
                    -1
            );
        }
    }

    // ===== initialisation helpers =====
    public static boolean isInitialised() {
        return INITIALISED.get();
    }
}

