package com.ouyunc.base.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * 外部渠道下行任务（Kafka {@code ouyunc_external_channel_outbound}），由独立适配服务消费。
 */
public class ExternalChannelOutboundPayload implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String appKey;
    private String messageId;
    private Long packetId;
    private Byte messageType;
    private String from;
    private String to;
    private Integer deliveryChannel;
    private Integer contentType;
    private String content;
    private String extra;
    /** 客服 ticketId，与 {@code Message.correlationId} 一致。 */
    private String ticketId;
    private Long createTime;

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public Long getPacketId() {
        return packetId;
    }

    public void setPacketId(Long packetId) {
        this.packetId = packetId;
    }

    public Byte getMessageType() {
        return messageType;
    }

    public void setMessageType(Byte messageType) {
        this.messageType = messageType;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public Integer getDeliveryChannel() {
        return deliveryChannel;
    }

    public void setDeliveryChannel(Integer deliveryChannel) {
        this.deliveryChannel = deliveryChannel;
    }

    public Integer getContentType() {
        return contentType;
    }

    public void setContentType(Integer contentType) {
        this.contentType = contentType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }
}
