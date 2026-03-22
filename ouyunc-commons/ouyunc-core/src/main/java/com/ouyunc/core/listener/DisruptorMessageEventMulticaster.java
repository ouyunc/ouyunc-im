package com.ouyunc.core.listener;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import com.ouyunc.core.disruptor.DisruptorEventFactory;
import com.ouyunc.core.listener.event.GenericEvent;
import com.ouyunc.core.listener.event.MessageEvent;

import java.util.Objects;
import java.util.concurrent.ThreadFactory;

/**
 * 基于 Disruptor 的本地消息多播：异步发布仅入环快速返回，由单消费者按序调用监听器（与 Order 注解 / 注册序一致）；
 * 同步发布在当前线程直接 {@link #dispatchToListeners(MessageEvent)}，无排队。
 *
 * @see com.ouyunc.base.model.Order
 */
public class DisruptorMessageEventMulticaster extends AbstractMessageEventMulticaster {

    private final Disruptor<GenericEvent<MessageEvent>> disruptor;
    private final RingBuffer<GenericEvent<MessageEvent>> ringBuffer;

    public DisruptorMessageEventMulticaster(int bufferSize) {
        this(bufferSize, DaemonThreadFactory.INSTANCE, ProducerType.MULTI, new com.lmax.disruptor.YieldingWaitStrategy());
    }

    public DisruptorMessageEventMulticaster(int bufferSize, ThreadFactory threadFactory, ProducerType producerType,
                                            WaitStrategy waitStrategy) {
        Objects.requireNonNull(threadFactory, "threadFactory");
        Objects.requireNonNull(waitStrategy, "waitStrategy");
        Disruptor<GenericEvent<MessageEvent>> d = new Disruptor<>(
                new DisruptorEventFactory<>(),
                bufferSize,
                threadFactory,
                producerType,
                waitStrategy
        );
        EventHandler<GenericEvent<MessageEvent>> handler = (event, sequence, endOfBatch) -> {
            try {
                MessageEvent payload = event.getSource();
                if (payload != null) {
                    dispatchToListeners(payload);
                }
            } finally {
                event.clear();
            }
        };
        d.handleEventsWith(handler);
        this.disruptor = d;
        this.ringBuffer = d.start();
    }

    @Override
    public void multicastEvent(MessageEvent event, boolean async) {
        if (async) {
            long sequence = ringBuffer.next();
            try {
                GenericEvent<MessageEvent> slot = ringBuffer.get(sequence);
                slot.setSource(event);
            } finally {
                ringBuffer.publish(sequence);
            }
        } else {
            dispatchToListeners(event);
        }
    }

    /**
     * 停止消费环（通常在进程退出前调用，会等待已在环中的事件处理完毕）。
     */
    public void shutdown() {
        disruptor.shutdown();
    }
}
