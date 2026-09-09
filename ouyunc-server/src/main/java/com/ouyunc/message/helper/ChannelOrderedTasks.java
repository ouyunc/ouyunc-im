package com.ouyunc.message.helper;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.executor.ThreadPoolManager;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 单连接业务串行下沉到虚拟线程池：同连接消息保序，PING 不走这里以免被群成员查询堵住。
 * 异步任务必须等 CompletionStage/Mono 完成再跑下一条，避免连发 subscribe 乱序并打满队列。
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

        private SerialQueue(Channel channel) {
            this.channel = channel;
        }

        private void offer(Supplier<? extends CompletionStage<?>> task) {
            int pending = size.incrementAndGet();
            if (pending > MessageConstant.CHANNEL_ORDERED_TASK_MAX) {
                size.decrementAndGet();
                log.error("连接有序队列溢出 channelId={} pending={}，关闭连接",
                        channel.id().asShortText(), pending);
                channel.close();
                return;
            }
            tasks.add(task);
            if (running.compareAndSet(false, true)) {
                ThreadPoolManager.messageProcessorExecutor().execute(this::drainNext);
            }
        }

        private void drainNext() {
            if (!channel.isActive()) {
                running.set(false);
                tasks.clear();
                size.set(0);
                return;
            }
            Supplier<? extends CompletionStage<?>> task = tasks.poll();
            if (task == null) {
                running.set(false);
                if (!tasks.isEmpty() && running.compareAndSet(false, true)) {
                    ThreadPoolManager.messageProcessorExecutor().execute(this::drainNext);
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
            stage.whenComplete((ignored, error) -> {
                if (error != null) {
                    log.error("连接有序异步任务失败 channelId={}", channel.id().asShortText(), error);
                }
                ThreadPoolManager.messageProcessorExecutor().execute(this::drainNext);
            });
        }
    }
}
