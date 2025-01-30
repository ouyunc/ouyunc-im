package com.ouyunc.message.schedule;

import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * qos timerTask
 */
public class QosTimerTask implements TimerTask{
    private static final Logger log = LoggerFactory.getLogger(QosTimerTask.class);

    /**
     * 任务id
     */
    private String taskId;

    /**
     * 任务延迟时间
     */
    private long delay;

    /**
     * 时间单位
     */
    private TimeUnit timeUnit;

    /**
     * 触发器
     */
    private Timer timer;

    /**
     * 超时时间
     */
    private Timeout scheduledTimeout;

    /**
     * 额外参数 t
     */
    private Packet packet;

    /**
     * 目标地址
     */
    private Target target;


    public QosTimerTask(String taskId, Packet packet, Target target,  Timer timer, long delay, TimeUnit timeUnit) {
        this.taskId = taskId;
        this.delay = delay;
        this.timeUnit = timeUnit;
        this.timer = timer;
        this.packet = packet;
        this.target = target;
    }


    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Target getTarget() {
        return target;
    }

    public void setTarget(Target target) {
        this.target = target;
    }

    public long getDelay() {
        return delay;
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    public Timer getTimer() {
        return timer;
    }

    public void setTimer(Timer timer) {
        this.timer = timer;
    }

    public Timeout getScheduledTimeout() {
        return scheduledTimeout;
    }

    public void setScheduledTimeout(Timeout scheduledTimeout) {
        this.scheduledTimeout = scheduledTimeout;
    }

    public Packet getPacket() {
        return packet;
    }

    public void setPacket(Packet packet) {
        this.packet = packet;
    }

    public boolean cancel() {
        // 停止单个任务
        if (scheduledTimeout != null && !scheduledTimeout.isExpired() && !scheduledTimeout.isCancelled()) {
            return scheduledTimeout.cancel();
        }
        return false;
    }

    @Override
    public void run(Timeout timeout) throws Exception {
        try {
//            MessageHelper.asyncSendMessage(packet, target, (sendResult)->{
//                if (SendStatusEnum.SEND_FAIL.equals(sendResult.getSendStatus())) {
//                    log.error("qos schedule timer 发送消息失败！");
//                    // todo 记录到失败表中/离线消息表
//                }
//            });
            System.out.println(1111);
        } finally {
            // 重新调度以实现固定频率
            scheduledTimeout = timer.newTimeout(this, delay, timeUnit);
        }
    }
}
