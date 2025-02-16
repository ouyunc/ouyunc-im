package com.ouyunc.base.packet.message.content;

import java.io.Serial;
import java.io.Serializable;

/**
 * 已读回执内容
 */
public class ReadReceiptContent implements Serializable {
    @Serial
    private static final long serialVersionUID = 100007L;

    /**
     * 消息发送者
     */
    private String identity;

    /**
     * 消息回执id
     */
    private String packetId;

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    public String getPacketId() {
        return packetId;
    }

    public void setPacketId(String packetId) {
        this.packetId = packetId;
    }
}
