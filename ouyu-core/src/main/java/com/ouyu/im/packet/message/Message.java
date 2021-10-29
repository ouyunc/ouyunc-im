package com.ouyu.im.packet.message;

import io.protostuff.Tag;

import java.io.Serializable;

/**
 * @Author fangzhenxun
 * @Description: 自定义Immessage
 * @Version V1.0
 **/
public class Message extends BaseMessage implements Serializable {
    private static final long serialVersionUID = -8247754944972623888L;

    /**
     * 发送者
     */
    @Tag(1)
    private String from;

    /**
     * 发送者所在服务器地址：ip:port
     */
    @Tag(2)
    private String fromServerAddress;

    /**
     * 接收者
     */
    @Tag(3)
    private String to;

    /**
     * 接收者所在服务器地址：ip:port
     */
    @Tag(4)
    private String toServerAddress;


    /**
     * 内容类型，如果登录的内容类型，聊天的消息内容类型（文本，语音，图片，视频。。），webrtc 的信令内容类型
     */
    @Tag(5)
    private int contentType;

    
    /**
     * 具体内容json str
     */
    @Tag(6)
    private String content;


    /**
     * 扩展字段
     */
    @Tag(7)
    private String extra;


    /**
     * 创建时间戳（毫秒）
     */
    @Tag(8)
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

    public String getFromServerAddress() {
        return fromServerAddress;
    }

    public void setFromServerAddress(String fromServerAddress) {
        this.fromServerAddress = fromServerAddress;
    }

    public String getToServerAddress() {
        return toServerAddress;
    }

    public void setToServerAddress(String toServerAddress) {
        this.toServerAddress = toServerAddress;
    }

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }

    public Message() {
    }



    public Message(String from, String to, int contentType, String content, long createTime) {
        this.from = from;
        this.to = to;
        this.contentType = contentType;
        this.content = content;
        this.createTime = createTime;
    }
}
