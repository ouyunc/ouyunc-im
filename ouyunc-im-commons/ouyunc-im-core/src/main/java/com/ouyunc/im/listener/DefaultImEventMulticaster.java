package com.ouyunc.im.listener;

import com.ouyunc.im.event.IMEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;

/**
 * @Author fangzhenxun
 * @Description: 简单im事件多播器的实现
 **/
public class DefaultImEventMulticaster extends AbstractImEventMulticaster{
    private static Logger log = LoggerFactory.getLogger(DefaultImEventMulticaster.class);

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
     * @Author fangzhenxun
     * @Description 设置任务执行器
     * @param taskExecutor
     * @return void
     */
    public void setTaskExecutor(Executor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }




    /**
     * @Author fangzhenxun
     * @Description 多播时间
     * @param event
     * @return void
     */
    @Override
    public void multicastEvent(IMEvent event) {
        Executor executor = getTaskExecutor();
        // 遍历所有的事件监听器
        for (IMListener<?> listener : getImListeners(event)) {
            if (executor != null) {
                executor.execute(() -> invokeListener(listener, event));
            } else {
                invokeListener(listener, event);
            }
        }
    }




    /**
     * 对给定的事件执行监听器
     */
    protected void invokeListener(IMListener listener, IMEvent event) {
        try {
            listener.onApplicationEvent(event);
        } catch (Throwable err) {
            // 这里不进行抛出异常，只记录
            log.error("im监听器 {} 执行事件 {} 失败：{}", listener, event, err.getMessage());
        }
    }

}
