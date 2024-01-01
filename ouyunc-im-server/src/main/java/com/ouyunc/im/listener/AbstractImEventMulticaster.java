package com.ouyunc.im.listener;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.ouyunc.im.event.IMEvent;
import com.ouyunc.im.utils.ObjectUtil;

import java.util.Collection;

/**
 * @Author fangzhenxun
 * @Description: 抽象im 多播器
 **/
public abstract class AbstractImEventMulticaster implements IMEventMulticaster{

    /**
     * im监听器
     */
    public volatile SetMultimap<Class<?>, IMListener<?>> imListeners = HashMultimap.create();


    /**
     * @Author fangzhenxun
     * @Description 添加im 监听器
     * @param listener
     * @return void
     */
    @Override
    public void addImListener(IMListener<?> listener) {
        if (listener != null) {
            // 获取该listener 的泛型
            Class<?> eventTypeClass = ObjectUtil.getInterfaceGenerics(listener);
            this.imListeners.put(eventTypeClass, listener);
        }
    }

    /**
     * @Author fangzhenxun
     * @Description 获取该事件的所有监听器
     * @param event
     * @return java.util.Collection<com.ouyunc.im.listener.IMListener<?>>
     */
    @Override
    public Collection<IMListener<?>> getImListeners(IMEvent event) {
        if (event == null) {
            return null;
        }
        return imListeners.get(event.getClass());
    }

    /**
     * @Author fangzhenxun
     * @Description 移除某个监听器
     * @param listener
     * @return void
     */
    @Override
    public void removeListener(IMListener<?> listener) {
        if (listener != null) {
            Class<?> eventTypeClass = ObjectUtil.getInterfaceGenerics(listener);
            this.imListeners.remove(eventTypeClass, listener);
        }
    }


    /**
     * @Author fangzhenxun
     * @Description 根据事件获取该事件的所有的监听器
     * @param event
     * @return void
     */
    @Override
    public void removeListener(IMEvent event) {
        if (event != null) {
            this.imListeners.removeAll(event.getClass());
        }
    }


    /**
     * @Author fangzhenxun
     * @Description 移除所有im监听器
     * @param
     * @return void
     */
    @Override
    public void removeAllListeners() {
        this.imListeners.clear();
    }
}
