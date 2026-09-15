package com.ouyunc.base.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * POST /api/im/message/push 响应体 data 部分。
 * <p>{@code ACCEPTED}：已写入 PENDING（后台确认落库）；{@code DUPLICATE}：已 COMMITTED；
 * {@code PROCESSING}：同 messageId 仍在途；{@code RETRYABLE_FAILED}：后台失败可重试。
 * 均不等于“已投递到客户端”。</p>
 * <p>{@code errorMessage} 仅 PROCESSING / RETRYABLE_FAILED 使用；成功态为 {@code null}。</p>
 */
public class MessagePushResponse implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String messageId;
    private String packetId;
    private String status;
    /** PROCESSING / RETRYABLE_FAILED 使用；ACCEPTED/DUPLICATE 为 null。 */
    private String errorMessage;
    /** toList 扇出时每接收人结果；单推为 null。 */
    private List<MessagePushResponse> items;

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

    public List<MessagePushResponse> getItems() {
        return items;
    }

    public void setItems(List<MessagePushResponse> items) {
        this.items = items;
    }
}
