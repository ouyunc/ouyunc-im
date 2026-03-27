package com.ouyunc.message.schedule;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
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
import java.util.function.Consumer;

/**
 * qos timerTask
 */
public class TimerTaskWrapper implements TimerTask{
    private static final Logger log = LoggerFactory.getLogger(TimerTaskWrapper.class);


    /***
     * 任务缓存
     */
    public static final Cache<String, TimerTaskWrapper> timerTaskCaffeine = new CaffeineLocalCache<>("timerTaskCaffeine", Caffeine.newBuilder().build(new CacheLoader<>() {
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
        // 停止单个任务
        if (scheduledTimeout != null && !scheduledTimeout.isExpired() && !scheduledTimeout.isCancelled()) {
            // 真正停止
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
