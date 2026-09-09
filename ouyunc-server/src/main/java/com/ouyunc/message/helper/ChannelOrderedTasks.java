package com.ouyunc.message.helper;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.executor.ThreadPoolManager;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单连接业务串行下沉到虚拟线程池：同连接消息保序，PING 不走这里以免被群成员查询堵住。
 */
public final class ChannelOrderedTasks {

    private static final Logger log = LoggerFactory.getLogger(ChannelOrderedTasks.class);

    private static final AttributeKey<SerialQueue> QUEUE_KEY =
            AttributeKey.valueOf("CHANNEL_ORDERED_TASK_QUEUE");

    private ChannelOrderedTasks() {
    }

    public static void execute(Channel channel, Runnable task) {
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

    private static final class SerialQueue {
        private final Channel channel;
        private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
        private final AtomicBoolean running = new AtomicBoolean(false);
        private final AtomicInteger size = new AtomicInteger(0);

        private SerialQueue(Channel channel) {
            this.channel = channel;
        }

        private void offer(Runnable task) {
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
                ThreadPoolManager.messageProcessorExecutor().execute(this::drain);
            }
        }

        private void drain() {
            try {
                Runnable task;
                while ((task = tasks.poll()) != null) {
                    size.decrementAndGet();
                    try {
                        task.run();
                    } catch (Exception e) {
                        log.error("连接有序任务失败 channelId={}", channel.id().asShortText(), e);
                    }
                }
            } finally {
                running.set(false);
                if (!tasks.isEmpty() && running.compareAndSet(false, true)) {
                    ThreadPoolManager.messageProcessorExecutor().execute(this::drain);
                }
            }
        }
    }
}
