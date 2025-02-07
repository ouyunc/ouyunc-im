package com.ouyunc.message.schedule;

import com.ouyunc.base.constant.NumberConstant;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * qos timerTask
 */
public class TimerTaskWrapper implements TimerTask{
    private static final Logger log = LoggerFactory.getLogger(TimerTaskWrapper.class);


    /**
     * 线程池
     */
    protected static final ExecutorService qosTaskExecutor = Executors.newVirtualThreadPerTaskExecutor();

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
    protected long delay;

    /**
     * 时间单位
     */
    protected TimeUnit timeUnit;


    /**
     * 超时时间
     */
    protected Timeout scheduledTimeout;

    /**
     *  当前循环次数
     */
    protected final AtomicInteger currentLoopCount;

    /**
     *  最大循环次数， 小于0 表示一直循环
     */
    protected final int maxLoops;



    public TimerTaskWrapper(String taskId, Consumer<TimerTaskWrapper> runnableTask, long delay, TimeUnit timeUnit, int maxLoops) {
        this.taskId = taskId;
        this.runnableTask = runnableTask;
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
            CompletableFuture.runAsync(() -> runnableTask.accept(this), qosTaskExecutor).exceptionally(ex -> {
                log.error("执行定时调度任务异常：{}", ex.getMessage());
                return null;
            });
        }catch (Exception e){
            log.error("执行定时调度任务异常：{}", e.getMessage());
        }finally {
            // 重新调度以实现固定频率
            scheduledTimeout = timeout.timer().newTimeout(this, delay, timeUnit);
        }
    }
}
