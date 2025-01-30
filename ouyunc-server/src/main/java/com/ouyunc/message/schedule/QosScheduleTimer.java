package com.ouyunc.message.schedule;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;
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

    public static Cache<String, QosTimerTask> qosTimerTaskCaffeine = new CaffeineLocalCache<>("qosTimerTaskCaffeine", Caffeine.newBuilder().build(new CacheLoader<>() {
        @Override
        public @Nullable QosTimerTask load(String taskId) throws Exception {
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
    }, 100, TimeUnit.MILLISECONDS, 1024);


    /**
     *  调度
     */
    public static void schedule(Packet packet, LoginClientInfo loginClientInfo, long delay, TimeUnit timeUnit) {
        qosExecutor.submit(() -> {
            try {
                QosTimerTask qosTimerTask = new QosTimerTask(String.valueOf(packet.getPacketId()), packet, Target.newBuilder().targetIdentity(loginClientInfo.getIdentity()).targetServerAddress(loginClientInfo.getLoginServerAddress()).deviceType(loginClientInfo.getDeviceType()).build(), timer, delay, timeUnit);
                // 存储任务信息
                qosTimerTaskCaffeine.put(qosTimerTask.getTaskId(), qosTimerTask);
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
        QosTimerTask qosTimerTask = qosTimerTaskCaffeine.get(taskId);
        if (qosTimerTask != null) {
            return qosTimerTask.cancel();
        }
        return false;
    }

}
