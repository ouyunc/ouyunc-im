package com.ouyunc.core.listener;

import com.ouyunc.core.listener.event.MessageEvent;

import java.util.EventListener;

/**
 * @Author fzx
 * message 事件监听器。实现类可标注 {@link com.ouyunc.base.model.Order}，值越小越先被调用（与拦截器约定一致）。
 */
@FunctionalInterface
public interface MessageListener<E extends MessageEvent> extends EventListener {
    /**
     * @Author fzx
     * @Description 监听事件通知
     */
    void onApplicationEvent(E event);
}