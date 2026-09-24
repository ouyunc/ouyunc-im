package com.ouyunc.message.helper;

import com.ouyunc.base.constant.QosControlConstant;
import com.ouyunc.message.monitor.QosRetryCancelMetrics;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** ACK 过载缓冲：EventLoop 只尝试非阻塞准入，不执行鉴权、Redis 或业务处理。 */
public final class QosAckBackpressure {
    private static final Logger log = LoggerFactory.getLogger(QosAckBackpressure.class);
    private static final Semaphore PENDING = new Semaphore(QosControlConstant.MAX_PENDING_ACKS);
    private static final AttributeKey<Semaphore> LOCAL = AttributeKey.valueOf("qos.ack.pending");
    /** 同一连接上相同 packetId 的等待 ACK 合并；不能按用户合并，否则会吞掉其他设备的重发取消。 */
    private static final AttributeKey<ConcurrentHashMap<Long, Boolean>> WAITING =
            AttributeKey.valueOf("qos.ack.waiting.packets");

    private QosAckBackpressure() { }

    /** 与 QosAckDispatcher 配合使用；保留原始 ACK 校验，不因缓冲而跳过权限检查。 */
    public static void execute(Channel channel, long packetId, Supplier<CompletionStage<Void>> task) {
        try {
            QosAckDispatcher.execute(channel, task);
        } catch (RejectedExecutionException error) {
            QosRetryCancelMetrics.ackDispatchReject();
            ConcurrentHashMap<Long, Boolean> waiting = waiting(channel);
            if (waiting.putIfAbsent(packetId, Boolean.TRUE) != null) {
                return;
            }
            Semaphore created = new Semaphore(QosControlConstant.MAX_PENDING_ACKS_PER_CHANNEL);
            Semaphore existing = channel.attr(LOCAL).setIfAbsent(created);
            Semaphore local = existing == null ? created : existing;
            if (!local.tryAcquire()) {
                waiting.remove(packetId);
                throw error;
            }
            if (!PENDING.tryAcquire()) {
                local.release();
                waiting.remove(packetId);
                throw error;
            }
            PendingAck pending = new PendingAck(channel, packetId, task, local, waiting);
            channel.closeFuture().addListener(pending);
            pending.schedule();
        }
    }

    private static ConcurrentHashMap<Long, Boolean> waiting(Channel channel) {
        ConcurrentHashMap<Long, Boolean> created = new ConcurrentHashMap<>();
        ConcurrentHashMap<Long, Boolean> existing = channel.attr(WAITING).setIfAbsent(created);
        return existing == null ? created : existing;
    }

    private static final class PendingAck implements io.netty.channel.ChannelFutureListener {
        private final Channel channel;
        private final long packetId;
        private final Supplier<CompletionStage<Void>> task;
        private final Semaphore local;
        private final ConcurrentHashMap<Long, Boolean> waiting;
        private final AtomicBoolean done = new AtomicBoolean();
        private int attempts;

        private PendingAck(Channel channel, long packetId, Supplier<CompletionStage<Void>> task, Semaphore local,
                           ConcurrentHashMap<Long, Boolean> waiting) {
            this.channel = channel;
            this.packetId = packetId;
            this.task = task;
            this.local = local;
            this.waiting = waiting;
        }

        private void schedule() {
            if (done.get()) {
                return;
            }
            try {
                channel.eventLoop().schedule(this::attempt, QosControlConstant.ACK_RETRY_DELAY_MS, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException error) {
                finish();
            }
        }

        private void attempt() {
            if (done.get() || !channel.isActive()) {
                finish();
                return;
            }
            try {
                QosAckDispatcher.execute(channel, task);
                finish();
            } catch (RejectedExecutionException error) {
                if (++attempts < QosControlConstant.ACK_RETRY_ATTEMPTS) {
                    schedule();
                } else {
                    QosRetryCancelMetrics.ackDispatchReject();
                    log.warn("ACK 缓冲达到重试上限 channel={}", channel.id());
                    finish();
                }
            } catch (RuntimeException error) {
                finish();
                log.warn("ACK 缓冲调度失败 channel={}", channel.id(), error);
            }
        }

        private void finish() {
            if (done.compareAndSet(false, true)) {
                channel.closeFuture().removeListener(this);
                waiting.remove(packetId);
                local.release();
                PENDING.release();
            }
        }

        @Override
        public void operationComplete(io.netty.channel.ChannelFuture future) {
            finish();
        }
    }
}
