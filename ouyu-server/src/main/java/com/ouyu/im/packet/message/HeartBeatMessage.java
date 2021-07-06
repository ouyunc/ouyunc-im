package com.ouyu.im.packet.message;

import io.protostuff.Tag;

import java.io.Serializable;

/**
 * @Author fangzhenxun
 * @Description: 心跳消息类型
 * @Version V1.0
 **/
public class HeartBeatMessage extends Message implements Serializable {
    /**
     * 发送方唯一标识，一般是ip
     */
    @Tag(1)
    private String from;

    /**
     * 接收方唯一标识，一般是ip
     */
    @Tag(2)
    private String to;


    /**
     * 消息内容,syn-ack/ping-pong
     */
    @Tag(3)
    private String content;


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

    public HeartBeatMessage() {
    }

    public HeartBeatMessage(String from, String to, String content) {
        this.from = from;
        this.to = to;
        this.content = content;
    }
}
