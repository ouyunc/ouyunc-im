package com.ouyu.im.entity;

import com.ouyu.im.packet.Packet;
import com.ouyu.im.packet.message.Message;

import java.io.Serializable;

/**
 * @Author fangzhenxun
 * @Description: 历史发送的协议包
 * @Version V1.0
 **/
public class HistoryPacket implements Serializable {
    private static final long serialVersionUID = 2132448426591725589L;

    /**
     * 用户是否已读/未读
     */
    private Boolean isRead;

    /**
     * 消息包
     */
    private Packet<Message> packet;


    public Boolean getRead() {
        return isRead;
    }

    public void setRead(Boolean read) {
        isRead = read;
    }

    public Packet<Message> getPacket() {
        return packet;
    }

    public void setPacket(Packet<Message> packet) {
        this.packet = packet;
    }

    public HistoryPacket() {
    }

    public HistoryPacket(Boolean isRead, Packet<Message> packet) {
        this.isRead = isRead;
        this.packet = packet;
    }
}
