package com.ouyunc.im.event;

import com.ouyunc.im.utils.SystemClock;

import java.time.Clock;
import java.util.EventObject;
/**
 * @Author fangzhenxun
 * im 事件抽象类
 * 注意： 这里只是简单实现，事件机制，IMEvent 及其子类事件是不同的事件
 */
public abstract class IMEvent extends EventObject {
    private static final long serialVersionUID = 1608291851906904065L;

    /**
     * 发布时间
     */
    private final long timestamp;

    public IMEvent(Object source) {
        super(source);
        this.timestamp = SystemClock.now();
    }

    public IMEvent(Object source, Clock clock) {
        super(source);
        this.timestamp = clock.millis();
    }

    public final long getTimestamp() {
        return this.timestamp;
    }
}