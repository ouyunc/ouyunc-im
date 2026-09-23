package com.ouyunc.base.packet.message.content;

import java.io.Serializable;

/**
 * 客户端原消息的受理结果。messageId 是客户端稳定幂等键，packetId 使用字符串避免雪花 ID 精度丢失。
 * ACCEPTED 仅表示服务端已提交受理，不表示接收方已送达或已读。
 */
public class MessageSendResultContent implements Serializable {
    private static final long serialVersionUID = 1L;

    private String messageId;
    private String packetId;
    private String status;
    private Integer code;
    private String description;
    private Long retryAfterMs;

    public MessageSendResultContent() { }

    public MessageSendResultContent(String messageId, String packetId, String status,
                                    Integer code, String description, Long retryAfterMs) {
        this.messageId = messageId;
        this.packetId = packetId;
        this.status = status;
        this.code = code;
        this.description = description;
        this.retryAfterMs = retryAfterMs;
    }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getPacketId() { return packetId; }
    public void setPacketId(String packetId) { this.packetId = packetId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getCode() { return code; }
    public void setCode(Integer code) { this.code = code; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getRetryAfterMs() { return retryAfterMs; }
    public void setRetryAfterMs(Long retryAfterMs) { this.retryAfterMs = retryAfterMs; }
}
