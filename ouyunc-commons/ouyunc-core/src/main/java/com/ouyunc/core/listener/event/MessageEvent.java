package com.ouyunc.core.listener.event;

import com.ouyunc.base.constant.enums.EventType;
import com.ouyunc.base.utils.SnowflakeUtil;

import java.util.Objects;

/**
 * 统一消息事件：通过 {@link #getType()} 区分语义，业务数据放在 {@link GenericEvent#getSource()}。
 */
public final class MessageEvent extends GenericEvent<Object> {

    private final String id;

    /**
     * 事件类型，必传
     */
    private final EventType type;



    public MessageEvent(Object source, EventType type) {
        super(source);
        this.id = SnowflakeUtil.nextIdStr();
        this.type = Objects.requireNonNull(type, "type");
    }

    public MessageEvent(Object source, EventType type, long publishTime) {
        super(source, publishTime);
        this.id = SnowflakeUtil.nextIdStr();
        this.type = Objects.requireNonNull(type, "type");
    }


    public MessageEvent(String id, Object source, EventType type) {
        super(source);
        this.id = id;
        this.type = Objects.requireNonNull(type, "type");
    }

    public MessageEvent(String id, Object source, EventType type, long publishTime) {
        super(source, publishTime);
        this.id = id;
        this.type = Objects.requireNonNull(type, "type");
    }


    public String getId() {
        return id;
    }

    public EventType getType() {
        return type;
    }

    public long getPublishTime() {
        return getTimestamp();
    }

    @Override
    public void setSource(Object source) {
        throw new UnsupportedOperationException("MessageEvent is immutable");
    }

    @Override
    public void setTimestamp(long timestamp) {
        throw new UnsupportedOperationException("MessageEvent is immutable");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("MessageEvent is immutable");
    }
}
