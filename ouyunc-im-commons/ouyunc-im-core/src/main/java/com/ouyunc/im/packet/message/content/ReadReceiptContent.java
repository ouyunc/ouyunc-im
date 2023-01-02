package com.ouyunc.im.packet.message.content;

import java.io.Serializable;
import java.util.Set;

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
    private Long packetId;

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    public Long getPacketId() {
        return packetId;
    }

    public void setPacketId(Long packetId) {
        this.packetId = packetId;
    }
}
