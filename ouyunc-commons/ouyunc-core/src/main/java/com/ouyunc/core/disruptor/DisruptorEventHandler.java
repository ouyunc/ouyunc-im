package com.ouyunc.core.disruptor;

import com.lmax.disruptor.EventHandler;
import com.ouyunc.core.listener.event.GenericEvent;
import com.ouyunc.core.processor.Processor;

/**
 * disruptor事件处理器（可继承扩展）
 * @param <T> 数据类型
 */
public class DisruptorEventHandler<T> implements EventHandler<GenericEvent<T>> {
    // 事件处理器
    private final Processor<T, Long> processor;

    public DisruptorEventHandler(Processor<T, Long> processor) {
        this.processor = processor;
    }

    // 回调处理事件
    @Override
    public void onEvent(GenericEvent<T> event, long sequence, boolean endOfBatch) {
        try {
            processor.process(event.getSource(), event.getTimestamp());
        } finally {
            event.clear();  // 必须清理引用
        }
    }
}