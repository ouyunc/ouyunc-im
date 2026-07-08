package com.ouyunc.core.listener;

import com.google.common.collect.Lists;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.metrics.DisruptorRingMetrics;

import java.util.Collection;
import java.util.List;

/**
 * @Author fzx
 * message 事件多播器
 */
public interface MessageEventMulticaster {

    /**
     * @Author fzx
     * @Description 添加某个监听器
     */
    void addMessageListener(MessageEventListener<MessageEvent> listener);


    /**
     * @Author fzx
     * @Description 获取事件的所有监听器
     */
    Collection<MessageEventListener<MessageEvent>> getMessageListeners(MessageEvent event);

    /**
     * @Author fzx
     * @Description 移除某个监听器
     */
    void removeMessageListener(MessageEventListener<MessageEvent> listener);

    /**
     * @Author fzx
     * @Description 移除某个事件的所有监听器
     */
    void removeMessageListener(MessageEvent event);

    /**
     * @Author fzx
     * @Description 移除所有监听器
     */
    void removeAllMessageListeners();


    /**
     * @Author fzx
     * @Description 多播事件
     */
    void multicastEvent(MessageEvent event, boolean async);



    /**
     * @Author fzx
     * @Description 多播事件
     */
    void multicastEventWithExecutor(MessageEvent event, boolean async);


    /**
     * 拉取当前各事件类型、各 Ring 上已创建 Disruptor 的 RingBuffer 指标（懒加载未创建的环不会出现）。
     */
    default List<DisruptorRingMetrics> snapshotDisruptorMetrics() {
        return Lists.newArrayList();
    }
}