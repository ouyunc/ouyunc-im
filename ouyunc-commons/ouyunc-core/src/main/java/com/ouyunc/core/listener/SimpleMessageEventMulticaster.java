package com.ouyunc.core.listener;

import com.ouyunc.core.listener.event.MessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;

/**
 * @Author fzx
 * @Description: 简单Message事件多播器的实现
 **/
public class SimpleMessageEventMulticaster extends AbstractMessageEventMulticaster{
    private final static Logger log = LoggerFactory.getLogger(SimpleMessageEventMulticaster.class);

    /**
     * 监听器事件执行器
     */
    private Executor taskExecutor;


    /**
     * Return the current task executor for this multicaster.
     */
    protected Executor getTaskExecutor() {
        return this.taskExecutor;
    }


    /**
     * @Author fzx
     * @Description 设置任务执行器
     * @param taskExecutor
     * @return void
     */
    public void setTaskExecutor(Executor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }





    /**
     * @Author fzx
     * @Description 多播事件
     * @param event
     * @param async 是否异步执行事件 true-异步， false-同步
     */
    @Override
    public void multicastEvent(MessageEvent event, boolean async) {
        Executor executor = getTaskExecutor();
        // 遍历所有的事件监听器
        for (MessageListener<MessageEvent> listener : getMessageListeners(event)) {
            if (async && executor != null) {
                executor.execute(() -> invokeListener(listener, event));
            } else {
                invokeListener(listener, event);
            }
        }
    }


    /**
     * 对给定的事件执行监听器
     */
    protected void invokeListener(MessageListener<MessageEvent> listener, MessageEvent event) {
        try {
            listener.onApplicationEvent(event);
        } catch (Throwable err) {
            // 这里不进行抛出异常，只记录
            log.error("message 监听器 {} 执行事件 {} 失败：{}", listener, event, err.getMessage());
        }
    }

}
