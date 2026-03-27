package com.ouyunc.core.listener;


import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import com.ouyunc.base.constant.enums.EventType;
import com.ouyunc.core.listener.event.MessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * @Author fzx
 * @Description: 抽象message 多播器；同一事件类型下多个 listener 按注册顺序依次调用（LinkedHashMultimap 保序）。
 **/
public abstract class AbstractMessageEventMulticaster implements MessageEventMulticaster{

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * 监听器（按事件类型分组，组内顺序为注册顺序）；每实例独立，便于组合多播器委托。
     */
    private final SetMultimap<EventType, MessageListener<MessageEvent>> messageListeners = LinkedHashMultimap.create();


    /**
     * @Author fzx
     * @Description 添加im 监听器
     */
    @Override
    public void addMessageListener(MessageListener<MessageEvent> listener) {
        if (listener != null) {
            EventType eventType = listener.type();
            if (eventType == null) {
                log.warn("message 监听器 {} 未声明 type()，忽略注册", listener);
                return;
            }
            messageListeners.put(eventType, listener);
        }
    }

    /**
     * @Author fzx
     * @Description 获取该事件的所有监听器
     */
    @Override
    public Collection<MessageListener<MessageEvent>> getMessageListeners(MessageEvent event) {
        if (event == null || event.getType() == null) {
            return null;
        }
        return messageListeners.get(event.getType());
    }

    /**
     * @Author fzx
     * @Description 移除某个监听器
     */
    @Override
    public void removeMessageListener(MessageListener<MessageEvent> listener) {
        if (listener != null) {
            EventType eventType = listener.type();
            if (eventType != null) {
                messageListeners.remove(eventType, listener);
            }
        }
    }


    /**
     * @Author fzx
     * @Description 根据事件获取该事件的所有的监听器
     */
    @Override
    public void removeMessageListener(MessageEvent event) {
        if (event != null && event.getType() != null) {
            messageListeners.removeAll(event.getType());
        }
    }


    /**
     * @Author fzx
     * @Description 移除所有im监听器
     */
    @Override
    public void removeAllMessageListeners() {
        messageListeners.clear();
    }

    /**
     * 执行单个监听器（异常吞掉仅打日志）。
     */
    protected void invokeListener(MessageListener<MessageEvent> listener, MessageEvent event) {
        try {
            listener.onEvent(event);
        } catch (Throwable err) {
            log.error("message 监听器 {} 执行事件 {} 失败：{}", listener, event, err.getMessage());
        }
    }

    /**
     * 按注册顺序将事件分发给所有匹配的监听器。
     */
    protected void dispatchToListeners(MessageEvent event) {
        if (event == null) {
            return;
        }
        Collection<MessageListener<MessageEvent>> listeners = getMessageListeners(event);
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        for (MessageListener<MessageEvent> listener : listeners) {
            invokeListener(listener, event);
        }
    }
}
