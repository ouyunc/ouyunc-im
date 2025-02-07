package com.ouyunc.message.schedule;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.SendStatusEnum;
import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.cache.Cache;
import com.ouyunc.cache.local.caffeine.CaffeineLocalCache;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.event.RemoveOfflineEvent;
import com.ouyunc.message.helper.MessageHelper;
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



    public static void schedule(Packet packet, Target target, long delay, TimeUnit timeUnit, int maxLoops) {
        qosExecutor.submit(() -> {
            try {
                // 构建任务信息
                String taskId = String.valueOf(packet.getPacketId());
                TimerTaskWrapper qosTimerTask = new TimerTaskWrapper(taskId, (timeTask) -> {
                    // 这里面抛异常会被task run 方法捕获，这里面抛异常不会影响其他任务
                    MessageHelper.asyncSendMessage(packet, target, (sendResult)->{
                        if (SendStatusEnum.SEND_OK.equals(sendResult.getSendStatus())) {
                            // 如果在给定的次数内发送成功，则取消发送
                            timeTask.cancel();
                            // 发布清除离线消息的事件,后续可以有mq 来处理了
                            MessageContext.publishEvent(new RemoveOfflineEvent(packet), true);
                        }
                    });
                }, delay, timeUnit, maxLoops);
                // 存储任务信息
                qosTimerTaskCaffeine.put(String.valueOf(packet.getPacketId()), qosTimerTask);
                // 开启第一层定时任务
                timer.newTimeout(qosTimerTask, NumberConstant.NUMBER_0, timeUnit);
            }catch (Exception e) {
                log.error("qos调度异常：{}", e.getMessage());
                // 这里可以做错误日志记录
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
        }else {
            log.error("qos取消任务失败，任务不存在,id：{}", taskId);
        }
        return false;
    }

}
