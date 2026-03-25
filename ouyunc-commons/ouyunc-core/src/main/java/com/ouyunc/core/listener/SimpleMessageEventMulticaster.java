package com.ouyunc.core.listener;

import com.ouyunc.core.listener.event.MessageEvent;

import java.util.Collection;
import java.util.concurrent.Executor;

/**
 * 简单多播：同步路径与 {@link AbstractMessageEventMulticaster#dispatchToListeners(MessageEvent)} 一致；
 * 异步路径为每个监听器向 {@link #setTaskExecutor(Executor)} 提交一个任务（监听器之间可并行），需注入执行器。
 *
 * @author fzx
 */
public class SimpleMessageEventMulticaster extends AbstractMessageEventMulticaster {

    private Executor taskExecutor;

    protected Executor getTaskExecutor() {
        return taskExecutor;
    }

    public void setTaskExecutor(Executor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    @Override
    public void multicastEvent(MessageEvent event, boolean async) {
        if (event == null) {
            return;
        }
        if (!async) {
            dispatchToListeners(event);
            return;
        }
        Collection<MessageListener<MessageEvent>> listeners = getMessageListeners(event);
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        Executor executor = getTaskExecutor();
        if (executor == null) {
            dispatchToListeners(event);
            return;
        }
        for (MessageListener<MessageEvent> listener : listeners) {
            executor.execute(() -> invokeListener(listener, event));
        }
    }
}
