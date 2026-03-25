package com.ouyunc.core.listener;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * 创建 {@link SimpleMessageEventMulticaster} 并注入异步执行器（如 {@code thread-pool.event-listener}）。
 */
public final class MessageEventMulticasterFactory {

    private MessageEventMulticasterFactory() {
    }

    public static MessageEventMulticaster create(Executor eventListenerExecutor) {
        SimpleMessageEventMulticaster m = new SimpleMessageEventMulticaster();
        m.setTaskExecutor(Objects.requireNonNull(eventListenerExecutor, "eventListenerExecutor"));
        return m;
    }
}
