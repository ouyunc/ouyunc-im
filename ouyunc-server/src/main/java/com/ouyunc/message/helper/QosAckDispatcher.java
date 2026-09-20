package com.ouyunc.message.helper;

import com.ouyunc.base.constant.QosControlConstant;
import com.ouyunc.base.executor.ThreadPoolManager;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * ACK 非阻塞准入：全局与每连接分别限流，拒绝时绝不回退到网络线程执行。
 * 许可绑定完整异步链，而非绑定启动订阅的 Runnable；成功、异常和取消均释放。
 * 调用方必须给异步链设置超时，防止未完成订阅永久占用容量。
 */
public final class QosAckDispatcher {
    private static final Semaphore GLOBAL = new Semaphore(QosControlConstant.MAX_IN_FLIGHT);
    private static final AttributeKey<Semaphore> PER_CHANNEL = AttributeKey.valueOf("qos.ack.admission");

    private QosAckDispatcher() {
    }

    public static void execute(Channel channel, Supplier<CompletionStage<Void>> task) {
        Semaphore local = channel.attr(PER_CHANNEL).get();
        if (local == null) {
            Semaphore created = new Semaphore(QosControlConstant.MAX_PER_CHANNEL);
            Semaphore existing = channel.attr(PER_CHANNEL).setIfAbsent(created);
            local = existing == null ? created : existing;
        }
        if (!local.tryAcquire()) {
            throw new RejectedExecutionException("ACK channel capacity exhausted");
        }
        if (!GLOBAL.tryAcquire()) {
            local.release();
            throw new RejectedExecutionException("ACK global capacity exhausted");
        }
        Semaphore admitted = local;
        // once 防止同步完成、注册回调失败等边界重复释放许可。
        java.util.concurrent.atomic.AtomicBoolean released = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable release = () -> {
            if (released.compareAndSet(false, true)) {
                admitted.release();
                GLOBAL.release();
            }
        };
        try {
            ThreadPoolManager.qosControlExecutor().execute(() -> {
                try {
                    task.get().whenComplete((ignored, error) -> release.run());
                } catch (Throwable error) {
                    release.run();
                    throw error;
                }
            });
        } catch (RuntimeException | Error error) {
            release.run();
            throw error;
        }
    }
}
