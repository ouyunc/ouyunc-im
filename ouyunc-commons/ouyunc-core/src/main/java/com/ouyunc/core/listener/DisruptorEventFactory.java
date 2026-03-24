package com.ouyunc.core.listener;

import com.lmax.disruptor.EventFactory;
import com.ouyunc.core.listener.event.GenericEvent;


/**
 * disruptor 事件工厂
 * @param <T>
 */
public class DisruptorEventFactory<T> implements EventFactory<GenericEvent<T>> {


    /**
     * 创建事件
     * @return
     */
    @Override
    public GenericEvent<T> newInstance() {
        return new GenericEvent<>();
    }
}
