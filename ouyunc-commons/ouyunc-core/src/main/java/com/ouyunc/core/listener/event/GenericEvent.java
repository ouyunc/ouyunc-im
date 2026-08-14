package com.ouyunc.core.listener.event;

import com.ouyunc.base.utils.TimeUtil;

/**
 * 通用事件容器（线程安全）
 * @param <T> 承载的数据类型
 */
public class GenericEvent<T> {
    // 使用 @Contended 避免伪共享（需开启JVM参数 -XX:-RestrictContended）
    //@jdk.internal.vm.annotation.Contended
    private T source;
    private long timestamp;

    public GenericEvent(T data, long timestamp) {
        this.source = data;
        this.timestamp = timestamp;
    }

    public GenericEvent() {
        this.timestamp = TimeUtil.currentTimeMillis();
    }

    public GenericEvent(T data) {
        this.source = data;
        this.timestamp = TimeUtil.currentTimeMillis();
    }

    public void clear() {
        this.source = null;  // 帮助GC回收
        this.timestamp = 0;
    }

    //---------- Getter/Setter ----------
    public T getSource() {
        return source;
    }

    public void setSource(T source) {
        this.source = source;
        this.timestamp = TimeUtil.currentTimeMillis();
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getTimestamp() {
        return timestamp;
    }

}