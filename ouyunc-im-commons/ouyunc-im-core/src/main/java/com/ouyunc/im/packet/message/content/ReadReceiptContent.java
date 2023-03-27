package com.ouyunc.im.packet.message.content;

import java.io.Serializable;

/**
 * 已读回执内容
 */
public class ReadReceiptContent implements Serializable {
    private static final long serialVersionUID = 100007L;

    /**
     * 消息发送者
     */
    private String identity;

    /**
     * 消息回执id
     */
    private long packetId;

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    public long getPacketId() {
        return packetId;
    }

    public void setPacketId(long packetId) {
        this.packetId = packetId;
    }
}
