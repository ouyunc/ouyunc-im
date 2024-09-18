package com.ouyunc.core.listener;

import com.ouyunc.core.listener.event.MessageEvent;

import java.util.EventListener;

/**
 * @Author fzx
 * message 事件监听器
 */
@FunctionalInterface
public interface MessageListener<E extends MessageEvent> extends EventListener {
    /**
     * @Author fzx
     * @Description 监听事件通知
     */
    void onApplicationEvent(E event);
}