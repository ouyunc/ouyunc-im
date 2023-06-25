package com.ouyunc.im.packet.message;

import io.protostuff.Tag;

import java.io.Serializable;

/**
 * @Author fangzhenxun
 * @Description: 自定义Immessage
 **/
public class Message implements Serializable {
    private static final long serialVersionUID = 102;

    /**
     * 发送者
     */
    @Tag(1)
    private String from;


    /**
     * 接收者
     */
    @Tag(2)
    private String to;



    /**
     * 内容类型，如果登录的内容类型，聊天的消息内容类型（文本，语音，图片，视频。。），webrtc 的信令内容类型
     */
    @Tag(3)
    private int contentType;

    
    /**
     * 具体内容json str
     */
    @Tag(4)
    private String content;


    /**
     * 扩展字段
     */
    @Tag(5)
    private String extra;


    /**
     * 创建时间戳（毫秒）
     */
    @Tag(6)
    private long  createTime;


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


    public int getContentType() {
        return contentType;
    }

    public void setContentType(int contentType) {
        this.contentType = contentType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }


    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }

    public Message() {
    }

    public Message(String from, String to, int contentType, String content, String extra, long createTime) {
        this.from = from;
        this.to = to;
        this.contentType = contentType;
        this.content = content;
        this.extra = extra;
        this.createTime = createTime;
    }

    public Message(String from, String to, int contentType, String content, long createTime) {
        this.from = from;
        this.to = to;
        this.contentType = contentType;
        this.content = content;
        this.createTime = createTime;
    }

    public Message(String from, String to, int contentType, long createTime) {
        this.from = from;
        this.to = to;
        this.contentType = contentType;
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "Message{" +
                "from='" + from + '\'' +
                ", to='" + to + '\'' +
                ", contentType=" + contentType +
                ", content='" + content + '\'' +
                ", extra='" + extra + '\'' +
                ", createTime=" + createTime +
                '}';
    }
}
