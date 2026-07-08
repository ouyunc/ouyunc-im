package com.ouyunc.message.schedule;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.cache.Cache;
import com.ouyunc.cache.local.caffeine.CaffeineLocalCache;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.message.context.MessageServerContext;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * qos timerTask
 */
public class TimerTaskWrapper implements TimerTask{
    private static final Logger log = LoggerFactory.getLogger(TimerTaskWrapper.class);

    /** 因缓存容量满被淘汰的重试任务累计次数（供监控） */
    private static final AtomicLong SIZE_EVICTION_COUNT = new AtomicLong(0);

    public static long sizeEvictionCount() {
        return SIZE_EVICTION_COUNT.get();
    }

    /***
     * 任务缓存
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
     * 任务id
     */
    protected String taskId;


    /**
     * 运行任务
     */
    protected Consumer<TimerTaskWrapper> runnableTask;


    /**
     * 任务延迟时间
     */
    protected long period;

    /**
     * 时间单位
     */
    protected TimeUnit timeUnit;

    /**
     * 超时时间
     */
    protected Timeout scheduledTimeout;


    /**
     * 是否同步执行提交的任务
     */
    protected boolean sync;

    /**
     *  当前循环次数
     */
    protected final AtomicInteger currentLoopCount;

    /**
     *  最大循环次数， 小于0 表示一直循环
     */
    protected final int maxLoops;



    public TimerTaskWrapper(String taskId, Consumer<TimerTaskWrapper> runnableTask, long period, TimeUnit timeUnit, boolean sync, int maxLoops) {
        this.taskId = taskId;
        this.runnableTask = runnableTask;
        this.period = period;
        this.timeUnit = timeUnit;
        this.maxLoops = maxLoops;
        this.currentLoopCount = new AtomicInteger(0);
        this.sync = sync;
        timerTaskCaffeine.put(taskId, this);
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

    public boolean cancel() {
        // 删除任务
        timerTaskCaffeine.delete(taskId);
        return cancelScheduledTimeout();
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
        TimerTaskWrapper timerTaskWrapper = timerTaskCaffeine.get(taskId);
        try {
            // 如果已经取消则取消当前
            if (timerTaskWrapper == null) {
                timeout.cancel();
                return;
            }
            // 当前循环计数
            if (maxLoops >= NumberConstant.NUMBER_0 && currentLoopCount.incrementAndGet() > maxLoops) {
                cancel();
                return;
            }
            // 同步执行任务还是异步执行任务
            if (sync) {
                runnableTask.accept(this);
            }else {
                CompletableFuture.runAsync(() -> runnableTask.accept(this), ThreadPoolManager.qosTaskExecutor()).exceptionally(ex -> {
                    log.error("执行定时调度任务异常：{}", ex.getMessage());
                    MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.SCHEDULE_TASK_ERROR, "业务 task 调度异常：" + ex.getMessage(), null), MessageEventTypeEnum.EXCEPTION));
                    return null;
                });
            }
        }finally {
            // 重新调度以实现固定频率
            if (timerTaskWrapper != null) {
                scheduledTimeout = timeout.timer().newTimeout(this, period, timeUnit);
            }
        }
    }
}
