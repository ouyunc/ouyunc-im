package com.ouyunc.im.listener;

import com.ouyunc.im.event.IMEvent;

import java.util.Collection;

/**
 * @Author fangzhenxun
 * im 事件多播器
 */
public interface IMEventMulticaster {

    /**
     * @Author fangzhenxun
     * @Description 添加某个监听器
     * @param listener
     * @return void
     */
    void addImListener(IMListener<?> listener);


    /**
     * @Author fangzhenxun
     * @Description 获取事件的所有监听器
     * @param event
     * @return void
     */
    Collection<IMListener<?>> getImListeners(IMEvent event);

    /**
     * @Author fangzhenxun
     * @Description 移除某个监听器
     * @param listener
     * @return void
     */
    void removeListener(IMListener<?> listener);

    /**
     * @Author fangzhenxun
     * @Description 移除某个事件的所有监听器
     * @param event
     * @return void
     */
    void removeListener(IMEvent event);

    /**
     * @Author fangzhenxun
     * @Description 移除所有监听器
     * @param
     * @return void
     */
    void removeAllListeners();


    /**
     * @Author fangzhenxun
     * @Description 同步多播事件
     * @param event
     * @return void
     */
    void multicastEvent(IMEvent event, boolean sync);
}