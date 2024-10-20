package com.ouyunc.base.packet.message;

import com.ouyunc.base.model.Metadata;
import io.protostuff.Tag;

import java.io.Serial;
import java.io.Serializable;

/**
 * @author fzx
 * @description 消息
 */
public class Message implements Serializable, Cloneable {
    @Serial
    private static final long serialVersionUID = 1L;


    /**
     * 发送者，唯一标识
     */
    @Tag(1)
    private String from;


    /**
     * 接收者，唯一标识
     */
    @Tag(2)
    private String to;



    /**
     * 内容类型，如果登录的内容类型，聊天的消息内容类型（文本，语音，图片，视频...），webrtc 的信令内容类型
     */
    @Tag(3)
    private int contentType;


    /**
     * 具体内容json str
     */
    @Tag(4)
    private String content;


    /**
     * 客户端扩展/附加字段，客户端自定义数据字段，如：消息发送时间、消息发送者昵称、消息发送者头像、消息发送者设备类型、消息发送者网络类型、消息发送者客户端版本、消息发送者客户端语言、消息发送者客户端平台、消息发送者客户端IP、消息发送者客户端MAC地址、消息发送者客户端操作系统、消息发送者客户端浏览器、消息发送者客户端浏览器版本、消息发送者客户端浏览器语言、消息发送
     */
    @Tag(5)
    private String extra;

    /**
     * 消息可靠性标识 qos = 0/1/2
     * QoS 0：至多一次，at most once；发送方发送一条消息，接收方最多能接收到一次。即发送方完成消息发送之后不关心消息发送是否成功。
     * QoS 1：至少一次，at least once；发送方发送一条消息，接收方至少能接收到一次。即发送方完成消息发送之后，若发送失败，则继续重发直到接受方接收到消息为止。这种模式下可能会导致接收方收到重复的消息。
     * QoS 2：确保一次：exactly once；发送方发送一条消息，接收方一定且只能收到一次。即发送方完成消息发送之后，若发送失败，则继续重发直到接收方接收到消息为止，在这一过程中同时保证接收方不会因为消息重传而收到重复的消息。
     */
    @Tag(6)
    private int qos;

    /**
     * 客户端消息创建时间戳（毫秒）
     */
    @Tag(7)
    private long createTime;


    /**
     * 元数据，对内访问
     */
    @Tag(8)
    private Metadata metadata;



    public Message() {
    }

    public Message(String from, String to, int contentType, long createTime) {
        this.from = from;
        this.to = to;
        this.contentType = contentType;
        this.createTime = createTime;
    }

    public Message(String from, String to, int contentType, String content, long createTime) {
        this.from = from;
        this.to = to;
        this.contentType = contentType;
        this.content = content;
        this.createTime = createTime;
    }
    public Message(String from, String to, int contentType, String content, long createTime, Metadata metadata) {
        this.from = from;
        this.to = to;
        this.contentType = contentType;
        this.content = content;
        this.createTime = createTime;
        this.metadata = metadata;
    }
    public Message(String from, String to, int contentType, String content, String extraData, int qos, long createTime) {
        this.from = from;
        this.to = to;
        this.contentType = contentType;
        this.content = content;
        this.extra = extraData;
        this.qos = qos;
        this.createTime = createTime;
    }

    public Message(String from, String to, int contentType, String content, int qos, long createTime, Metadata metadata) {
        this.from = from;
        this.to = to;
        this.contentType = contentType;
        this.content = content;
        this.qos = qos;
        this.createTime = createTime;
        this.metadata = metadata;
    }

    public Message(String from, String to, int contentType, String content, String extraData, Metadata metadata, int qos, long createTime) {
        this.from = from;
        this.to = to;
        this.contentType = contentType;
        this.content = content;
        this.extra = extraData;
        this.metadata = metadata;
        this.qos = qos;
        this.createTime = createTime;
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

    public Metadata getMetadata() {
        return metadata == null ? new Metadata() : metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
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

    public int getQos() {
        return qos;
    }

    public void setQos(int qos) {
        this.qos = qos;
    }

    @Override
    public Message clone() {
        try {
            Message message = (Message) super.clone();
            if (this.metadata != null) {
                message.setMetadata(this.metadata.clone());
            }
            return message;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
