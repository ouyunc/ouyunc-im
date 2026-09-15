package com.ouyunc.message.helper;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.message.schedule.ScheduleTimer;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 单连接业务串行下沉到虚拟线程池：同连接消息保序，PING 不走这里以免被群成员查询堵住。
 * 异步任务必须等 CompletionStage/Mono 完成再跑下一条，避免连发 subscribe 乱序并打满队列。
 *
 * <p>调度入口统一捕获 {@link RejectedExecutionException}：清理 running、延迟重试一次，
 * 仍失败则关连并清空队列。禁止 CallerRunsPolicy 把重活退回 EventLoop。</p>
 */
public final class ChannelOrderedTasks {

    private static final Logger log = LoggerFactory.getLogger(ChannelOrderedTasks.class);

    private static final AttributeKey<SerialQueue> QUEUE_KEY =
            AttributeKey.valueOf("CHANNEL_ORDERED_TASK_QUEUE");

    private ChannelOrderedTasks() {
    }

    public static void execute(Channel channel, Runnable task) {
        executeAsync(channel, () -> {
            task.run();
            return CompletableFuture.completedFuture(null);
        });
    }

    public static void executeAsync(Channel channel, Supplier<? extends CompletionStage<?>> task) {
        if (channel == null || task == null || !channel.isActive()) {
            return;
        }
        SerialQueue created = new SerialQueue(channel);
        SerialQueue queue = channel.attr(QUEUE_KEY).setIfAbsent(created);
        if (queue == null) {
            queue = created;
        }
        queue.offer(task);
    }

    public static CompletionStage<Void> toVoidStage(Mono<Void> mono) {
        if (mono == null) {
            return CompletableFuture.completedFuture(null);
        }
        return mono.toFuture();
    }

    private static final class SerialQueue {
        private final Channel channel;
        private final Queue<Supplier<? extends CompletionStage<?>>> tasks = new ConcurrentLinkedQueue<>();
        private final AtomicBoolean running = new AtomicBoolean(false);
        private final AtomicInteger size = new AtomicInteger(0);
        private final AtomicBoolean closeHooked = new AtomicBoolean(false);
        private final AtomicBoolean retryScheduled = new AtomicBoolean(false);

        private SerialQueue(Channel channel) {
            this.channel = channel;
            hookCloseCleanup();
        }

        private void hookCloseCleanup() {
            if (!closeHooked.compareAndSet(false, true)) {
                return;
            }
            channel.closeFuture().addListener(future -> clearAndStop("channel-closed"));
        }

        private void offer(Supplier<? extends CompletionStage<?>> task) {
            if (!channel.isActive()) {
                return;
            }
            int pending = size.incrementAndGet();
            if (pending > MessageConstant.CHANNEL_ORDERED_TASK_MAX) {
                size.decrementAndGet();
                log.error("连接有序队列溢出 channelId={} pending={}，关闭连接",
                        channel.id().asShortText(), pending);
                failClose("queue-overflow");
                return;
            }
            tasks.add(task);
            tryStart();
        }

        private void tryStart() {
            if (running.compareAndSet(false, true)) {
                scheduleDrain(false);
            }
        }

        /**
         * 唯一调度入口：捕获拒绝；失败时明确状态转换（重试 / 关连清队列）。
         */
        private void scheduleDrain(boolean fromRetry) {
            if (!channel.isActive()) {
                clearAndStop("channel-inactive");
                return;
            }
            try {
                ThreadPoolManager.messageProcessorExecutor().execute(this::drainNext);
            } catch (RejectedExecutionException ex) {
                log.error("连接有序调度被拒绝 channelId={} fromRetry={}",
                        channel.id().asShortText(), fromRetry, ex);
                if (!fromRetry) {
                    scheduleRetryOnce();
                    return;
                }
                failClose("schedule-rejected");
            } catch (RuntimeException ex) {
                log.error("连接有序调度异常 channelId={}", channel.id().asShortText(), ex);
                failClose("schedule-error");
            }
        }

        private void scheduleRetryOnce() {
            if (!retryScheduled.compareAndSet(false, true)) {
                // 已有重试在途，保持 running=true 等回调
                return;
            }
            // ScheduleTimer 调度失败时返回 null，必须立刻 failClose，否则 running 永久卡住
            var timeout = ScheduleTimer.scheduleOnce(() -> {
                retryScheduled.set(false);
                if (!channel.isActive()) {
                    clearAndStop("channel-inactive-on-retry");
                    return;
                }
                // running 仍为 true，直接再投递 drain
                scheduleDrain(true);
            }, MessageConstant.CHANNEL_ORDERED_SCHEDULE_RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
            if (timeout == null) {
                failClose("schedule-retry-failed");
            }
        }

        private void drainNext() {
            if (!channel.isActive()) {
                clearAndStop("channel-inactive");
                return;
            }
            Supplier<? extends CompletionStage<?>> task = tasks.poll();
            if (task == null) {
                running.set(false);
                // 防 TOCTOU：停跑后若又有入队则重启
                if (!tasks.isEmpty()) {
                    tryStart();
                }
                return;
            }
            size.decrementAndGet();
            CompletionStage<?> stage;
            try {
                stage = task.get();
            } catch (Exception e) {
                log.error("连接有序任务失败 channelId={}", channel.id().asShortText(), e);
                stage = CompletableFuture.completedFuture(null);
            }
            if (stage == null) {
                stage = CompletableFuture.completedFuture(null);
            }
            CompletableFuture<?> timed = stage.toCompletableFuture()
                    .orTimeout(MessageConstant.CHANNEL_ORDERED_TASK_DEADLINE_MS, TimeUnit.MILLISECONDS);
            timed.whenComplete((ignored, error) -> {
                if (error != null) {
                    if (error instanceof TimeoutException
                            || (error.getCause() instanceof TimeoutException)) {
                        log.error("连接有序任务超时 channelId={} deadlineMs={}",
                                channel.id().asShortText(), MessageConstant.CHANNEL_ORDERED_TASK_DEADLINE_MS);
                    } else {
                        log.error("连接有序异步任务失败 channelId={}", channel.id().asShortText(), error);
                    }
                }
                scheduleDrain(false);
            });
        }

        private void failClose(String reason) {
            log.error("连接有序队列失败关闭 channelId={} reason={} pending={}",
                    channel.id().asShortText(), reason, size.get());
            clearAndStop(reason);
            if (channel.isActive()) {
                channel.close();
            }
        }

        private void clearAndStop(String reason) {
            running.set(false);
            retryScheduled.set(false);
            tasks.clear();
            size.set(0);
            log.debug("连接有序队列已清理 channelId={} reason={}", channel.id().asShortText(), reason);
        }
    }
}
