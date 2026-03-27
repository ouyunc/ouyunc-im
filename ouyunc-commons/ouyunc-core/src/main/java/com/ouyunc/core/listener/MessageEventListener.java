package com.ouyunc.core.listener;

import com.ouyunc.base.constant.enums.EventType;
import com.ouyunc.core.listener.event.MessageEvent;

/**
 * @Author fzx
 * message 事件监听器。
 * <p>推荐使用 {@link EventListener} 注解指定执行顺序和 RingBuffer。</p>
 * <ul>
 *   <li>相同 order 的监听器并行执行</li>
 *   <li>不同 order 的监听器按从小到大串行执行</li>
 * </ul>
 */
public interface MessageEventListener<E extends MessageEvent> extends java.util.EventListener {

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