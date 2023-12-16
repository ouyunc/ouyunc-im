package com.ouyunc.im.listener;

import com.ouyunc.im.event.IMEvent;

import java.util.EventListener;

/**
 * @Author fangzhenxun
 * im 事件监听器
 */
@FunctionalInterface
public interface IMListener<E extends IMEvent> extends EventListener {
    /**
     * @Author fangzhenxun
     * @Description 监听事件通知
     * @param event
     * @return void
     */
    void onApplicationEvent(E event);
}