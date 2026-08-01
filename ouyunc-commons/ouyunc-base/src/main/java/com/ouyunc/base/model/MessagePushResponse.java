package com.ouyunc.base.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * POST /api/im/message/push 响应体 data 部分。
 * <p>{@code ACCEPTED}/{@code DUPLICATE} 均表示调用成功（已受理）；{@code PROCESSING} 表示同 messageId 尚未成功，可重试。
 * 均不等于已投递。</p>
 * <p>{@code errorMessage} 仅在 {@code PROCESSING} 时有文案；成功态为 {@code null}。</p>
 */
public class MessagePushResponse implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String messageId;
    private String packetId;
    private String status;
    /** 仅 PROCESSING 使用；ACCEPTED/DUPLICATE 为 null。 */
    private String errorMessage;

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

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
