package com.ouyunc.message.processor.http;

/**
 * POST /api/im/push 请求体（JSON）。appKey 可与 Header {@link com.ouyunc.base.constant.HttpRequestConstant#HTTP_HEADER_APP_KEY} 二选一。
 */
public class MessagePushRequest {

    private String appKey;

    /**
     * 发送方业务标识，缺省为 system
     */
    private String from;

    private String to;

    /**
     * 内容类型，参见 {@link com.ouyunc.base.constant.enums.MessageContentTypeEnum}
     */
    private Integer contentType;

    private String content;

    private Integer qos;

    private Long createTime;

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
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

    public Integer getQos() {
        return qos;
    }

    public void setQos(Integer qos) {
        this.qos = qos;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }
}
