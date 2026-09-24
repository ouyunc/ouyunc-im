package com.ouyunc.message.helper;

import com.ouyunc.base.constant.QosControlConstant;
import com.ouyunc.message.monitor.QosRetryCancelMetrics;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletionStage;
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

    private QosAckBackpressure() { }

    /** 与 QosAckDispatcher 配合使用；保留原始 ACK 校验，不因缓冲而跳过权限检查。 */
    public static void execute(Channel channel, Supplier<CompletionStage<Void>> task) {
        try {
            QosAckDispatcher.execute(channel, task);
        } catch (RejectedExecutionException error) {
            QosRetryCancelMetrics.ackDispatchReject();
            Semaphore created = new Semaphore(QosControlConstant.MAX_PENDING_ACKS_PER_CHANNEL);
            Semaphore existing = channel.attr(LOCAL).setIfAbsent(created);
            Semaphore local = existing == null ? created : existing;
            if (!local.tryAcquire()) {
                throw error;
            }
            if (!PENDING.tryAcquire()) {
                local.release();
                throw error;
            }
            PendingAck pending = new PendingAck(channel, task, local);
            channel.closeFuture().addListener(pending);
            pending.schedule();
        }
    }

    private static final class PendingAck implements io.netty.channel.ChannelFutureListener {
        private final Channel channel;
        private final Supplier<CompletionStage<Void>> task;
        private final Semaphore local;
        private final AtomicBoolean done = new AtomicBoolean();
        private int attempts;

        private PendingAck(Channel channel, Supplier<CompletionStage<Void>> task, Semaphore local) {
            this.channel = channel;
            this.task = task;
            this.local = local;
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
