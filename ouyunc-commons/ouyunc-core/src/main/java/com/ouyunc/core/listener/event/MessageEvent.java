package com.ouyunc.core.listener.event;

import com.ouyunc.base.constant.enums.EventType;
import com.ouyunc.base.utils.SnowflakeUtil;

import java.util.Objects;

/**
 * 统一消息事件：通过 {@link #getType()} 区分语义，业务数据放在 {@link GenericEvent#getSource()}。
 */
public final class MessageEvent extends GenericEvent<Object> {

    private String id;

    /**
     * 事件类型，必传
     */
    private EventType type;



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

    public void setId(String id) {
        this.id = id;
    }

    public EventType getType() {
        return type;
    }

    public void setType(EventType type) {
        this.type = type;
    }

    public long getPublishTime() {
        return getTimestamp();
    }

    public void clear() {
        super.clear();
        this.id = null;
        this.type = null;
    }
}
