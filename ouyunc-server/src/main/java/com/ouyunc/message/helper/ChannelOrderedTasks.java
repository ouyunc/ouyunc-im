package com.ouyunc.message.helper;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.message.schedule.ScheduleTimer;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import io.netty.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * 单连接有界串行队列。每条任务独立计时，完成后才启动下一条。
 * 队列锁只保护状态，不在锁内执行用户代码、取消回调或存储操作。
 */
public final class ChannelOrderedTasks {
    private static final Logger log = LoggerFactory.getLogger(ChannelOrderedTasks.class);
    private static final AttributeKey<SerialQueue> QUEUE_KEY = AttributeKey.valueOf("CHANNEL_ORDERED_TASK_QUEUE");

    private ChannelOrderedTasks() { }

    public static void execute(Channel channel, Runnable task) {
        executeAsync(channel, () -> {
            task.run();
            return CompletableFuture.completedFuture(null);
        });
    }

    public static void executeAsync(Channel channel, Supplier<? extends CompletionStage<?>> task) {
        executeAsync(channel, task, MessageConstant.CHANNEL_ORDERED_TASK_DEADLINE_MS);
    }

    /** deadline 为本条任务进入执行阶段后的上限，包含同步 supplier；不改变其它任务的期限。 */
    public static void executeAsync(Channel channel, Supplier<? extends CompletionStage<?>> task, long deadlineMs) {
        if (channel == null || task == null || !channel.isActive()) {
            return;
        }
        SerialQueue queue = channel.attr(QUEUE_KEY).get();
        if (queue == null) {
            SerialQueue created = new SerialQueue(channel);
            SerialQueue existing = channel.attr(QUEUE_KEY).setIfAbsent(created);
            queue = existing == null ? created : existing;
            if (existing == null) {
                channel.closeFuture().addListener(ignored -> created.stop());
            }
        }
        queue.offer(new Task(task, deadlineMs > 0 ? deadlineMs : MessageConstant.CHANNEL_ORDERED_TASK_DEADLINE_MS));
    }

    public static CompletionStage<Void> toVoidStage(Mono<Void> mono) {
        return mono == null ? CompletableFuture.completedFuture(null) : mono.toFuture();
    }

    private record Task(Supplier<? extends CompletionStage<?>> supplier, long deadlineMs) { }

    /**
     * cancel 可同步触发 Reactor 的 Redis 清理，不能在 EventLoop/时间轮直接调用。
     * 两个有界池均拒绝时记录错误；占位仍由 owner 校验与 PENDING TTL 兜底，不 CallerRuns。
     */
    static void cancelOffloaded(CompletableFuture<?> future) {
        if (future == null || future.isDone()) {
            return;
        }
        Runnable cancel = () -> future.cancel(true);
        try {
            ThreadPoolManager.qosControlExecutor().execute(cancel);
        } catch (RejectedExecutionException first) {
            try {
                ThreadPoolManager.repositoryExecutor().execute(cancel);
            } catch (RejectedExecutionException second) {
                log.error("取消清理执行器已满，等待底层超时和占位 TTL 回收", second);
            }
        }
    }

    private static final class SerialQueue {
        private final Channel channel;
        private final Queue<Task> pending = new ArrayDeque<>();
        private boolean running;
        private boolean stopped;
        private Execution current;

        private SerialQueue(Channel channel) { this.channel = channel; }

        private void offer(Task task) {
            boolean start = false;
            boolean overflow;
            synchronized (this) {
                if (stopped || !channel.isActive()) {
                    return;
                }
                overflow = pending.size() >= MessageConstant.CHANNEL_ORDERED_TASK_MAX;
                if (!overflow) {
                    pending.add(task);
                    if (!running) {
                        running = true;
                        start = true;
                    }
                }
            }
            if (overflow) {
                failClose("queue-overflow");
            } else if (start) {
                scheduleDrain(false);
            }
        }

