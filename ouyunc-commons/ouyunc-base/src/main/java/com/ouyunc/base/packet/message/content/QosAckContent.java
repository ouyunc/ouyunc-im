package com.ouyunc.base.packet.message.content;

import java.io.Serializable;

/**
 * QoS ACK 正文，对外固定 JSON：{@code {"ackId":"<packetId>","messageId":"<客户端消息id>"}}。
 * <p>客户端以 {@code messageId} 为稳定业务键停重试/对账；{@code ackId} 为服务端内部 packetId，
 * 用于定位热数据与下行重试任务，客户端可不感知其生成规则。</p>
 */
public class QosAckContent implements Serializable {
    private static final long serialVersionUID = -1L;

    /**
     * 服务端原消息的正式 packetId（十进制字符串）
     */
    private String ackId;

    /**
     * 客户端消息 id（稳定键）
     */
    private String messageId;

    public QosAckContent() {
    }

    public QosAckContent(String ackId, String messageId) {
        this.ackId = ackId;
        this.messageId = messageId;
    }

    public String getAckId() {
        return ackId;
    }

    public void setAckId(String ackId) {
        this.ackId = ackId;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
}
