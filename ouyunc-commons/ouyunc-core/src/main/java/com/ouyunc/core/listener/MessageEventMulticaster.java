package com.ouyunc.core.listener;

import com.ouyunc.core.listener.event.MessageEvent;

import java.util.Collection;

/**
 * @Author fzx
 * message 事件多播器
 */
public interface MessageEventMulticaster {

    /**
     * @Author fzx
     * @Description 添加某个监听器
     */
    void addMessageListener(MessageListener<MessageEvent> listener);


    /**
     * @Author fzx
     * @Description 获取事件的所有监听器
     */
    Collection<MessageListener<MessageEvent>> getMessageListeners(MessageEvent event);

    /**
     * @Author fzx
     * @Description 移除某个监听器
     */
    void removeMessageListener(MessageListener<MessageEvent> listener);

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
    void multicastEvent(MessageEvent event, boolean sync);
}