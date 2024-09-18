package com.ouyunc.core.listener.event;


import java.util.EventObject;

/**
 * @Author fzx
 * message 事件抽象类
 * 注意： 这里只是简单实现，事件机制，MessageEvent 及其子类事件是不同的事件
 */
public abstract class MessageEvent extends EventObject {

    /**
     * 发布时间
     */
    private final long publishTime;

    public MessageEvent(Object source) {
        super(source);
        // 这里时间使用
        this.publishTime = System.currentTimeMillis();
    }

    public MessageEvent(Object source, long publishTime) {
        super(source);
        this.publishTime = publishTime;
    }

    public final long getPublishTime() {
        return this.publishTime;
    }
}