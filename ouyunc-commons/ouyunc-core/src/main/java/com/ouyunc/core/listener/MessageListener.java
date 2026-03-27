package com.ouyunc.core.listener;

import com.ouyunc.base.constant.enums.EventType;
import com.ouyunc.core.listener.event.MessageEvent;

import java.util.EventListener;

/**
 * @Author fzx
 * message 事件监听器。实现类可标注 {@link com.ouyunc.base.model.Order}，值越小越先被调用（与拦截器约定一致）。
 */
public interface MessageListener<E extends MessageEvent> extends EventListener {

    /**
     * 事件类型，必传
     */
    EventType type();


    /**
     * @Author fzx
     * @Description 监听事件通知
     */
    void onEvent(E event);
}