package com.ouyunc.message.schedule;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.cache.Cache;
import com.ouyunc.cache.local.caffeine.CaffeineLocalCache;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.message.context.MessageServerContext;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 时间轮任务包装：时间轮线程只负责触发索引，业务/租约 I/O 一律下沉有界执行器。
 * <p>{@code waitForCompletion=true}（fixed-delay）：任务完成后再排下一轮，避免重叠。
 * {@code false}（fixed-rate）：提交后立即排下一轮。</p>
 */
public class TimerTaskWrapper implements TimerTask {
    private static final Logger log = LoggerFactory.getLogger(TimerTaskWrapper.class);

    /** 因缓存容量满被淘汰的业务重试任务累计次数（供监控） */
    private static final AtomicLong SIZE_EVICTION_COUNT = new AtomicLong(0);
    /** 时间轮触发相对 deadline 的延迟累计（纳秒） */
    private static final AtomicLong TRIGGER_DELAY_NANOS_SUM = new AtomicLong(0);
    private static final AtomicLong TRIGGER_DELAY_COUNT = new AtomicLong(0);
    private static final AtomicLong TRIGGER_DELAY_NANOS_MAX = new AtomicLong(0);
    /** 向执行器提交被拒绝累计 */
    private static final AtomicLong EXECUTOR_REJECT_COUNT = new AtomicLong(0);

    public static long sizeEvictionCount() {
        return SIZE_EVICTION_COUNT.get();
    }

    public static long executorRejectCount() {
        return EXECUTOR_REJECT_COUNT.get();
    }

    public static long triggerDelayCount() {
        return TRIGGER_DELAY_COUNT.get();
    }

    public static long triggerDelayNanosSum() {
        return TRIGGER_DELAY_NANOS_SUM.get();
    }

    public static long triggerDelayNanosMax() {
        return TRIGGER_DELAY_NANOS_MAX.get();
    }

    /**
     * 业务重试任务缓存（QoS 等）；容量满可淘汰，不得放入租约任务。
     */
    public static final Cache<String, TimerTaskWrapper> timerTaskCaffeine = new CaffeineLocalCache<>("timerTaskCaffeine", Caffeine.newBuilder()
            .maximumSize(MessageConstant.TIMER_TASK_CACHE_MAX_SIZE)
            .evictionListener((String taskId, TimerTaskWrapper task, RemovalCause cause) -> {
                if (cause == RemovalCause.SIZE) {
                    SIZE_EVICTION_COUNT.incrementAndGet();
                    log.warn("定时任务缓存因容量满被淘汰，taskId={}, cause={}", taskId, cause);
                    if (task != null) {
                        task.cancelScheduledTimeout();
                    }
                } else if (cause == RemovalCause.EXPIRED) {
                    log.debug("定时任务缓存过期淘汰，taskId={}", taskId);
                }
            })
            .recordStats()
            .build(new CacheLoader<>() {
                @Override
                public @Nullable TimerTaskWrapper load(String taskId) throws Exception {
                    return null;
                }
            }));

    /**
     * 系统任务（节点租约等）：独立表，不受业务 Caffeine SIZE 淘汰影响。
     */
    public static final ConcurrentHashMap<String, TimerTaskWrapper> systemTimerTasks = new ConcurrentHashMap<>();

    protected String taskId;
    protected Consumer<TimerTaskWrapper> runnableTask;
    protected long period;
    protected TimeUnit timeUnit;
    protected volatile Timeout scheduledTimeout;
    /**
     * true=fixed-delay：等任务（含异步）完成后再调度下一轮；false=fixed-rate：触发后立刻排下一轮。
     * 历史字段名 sync：曾表示在时间轮线程同步跑业务，已废弃该语义。
     */
    protected boolean waitForCompletion;
    protected final AtomicInteger currentLoopCount;
    protected final int maxLoops;
    protected final TimerTaskKind kind;
    /** 本次 Timeout 预期触发时刻（nanoTime），用于计算时间轮滞后 */
    private volatile long expectedFireNanos;

    public TimerTaskWrapper(String taskId, Consumer<TimerTaskWrapper> runnableTask, long period, TimeUnit timeUnit,
                            boolean waitForCompletion, int maxLoops) {
        this(taskId, runnableTask, period, timeUnit, waitForCompletion, maxLoops, TimerTaskKind.BUSINESS);
    }

    public TimerTaskWrapper(String taskId, Consumer<TimerTaskWrapper> runnableTask, long period, TimeUnit timeUnit,
                            boolean waitForCompletion, int maxLoops, TimerTaskKind kind) {
        this.taskId = taskId;
        this.runnableTask = runnableTask;
        this.period = period;
        this.timeUnit = timeUnit;
        this.maxLoops = maxLoops;
        this.currentLoopCount = new AtomicInteger(0);
        this.waitForCompletion = waitForCompletion;
        this.kind = kind == null ? TimerTaskKind.BUSINESS : kind;
        putSelf();
    }

    private void putSelf() {
        if (kind == TimerTaskKind.SYSTEM) {
            systemTimerTasks.put(taskId, this);
        } else {
            timerTaskCaffeine.put(taskId, this);
        }
    }

    static TimerTaskWrapper lookup(String taskId) {
        TimerTaskWrapper system = systemTimerTasks.get(taskId);
        if (system != null) {
            return system;
        }
        return timerTaskCaffeine.get(taskId);
    }

