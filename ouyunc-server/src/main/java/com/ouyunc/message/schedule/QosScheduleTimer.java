package com.ouyunc.message.schedule;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.cache.Cache;
import com.ouyunc.cache.local.caffeine.CaffeineLocalCache;
import io.netty.util.HashedWheelTimer;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * qos 调度器
 */
public class QosScheduleTimer {
    private static final Logger log = LoggerFactory.getLogger(QosScheduleTimer.class);

    private static final Cache<String, TimerTaskWrapper> qosTimerTaskCaffeine = new CaffeineLocalCache<>("qosTimerTaskCaffeine", Caffeine.newBuilder().build(new CacheLoader<>() {
        @Override
        public @Nullable TimerTaskWrapper load(String taskId) throws Exception {
            return null;
        }
    }));


    /**
     * 虚拟线程
     */
    private static final ExecutorService qosExecutor = Executors.newVirtualThreadPerTaskExecutor();

    // 时间轮触发器
    private static final HashedWheelTimer timer = new HashedWheelTimer( r -> {
        Thread thread = new Thread(r, "Qos-Timer-Worker");
        thread.setDaemon(true);
        return thread;
    }, NumberConstant.NUMBER_100, TimeUnit.MILLISECONDS, 1024);


    /**
     *  调度
     */
    public static void schedule(String taskId, Runnable task, long delay, TimeUnit timeUnit) {
        schedule(taskId, task, delay, timeUnit, NumberConstant.NUMBER_NEGATIVE_1);
    }

    /**
     * 无循环次数限制
     */
    public static void schedule(String taskId, Runnable task, long delay, TimeUnit timeUnit, int maxLoops) {
        qosExecutor.submit(() -> {
            try {
                TimerTaskWrapper qosTimerTask = new TimerTaskWrapper(taskId, task, timer, delay, timeUnit, maxLoops);
                // 存储任务信息
                qosTimerTaskCaffeine.put(taskId, qosTimerTask);
                // 开启第一层定时任务
                timer.newTimeout(qosTimerTask, NumberConstant.NUMBER_0, timeUnit);
            }catch (Exception e) {
                log.error("qos调度异常：{}", e.getMessage());
            }
        });
    }

    /**
     * 取消任务
     */
    public static boolean cancel(String taskId) {
        TimerTaskWrapper qosTimerTask = qosTimerTaskCaffeine.get(taskId);
        if (qosTimerTask != null) {
            return qosTimerTask.cancel();
        }
        return false;
    }

}
