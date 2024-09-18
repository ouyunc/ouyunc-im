package com.ouyunc.core.listener;


import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.ouyunc.base.utils.ObjectUtil;
import com.ouyunc.core.listener.event.MessageEvent;

import java.util.Collection;

/**
 * @Author fzx
 * @Description: 抽象message 多播器
 **/
public abstract class AbstractMessageEventMulticaster implements MessageEventMulticaster{

    /**
     * 监听器
     */
    private static final SetMultimap<Class<?>, MessageListener<MessageEvent>> messageListeners = HashMultimap.create();


    /**
     * @Author fzx
     * @Description 添加im 监听器
     */
    @Override
    public void addMessageListener(MessageListener<MessageEvent> listener) {
        if (listener != null) {
            // 获取该listener 的泛型
            Class<?> eventTypeClass = ObjectUtil.getInterfaceGenerics(listener);
            messageListeners.put(eventTypeClass, listener);
        }
    }

    /**
     * @Author fzx
     * @Description 获取该事件的所有监听器
     */
    @Override
    public Collection<MessageListener<MessageEvent>> getMessageListeners(MessageEvent event) {
        if (event == null) {
            return null;
        }
        return messageListeners.get(event.getClass());
    }

    /**
     * @Author fzx
     * @Description 移除某个监听器
     */
    @Override
    public void removeMessageListener(MessageListener<MessageEvent> listener) {
        if (listener != null) {
            Class<?> eventTypeClass = ObjectUtil.getInterfaceGenerics(listener);
            messageListeners.remove(eventTypeClass, listener);
        }
    }


    /**
     * @Author fzx
     * @Description 根据事件获取该事件的所有的监听器
     */
    @Override
    public void removeMessageListener(MessageEvent event) {
        if (event != null) {
            messageListeners.removeAll(event.getClass());
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
}