        private void scheduleDrain(boolean retry) {
            synchronized (this) {
                if (stopped) {
                    return;
                }
            }
            try {
                ThreadPoolManager.messageProcessorExecutor().execute(this::drainNext);
            } catch (RejectedExecutionException ex) {
                if (retry || ScheduleTimer.scheduleOnce(() -> scheduleDrain(true),
                        MessageConstant.CHANNEL_ORDERED_SCHEDULE_RETRY_DELAY_MS, TimeUnit.MILLISECONDS) == null) {
                    failClose("schedule-rejected");
                }
            } catch (RuntimeException ex) {
                log.error("连接有序调度失败 channelId={}", channel.id().asShortText(), ex);
                failClose("schedule-error");
            }
        }

        private void drainNext() {
            Execution execution;
            synchronized (this) {
                if (stopped) {
                    return;
                }
                Task task = pending.poll();
                if (task == null) {
                    running = false;
                    return;
                }
                execution = new Execution(task);
                current = execution;
            }
            execution.run();
        }

        private void failClose(String reason) {
            log.warn("连接有序任务停止 channelId={} reason={}", channel.id().asShortText(), reason);
            stop();
            channel.close();
        }

        private void stop() {
            Execution execution;
            synchronized (this) {
                if (stopped) {
                    return;
                }
                stopped = true;
                pending.clear();
                execution = current;
                current = null;
            }
            if (execution != null) {
                execution.cancel();
            }
        }

        /** 执行状态同时覆盖 supplier 和其返回的异步操作；迟到的 Future 也必须取消。 */
        private final class Execution {
            private final Task task;
            private boolean settled;
            private Thread runner;
            private CompletableFuture<?> source;
            private Timeout deadline;

            private Execution(Task task) { this.task = task; }

            private void run() {
                synchronized (this) {
                    if (settled) {
                        return;
                    }
                    runner = Thread.currentThread();
                    deadline = ScheduleTimer.scheduleOnce(() -> {
                        if (cancel()) {
                            failClose("task-timeout");
                        }
                    },
                            task.deadlineMs(), TimeUnit.MILLISECONDS);
                }
                if (deadline == null) {
                    synchronized (this) {
                        runner = null;
                    }
                    failClose("deadline-schedule-failed");
                    return;
                }
                CompletableFuture<?> raw;
                try {
                    CompletionStage<?> stage = task.supplier().get();
                    raw = stage == null ? CompletableFuture.completedFuture(null) : stage.toCompletableFuture();
                } catch (Exception ex) {
                    raw = CompletableFuture.failedFuture(ex);
                } finally {
                    synchronized (this) {
                        runner = null;
                        // 任务退出后消耗取消中断，防止平台工作线程复用时污染下一条任务。
                        if (settled) {
                            Thread.interrupted();
                        }
                    }
                }
                synchronized (this) {
                    source = raw;
                    if (settled) {
                        cancelOffloaded(raw);
                        return;
                    }
                }
                raw.whenComplete((ignored, error) -> complete(error));
            }

            private void complete(Throwable error) {
                synchronized (this) {
                    if (settled) {
                        return;
                    }
                    settled = true;
                    deadline.cancel();
                }
                if (error != null) {
                    log.error("连接有序任务失败 channelId={}", channel.id().asShortText(), error);
                }
                synchronized (SerialQueue.this) {
                    if (current == this) {
                        current = null;
                    }
                }
                scheduleDrain(false);
            }

            private boolean cancel() {
                CompletableFuture<?> raw;
                synchronized (this) {
                    if (settled) {
                        return false;
                    }
                    settled = true;
                    if (deadline != null) {
                        deadline.cancel();
                    }
                    // 仅中断仍归当前任务所有的线程；实际 I/O 是否可中断由驱动超时控制。
                    if (runner != null) {
                        runner.interrupt();
                    }
                    raw = source;
                }
                cancelOffloaded(raw);
                return true;
            }
        }
    }
}
