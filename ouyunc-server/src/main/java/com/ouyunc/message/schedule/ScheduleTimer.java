package com.ouyunc.message.schedule;

import com.ouyunc.core.exception.ExceptionReporter;

import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.message.context.MessageServerContext;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 调度器：时间轮只做到期索引；业务/租约工作由 {@link TimerTaskWrapper} 下沉到有界执行器。
 * <p>若在 ctx 中优先考虑 {@code ctx.executor().schedule}。</p>
 */
public class ScheduleTimer {

    private static final Logger log = LoggerFactory.getLogger(ScheduleTimer.class);

    private static final AtomicBoolean STOPPED = new AtomicBoolean(false);

    // 时间轮触发器（单线程）；禁止在此线程同步打 Redis/DB
    protected static final HashedWheelTimer timer = new HashedWheelTimer(r -> {
        Thread thread = new Thread(r, "Timer-Worker");
        thread.setDaemon(true);
        return thread;
    }, NumberConstant.NUMBER_100, TimeUnit.MILLISECONDS, 1024);


    /**
     * 调度定时任务,固定频率（业务域）
     */
    public static void scheduleAtFixedRate(String taskId, Consumer<TimerTaskWrapper> task, long initialDelay, long period, TimeUnit timeUnit, int maxLoops) {
        schedule(taskId, task, initialDelay, period, timeUnit, false, maxLoops, TimerTaskKind.BUSINESS);
    }

    /**
     * 一直循环调度定时任务,固定频率（业务域）
     */
    public static void scheduleAtFixedRate(String taskId, Consumer<TimerTaskWrapper> task, long initialDelay, long period, TimeUnit timeUnit) {
        schedule(taskId, task, initialDelay, period, timeUnit, false, NumberConstant.NUMBER_NEGATIVE_1, TimerTaskKind.BUSINESS);
    }

    /**
     * 系统任务固定频率（节点租约等）：独立缓存 + node-lease 执行器。
     */
    public static void scheduleSystemAtFixedRate(String taskId, Consumer<TimerTaskWrapper> task, long initialDelay, long period, TimeUnit timeUnit) {
        schedule(taskId, task, initialDelay, period, timeUnit, false, NumberConstant.NUMBER_NEGATIVE_1, TimerTaskKind.SYSTEM);
    }

    /**
     * 一直循环调度定时任务，固定间隔时间（完成后 delay，业务域）
     */
    public static void scheduleWithFixedDelay(String taskId, Consumer<TimerTaskWrapper> task, long initialDelay, long period, TimeUnit timeUnit) {
        schedule(taskId, task, initialDelay, period, timeUnit, true, NumberConstant.NUMBER_NEGATIVE_1, TimerTaskKind.BUSINESS);
    }

    /**
     * 一直循环调度定时任务，固定间隔时间（完成后 delay，业务域）
     */
    public static void scheduleWithFixedDelay(String taskId, Consumer<TimerTaskWrapper> task, long initialDelay, long period, TimeUnit timeUnit, int maxLoops) {
        schedule(taskId, task, initialDelay, period, timeUnit, true, maxLoops, TimerTaskKind.BUSINESS);
    }

    /**
     * 一直循环调度定时任务
     *
     * @param waitForCompletion true=fixed-delay（完成后再调度）；false=fixed-rate（触发后立即调度下一轮）
     */
    public static void schedule(String taskId, Consumer<TimerTaskWrapper> task, long initialDelay, long period, TimeUnit timeUnit, boolean waitForCompletion, int maxLoops) {
        schedule(taskId, task, initialDelay, period, timeUnit, waitForCompletion, maxLoops, TimerTaskKind.BUSINESS);
    }

    public static void schedule(String taskId, Consumer<TimerTaskWrapper> task, long initialDelay, long period,
                                TimeUnit timeUnit, boolean waitForCompletion, int maxLoops, TimerTaskKind kind) {
        try {
            // 同 taskId 先取消旧任务，避免时间轮残留与缓存覆盖导致双调度
            cancelQuietly(taskId);
            TimerTaskWrapper wrapper = new TimerTaskWrapper(taskId, task, period, timeUnit, waitForCompletion, maxLoops, kind);
            wrapper.markExpectedFire(initialDelay, timeUnit);
            Timeout timeout = timer.newTimeout(wrapper, initialDelay, timeUnit);
            wrapper.setScheduledTimeout(timeout);
        } catch (Exception e) {
            log.error("task 调度异常：{}", e.getMessage());
            ExceptionReporter.reportSystem(ExceptionCodeEnum.SCHEDULE_TASK_ERROR, "task 调度异常：" + e.getMessage(), "ScheduleTimer.schedule", null, e);
        }
    }

    /**
     * 调度一次性定时任务，只执行一次
     * @return Timeout 可以用于取消任务
     */
    public static Timeout scheduleOnce(Runnable task, long delay, TimeUnit timeUnit) {
        try {
            // 时间轮回调只跑传入 Runnable；重活请由调用方自行提交执行器
            return timer.newTimeout(timeout -> {
                try {
                    task.run();
                } catch (Exception e) {
                    log.error("一次性任务执行异常：{}", e.getMessage());
                    ExceptionReporter.reportSystem(ExceptionCodeEnum.SCHEDULE_TASK_ERROR, "一次性任务执行异常：" + e.getMessage(), "ScheduleTimer.schedule", null, e);
                }
            }, delay, timeUnit);
        } catch (Exception e) {
            log.error("一次性任务调度异常：{}", e.getMessage());
            ExceptionReporter.reportSystem(ExceptionCodeEnum.SCHEDULE_TASK_ERROR, "一次性任务调度异常：" + e.getMessage(), "ScheduleTimer.schedule", null, e);
            return null;
        }
    }

    /**
     * 取消一次性定时任务
     */
    public static boolean cancelOnce(Timeout timeout) {
        if (timeout != null && !timeout.isExpired()) {
            return timeout.cancel();
        }
        return false;
    }

    /**
     * 取消任务
     */
    public static boolean cancel(String taskId) {
        TimerTaskWrapper qosTimerTask = TimerTaskWrapper.lookup(taskId);
        if (qosTimerTask != null) {
            return qosTimerTask.cancel();
        } else {
            log.warn("qos取消任务失败，任务不存在,id：{}", taskId);
        }
        return false;
    }

    /**
     * 本机有任务才取消；任务不在本 JVM（落地节点收到 C2S ACK）时不打 warn。
     */
    public static boolean cancelIfPresent(String taskId) {
        TimerTaskWrapper existing = TimerTaskWrapper.lookup(taskId);
        if (existing == null) {
            return false;
        }
        return existing.cancel();
    }

    /** 调度替换场景：任务不存在不打 warn。 */
    private static void cancelQuietly(String taskId) {
        TimerTaskWrapper existing = TimerTaskWrapper.lookup(taskId);
        if (existing != null) {
            existing.cancel();
        }
    }

    /**
     * 停止时间轮并取消所有未完成超时任务（优雅关闭时调用，幂等）。
     */
    public static void stop() {
        if (!STOPPED.compareAndSet(false, true)) {
            return;
        }
        try {
            Set<Timeout> unfinished = timer.stop();
            int size = unfinished == null ? 0 : unfinished.size();
            log.warn("ScheduleTimer 已停止, unfinishedTimeouts={}", size);
            TimerTaskWrapper.systemTimerTasks.clear();
        } catch (Exception e) {
            log.warn("ScheduleTimer 停止异常: {}", e.getMessage());
        }
    }
}
