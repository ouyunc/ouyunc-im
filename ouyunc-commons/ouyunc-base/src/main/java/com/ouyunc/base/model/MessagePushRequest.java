package com.ouyunc.base.model;



import java.io.Serial;

/**
 * POST /api/im/push 请求体（JSON）。
 * <p>
 * 发送方 {@code from}、{@code fromType} 由 JWT 解析，不在请求体传递。
 */
public class MessagePushRequest implements java.io.Serializable{
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 推送类型、
     */
    private Integer pushType;

    /**
     * 推送渠道
     */
    private Integer pushChannel;

    /**
     * 业务消息 ID（必填），作为 {@code Message.id}。
     */
    private String messageId;

    /**
     * 发送方业务标识；HTTP 推送由 JWT 解析，不在请求体传递。
     */
    private String from;


    /**
     * 发送方标识类型；HTTP 推送由 JWT {@code fromType} claim 解析，不在请求体传递。
     * 与用户表 {@code type} 无关。
     */
    private Integer fromType;


    /**
     * 接收方业务标识
     */
    private String to;


    /**
     * 接收方标识类型，见 { com.ouyunc.base.constant.enums.MessageFromToTypeEnum}
     */
    private Integer toType;


    /**
     * 内容
     */
    private String content;

    /**
     * 扩展字段（可选），见 {@link MessagePushExtra}。
     */
    private MessagePushExtra extra;

    private Long createTime;

    public Integer getPushType() {
        return pushType;
    }

    public void setPushType(Integer pushType) {
        this.pushType = pushType;
    }

    public Integer getPushChannel() {
        return pushChannel;
    }

    public void setPushChannel(Integer pushChannel) {
        this.pushChannel = pushChannel;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public Integer getFromType() {
        return fromType;
    }

    public void setFromType(Integer fromType) {
        this.fromType = fromType;
    }

    public Integer getToType() {
        return toType;
    }

    public void setToType(Integer toType) {
        this.toType = toType;
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public MessagePushExtra getExtra() {
        return extra;
    }

    public void setExtra(MessagePushExtra extra) {
        this.extra = extra;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }
}
