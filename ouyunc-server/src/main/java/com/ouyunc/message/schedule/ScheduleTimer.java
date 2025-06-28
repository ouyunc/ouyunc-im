package com.ouyunc.message.schedule;

import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.core.listener.event.ExceptionEvent;
import com.ouyunc.message.context.MessageServerContext;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 调度器， 如果是在ctx 中优先考虑 ctx.executor().schedule 的定时器
 */
public class ScheduleTimer {

    private static final Logger log = LoggerFactory.getLogger(ScheduleTimer.class);

    // 时间轮触发器
    protected static final HashedWheelTimer timer = new HashedWheelTimer(r -> {
        Thread thread = new Thread(r, "Timer-Worker");
        thread.setDaemon(true);
        return thread;
    }, NumberConstant.NUMBER_100, TimeUnit.MILLISECONDS, 1024);


    /**
     * 调度定时任务,固定频率
     */
    public static void scheduleAtFixedRate(String taskId, Consumer<TimerTaskWrapper> task, long initialDelay, long period, TimeUnit timeUnit, int maxLoops) {
        schedule(taskId, task, initialDelay, period, timeUnit, false, maxLoops);
    }

    /**
     * 一直循环调度定时任务,固定频率
     */
    public static void scheduleAtFixedRate(String taskId, Consumer<TimerTaskWrapper> task, long initialDelay, long period, TimeUnit timeUnit) {
        schedule(taskId, task, initialDelay, period, timeUnit, false, NumberConstant.NUMBER_NEGATIVE_1);
    }

    /**
     * 一直循环调度定时任务，固定间隔时间
     */
    public static void scheduleWithFixedDelay(String taskId, Consumer<TimerTaskWrapper> task, long initialDelay, long period, TimeUnit timeUnit) {
        schedule(taskId, task, initialDelay, period, timeUnit, true, NumberConstant.NUMBER_NEGATIVE_1);
    }

    /**
     * 一直循环调度定时任务，固定间隔时间
     */
    public static void scheduleWithFixedDelay(String taskId, Consumer<TimerTaskWrapper> task, long initialDelay, long period, TimeUnit timeUnit, int maxLoops) {
        schedule(taskId, task, initialDelay, period, timeUnit, true, maxLoops);
    }

    /**
     * 一直循环调度定时任务
     */
    public static void schedule(String taskId, Consumer<TimerTaskWrapper> task, long initialDelay, long period, TimeUnit timeUnit, boolean sync, int maxLoops) {
        try {
            // 开启第一层定时任务
            timer.newTimeout(new TimerTaskWrapper(taskId, task, period, timeUnit, sync, maxLoops), initialDelay, timeUnit);
        }catch (Exception e) {
            log.error("task 调度异常：{}", e.getMessage());
            // 这里可以做错误日志记录
            MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.SCHEDULE_TASK_ERROR, "task 调度异常：" + e.getMessage(), null));
        }
    }

    /**
     * 调度一次性定时任务，只执行一次
     * @return Timeout 可以用于取消任务
     */
    public static Timeout scheduleOnce(Runnable task, long delay, TimeUnit timeUnit) {
        try {
            // 直接使用timer创建一次性定时任务
            return timer.newTimeout(timeout -> {
                try {
                    task.run();
                } catch (Exception e) {
                    log.error("一次性任务执行异常：{}", e.getMessage());
                    MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.SCHEDULE_TASK_ERROR, "一次性任务执行异常：" + e.getMessage(), null));
                }
            }, delay, timeUnit);
        }catch (Exception e) {
            log.error("一次性任务调度异常：{}", e.getMessage());
            // 这里可以做错误日志记录
            MessageServerContext.publishEvent(new ExceptionEvent(ExceptionCodeEnum.SCHEDULE_TASK_ERROR, "一次性任务调度异常：" + e.getMessage(), null));
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
        TimerTaskWrapper qosTimerTask = TimerTaskWrapper.timerTaskCaffeine.get(taskId);
        if (qosTimerTask != null) {
            return qosTimerTask.cancel();
        }else {
            log.error("qos取消任务失败，任务不存在,id：{}", taskId);
        }
        return false;
    }
}
