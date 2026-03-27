package com.ouyunc.core.listener;


import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import com.ouyunc.base.constant.enums.EventRingEnum;
import com.ouyunc.base.constant.enums.EventType;
import com.ouyunc.core.listener.event.MessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author fzx
 * @Description: 抽象message 多播器；同一事件类型下多个 listener 按注册顺序依次调用（LinkedHashMultimap 保序）。
 **/
public abstract class AbstractMessageEventMulticaster implements MessageEventMulticaster{

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * 监听器（按事件类型分组，组内顺序为注册顺序）；每实例独立，便于组合多播器委托。
     */
    private final SetMultimap<EventType, MessageEventListener<MessageEvent>> messageListeners = LinkedHashMultimap.create();
    private final Object listenersMonitor = new Object();

    /**
     * 按事件类型缓存排序后的监听器，避免每次发布事件都复制+排序。
     */
    private final Map<EventType, List<MessageEventListener<MessageEvent>>> orderedListenersCache = new ConcurrentHashMap<>();
    /**
     * 监听器类级别元数据缓存，避免热路径反射。
     */
    private final Map<Class<?>, ListenerMeta> listenerMetaCache = new ConcurrentHashMap<>();


    /**
     * @Author fzx
     * @Description 添加im 监听器
     */
    @Override
    public void addMessageListener(MessageEventListener<MessageEvent> listener) {
        if (listener != null) {
            EventType eventType = listener.type();
            if (eventType == null) {
                log.warn("message 监听器 {} 未声明 type()，忽略注册", listener);
                return;
            }
            synchronized (listenersMonitor) {
                messageListeners.put(eventType, listener);
                orderedListenersCache.remove(eventType);
            }
        }
    }

    /**
     * @Author fzx
     * @Description 获取该事件的所有监听器
     */
    @Override
    public Collection<MessageEventListener<MessageEvent>> getMessageListeners(MessageEvent event) {
        if (event == null || event.getType() == null) {
            return null;
        }
        synchronized (listenersMonitor) {
            return List.copyOf(messageListeners.get(event.getType()));
        }
    }

    /**
     * @Author fzx
     * @Description 移除某个监听器
     */
    @Override
    public void removeMessageListener(MessageEventListener<MessageEvent> listener) {
        if (listener != null) {
            EventType eventType = listener.type();
            if (eventType != null) {
                synchronized (listenersMonitor) {
                    messageListeners.remove(eventType, listener);
                    orderedListenersCache.remove(eventType);
                }
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
            synchronized (listenersMonitor) {
                messageListeners.removeAll(event.getType());
                orderedListenersCache.remove(event.getType());
            }
        }
    }


    /**
     * @Author fzx
     * @Description 移除所有im监听器
     */
    @Override
    public void removeAllMessageListeners() {
        synchronized (listenersMonitor) {
            messageListeners.clear();
            orderedListenersCache.clear();
        }
    }

    /**
     * 执行单个监听器（异常吞掉仅打日志）。
     */
    protected void invokeListener(MessageEventListener<MessageEvent> listener, MessageEvent event) {
        try {
            listener.onEvent(event);
        } catch (Throwable err) {
            log.error("message 监听器 {} 执行事件 {} 失败：{}", listener, event, err.getMessage());
        }
    }

    protected int resolveListenerOrder(MessageEventListener<MessageEvent> listener) {
        return resolveListenerMeta(listener).order();
    }

    protected EventRingEnum resolveListenerRing(MessageEventListener<MessageEvent> listener) {
        return resolveListenerMeta(listener).ring();
    }

    private ListenerMeta resolveListenerMeta(MessageEventListener<MessageEvent> listener) {
        return listenerMetaCache.computeIfAbsent(listener.getClass(), ignored -> {
            EventListener annotation = listener.getClass().getAnnotation(EventListener.class);
            if (annotation == null) {
                return new ListenerMeta(100, EventRingEnum.NORMAL);
            }
            EventRingEnum ring = annotation.ring() == null ? EventRingEnum.NORMAL : annotation.ring();
            return new ListenerMeta(annotation.order(), ring);
        });
    }

    protected List<MessageEventListener<MessageEvent>> getOrderedListeners(MessageEvent event) {
        if (event == null || event.getType() == null) {
            return List.of();
        }
        EventType eventType = event.getType();
        List<MessageEventListener<MessageEvent>> cached = orderedListenersCache.get(eventType);
        if (cached != null) {
            return cached;
        }
        synchronized (listenersMonitor) {
            List<MessageEventListener<MessageEvent>> current = orderedListenersCache.get(eventType);
            if (current != null) {
                return current;
            }
            Collection<MessageEventListener<MessageEvent>> listeners = messageListeners.get(eventType);
            if (listeners.isEmpty()) {
                orderedListenersCache.put(eventType, List.of());
                return List.of();
            }
            List<MessageEventListener<MessageEvent>> ordered = new ArrayList<>(listeners);
            ordered.sort(Comparator.comparingInt(this::resolveListenerOrder));
            List<MessageEventListener<MessageEvent>> immutable = Collections.unmodifiableList(ordered);
            orderedListenersCache.put(eventType, immutable);
            return immutable;
        }
    }


    /**
     * 按注解 order 顺序将事件分发给所有匹配的监听器。
     */
    protected void dispatchToListeners(MessageEvent event) {
        if (event == null) {
            return;
        }
        List<MessageEventListener<MessageEvent>> orderedListeners = getOrderedListeners(event);
        int size = orderedListeners.size();
        if (size == 0) {
            return;
        }
        if (size == 1) {
            invokeListener(orderedListeners.get(0), event);
            return;
        }
        for (MessageEventListener<MessageEvent> listener : orderedListeners) {
            invokeListener(listener, event);
        }
    }

    private static final class ListenerMeta {
        private final int order;
        private final EventRingEnum ring;

        private ListenerMeta(int order, EventRingEnum ring) {
            this.order = order;
            this.ring = ring;
        }

        private int order() {
            return order;
        }

        private EventRingEnum ring() {
            return ring;
        }
    }
}
