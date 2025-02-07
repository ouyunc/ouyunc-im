package com.ouyunc.message.schedule;

import com.ouyunc.base.constant.NumberConstant;
import io.netty.util.HashedWheelTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * 调度器
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
     * 调度定时任务
     */
    public static void schedule(String taskId, Runnable task, long delay, TimeUnit timeUnit, int maxLoops) {
        // 开启第一层定时任务
        timer.newTimeout(new TimerTaskWrapper(taskId, task, delay, timeUnit, maxLoops), NumberConstant.NUMBER_0, timeUnit);
    }

    /**
     * 一直循环调度定时任务
     */
    public static void schedule(String taskId, Runnable task, long delay, TimeUnit timeUnit) {
        schedule(taskId, task, delay, timeUnit, NumberConstant.NUMBER_NEGATIVE_1);
    }

}
