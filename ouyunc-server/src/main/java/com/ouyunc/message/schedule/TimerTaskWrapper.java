package com.ouyunc.message.schedule;

import com.ouyunc.base.constant.NumberConstant;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * qos timerTask
 */
public class TimerTaskWrapper implements TimerTask{
    private static final Logger log = LoggerFactory.getLogger(TimerTaskWrapper.class);
    private static final ExecutorService qosTaskExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * 任务id
     */
    private String taskId;


    /**
     * 运行任务
     */
    private Runnable runnableTask;

    /**
     * 触发器
     */
    private Timer timer;

    /**
     * 任务延迟时间
     */
    private long delay;

    /**
     * 时间单位
     */
    private TimeUnit timeUnit;


    /**
     * 超时时间
     */
    private Timeout scheduledTimeout;

    /**
     *  当前循环次数
     */
    private final AtomicInteger currentLoopCount;

    /**
     *  最大循环次数， 小于0 表示一直循环
     */
    private final int maxLoops;



    public TimerTaskWrapper(String taskId, Runnable runnableTask, Timer timer, long delay, TimeUnit timeUnit, int maxLoops) {
        this.taskId = taskId;
        this.runnableTask = runnableTask;
        this.timer = timer;
        this.delay = delay;
        this.timeUnit = timeUnit;
        this.maxLoops = maxLoops;
        this.currentLoopCount = new AtomicInteger(0);
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
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

    public Runnable getRunnableTask() {
        return runnableTask;
    }

    public void setRunnableTask(Runnable runnableTask) {
        this.runnableTask = runnableTask;
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
            // 当前循环计数
            if (maxLoops >= NumberConstant.NUMBER_0 && currentLoopCount.incrementAndGet() > maxLoops) {
                cancel();
                return;
            }
            CompletableFuture.runAsync(runnableTask, qosTaskExecutor);
        }catch (Exception e){
            log.error("qos 执行定时调度任务异常：{}", e.getMessage());
            // todo 记录到错误日志或业务错误表中
        }finally {
            // 重新调度以实现固定频率
            scheduledTimeout = timer.newTimeout(this, delay, timeUnit);
        }
    }
}
