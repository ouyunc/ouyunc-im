package com.ouyunc.base.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * POST /api/im/message/push 响应体 data 部分。
 */
public class MessagePushResponse implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String messageId;
    private String packetId;
    private String status;
    private Boolean recipientOnline;

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getPacketId() {
        return packetId;
    }

    public void setPacketId(String packetId) {
        this.packetId = packetId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getRecipientOnline() {
        return recipientOnline;
    }

    public void setRecipientOnline(Boolean recipientOnline) {
        this.recipientOnline = recipientOnline;
    }
}
