package com.ouyunc.im.packet.message.content;

import java.io.Serializable;
import java.util.Set;

/**
 * 已读回执内容
 */
public class ReadReceiptContent implements Serializable {
    private static final long serialVersionUID = 100007L;

    /**
     * 回执类型的消息：私聊/群聊
     */
    private byte messageType;

    /**
     * 批量回执的消息packet 的id集合
     */
    private Set<Long> packetIdList;

    public byte getMessageType() {
        return messageType;
    }

    public void setMessageType(byte messageType) {
        this.messageType = messageType;
    }

    public Set<Long> getPacketIdList() {
        return packetIdList;
    }

    public void setPacketIdList(Set<Long> packetIdList) {
        this.packetIdList = packetIdList;
    }
}
