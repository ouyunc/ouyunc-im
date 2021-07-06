package com.ouyu.im.packet.message;

import io.protostuff.Tag;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * @Author fangzhenxun
 * @Description: ack 应答消息，针对外部端与服务器端的消息回执应答
 * @Version V1.0
 **/
public class AcknowledgeMessage extends Message implements Serializable {

    /**
     * 发送方
     **/
    @Tag(1)
    private String from;

    /**
     * 接受方
     **/
    @Tag(2)
    private String to;

    /**
     * 返回体信息
     **/
    @Tag(3)
    private String content;

    /**
     * 时间戳,毫秒
     **/
    @Tag(4)
    private long createTime;

    public String getData() {
        return content;
    }

    public void setData(String content) {
        this.content = content;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
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

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public AcknowledgeMessage() {
        this.createTime = LocalDateTime.now().toInstant(ZoneOffset.of("+8")).toEpochMilli();
    }

    public AcknowledgeMessage(String from, String to, String content) {
        this.from = from;
        this.to = to;
        this.content = content;
        this.createTime = LocalDateTime.now().toInstant(ZoneOffset.of("+8")).toEpochMilli();
    }
}
