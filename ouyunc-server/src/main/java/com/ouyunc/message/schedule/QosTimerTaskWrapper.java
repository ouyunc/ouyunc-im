package com.ouyunc.message.schedule;

import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.listener.event.SendOfflineEvent;
import com.ouyunc.message.context.MessageServerContext;
import io.netty.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


/**
 * qos 定时任务包装类
 */
public class QosTimerTaskWrapper extends TimerTaskWrapper{

    private static final Logger log = LoggerFactory.getLogger(QosTimerTaskWrapper.class);


    /**
     * 消息packet
     */
    private Packet packet;

    public QosTimerTaskWrapper(Packet packet, Runnable runnableTask, long delay, TimeUnit timeUnit, int maxLoops) {
        super(String.valueOf(packet.getPacketId()), runnableTask, delay, timeUnit, maxLoops);
        this.packet = packet;
    }

    public Packet getPacket() {
        return packet;
    }

    public void setPacket(Packet packet) {
        this.packet = packet;
    }

    @Override
    public void run(Timeout timeout) throws Exception {
        try {
            // 当前循环计数
            if (maxLoops >= NumberConstant.NUMBER_0 && currentLoopCount.incrementAndGet() > maxLoops) {
                cancel();
                // 将消息发送到离线消息mq
                MessageServerContext.publishEvent(new SendOfflineEvent(packet), true);
                return;
            }
            CompletableFuture.runAsync(runnableTask, qosTaskExecutor).exceptionally(ex -> {
                // 将消息发送到离线消息mq
                MessageServerContext.publishEvent(new SendOfflineEvent(packet), true);
                return null;
            });
        }catch (Exception e){
            log.error("qos 执行定时调度任务异常：{}", e.getMessage());
            // 将消息发送到离线消息mq
            MessageServerContext.publishEvent(new SendOfflineEvent(packet), true);
        }finally {
            // 重新调度以实现固定频率
            scheduledTimeout = timeout.timer().newTimeout(this, delay, timeUnit);
        }
    }
}