    private boolean stillRegistered() {
        if (kind == TimerTaskKind.SYSTEM) {
            return systemTimerTasks.get(taskId) == this;
        }
        return timerTaskCaffeine.get(taskId) == this;
    }

    private ExecutorService workExecutor() {
        return kind == TimerTaskKind.SYSTEM
                ? ThreadPoolManager.nodeLeaseExecutor()
                : ThreadPoolManager.qosTaskExecutor();
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public long getDelay() {
        return period;
    }

    public void setDelay(long delay) {
        this.period = delay;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    public Timeout getScheduledTimeout() {
        return scheduledTimeout;
    }

    public void setScheduledTimeout(Timeout scheduledTimeout) {
        this.scheduledTimeout = scheduledTimeout;
    }

    /** 首次/再次调度时由 ScheduleTimer 写入预期触发时刻 */
    void markExpectedFire(long delay, TimeUnit unit) {
        this.expectedFireNanos = System.nanoTime() + unit.toNanos(delay);
    }

    public Consumer<TimerTaskWrapper> getRunnableTask() {
        return runnableTask;
    }

    public void setRunnableTask(Consumer<TimerTaskWrapper> runnableTask) {
        this.runnableTask = runnableTask;
    }

    public AtomicInteger getCurrentLoopCount() {
        return currentLoopCount;
    }

    public int getMaxLoops() {
        return maxLoops;
    }

    public TimerTaskKind getKind() {
        return kind;
    }

    public boolean cancel() {
        boolean removed;
        if (kind == TimerTaskKind.SYSTEM) {
            removed = systemTimerTasks.remove(taskId, this);
        } else {
            // 旧任务不得删除同 key 下已经替换的新实例。
            removed = timerTaskCaffeine.asMap().remove(taskId, this);
        }
        boolean timeoutCancelled = cancelScheduledTimeout();
        // 已触发的 Timeout 不能再 cancel，但移除索引已经完成逻辑取消。
        return removed || timeoutCancelled;
    }

    /**
     * 仅取消时间轮上的调度，不操作缓存（供容量淘汰等场景使用，避免在 eviction 回调中重入缓存）
     */
    boolean cancelScheduledTimeout() {
        if (scheduledTimeout != null && !scheduledTimeout.isExpired() && !scheduledTimeout.isCancelled()) {
            return scheduledTimeout.cancel();
        }
        return false;
    }

    @Override
    public void run(Timeout timeout) throws Exception {
        recordTriggerDelay();
        if (!stillRegistered()) {
            timeout.cancel();
            return;
        }
        if (maxLoops >= NumberConstant.NUMBER_0 && currentLoopCount.incrementAndGet() > maxLoops) {
            cancel();
            return;
        }

        Timer timer = timeout.timer();
        if (waitForCompletion) {
            // fixed-delay：完成后再排下一轮，保证不重叠
            submitWork(() -> {
                try {
                    runnableTask.accept(this);
                } finally {
                    rescheduleIfAlive(timer);
                }
            }, timer);
        } else {
            // fixed-rate：提交后立刻排下一轮；实际 I/O 在执行器上
            try {
                submitWork(() -> runnableTask.accept(this), timer);
            } finally {
                rescheduleIfAlive(timer);
            }
        }
    }

    private void submitWork(Runnable work, Timer timer) {
        try {
            workExecutor().execute(() -> {
                try {
                    work.run();
                } catch (Exception ex) {
                    log.error("执行定时调度任务异常 taskId={}: {}", taskId, ex.getMessage());
                    MessageServerContext.publishEvent(new MessageEvent(
                            ExceptionEventPayload.of(ExceptionCodeEnum.SCHEDULE_TASK_ERROR,
                                    "业务 task 调度异常：" + ex.getMessage(), null),
                            MessageEventTypeEnum.EXCEPTION));
                }
            });
        } catch (RejectedExecutionException ex) {
            EXECUTOR_REJECT_COUNT.incrementAndGet();
            log.error("定时任务提交执行器被拒绝 taskId={} kind={}", taskId, kind, ex);
            MessageServerContext.publishEvent(new MessageEvent(
                    ExceptionEventPayload.of(ExceptionCodeEnum.SCHEDULE_TASK_ERROR,
                            "task 执行器拒绝：" + ex.getMessage(), null),
                    MessageEventTypeEnum.EXCEPTION));
            // fixed-delay 被拒时 work 的 finally 不会跑，需自行排下一轮以免永久停摆
            if (waitForCompletion) {
                rescheduleIfAlive(timer);
            }
        }
    }

    private void rescheduleIfAlive(Timer timer) {
        if (!stillRegistered()) {
            return;
        }
        try {
            markExpectedFire(period, timeUnit);
            scheduledTimeout = timer.newTimeout(this, period, timeUnit);
        } catch (Exception e) {
            log.error("重调度失败 taskId={}: {}", taskId, e.getMessage());
        }
    }

    private void recordTriggerDelay() {
        long expected = expectedFireNanos;
        if (expected <= 0L) {
            return;
        }
        long delay = System.nanoTime() - expected;
        if (delay < 0L) {
            delay = 0L;
        }
        TRIGGER_DELAY_NANOS_SUM.addAndGet(delay);
        TRIGGER_DELAY_COUNT.incrementAndGet();
        long prev;
        do {
            prev = TRIGGER_DELAY_NANOS_MAX.get();
            if (delay <= prev) {
                break;
            }
        } while (!TRIGGER_DELAY_NANOS_MAX.compareAndSet(prev, delay));
    }
}
