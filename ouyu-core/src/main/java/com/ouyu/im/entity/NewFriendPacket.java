package com.ouyu.im.entity;

import com.ouyu.im.packet.Packet;
import com.ouyu.im.packet.message.Message;

import java.io.Serializable;

/**
 * @Author fangzhenxun
 * @Description: 新添加的朋友协议包
 * @Version V1.0
 **/
public class NewFriendPacket implements Serializable {
    private static final long serialVersionUID = -5643189311527853566L;


    /**
     * 申请好友的状态，0-待处理（接收方）,1-等待验证（发送方），2-已添加（发送方），3-已同意（接收方），4-已拒绝（接收方），5-未通过（发送方）
     **/
    private Integer status;

    /**
     * 消息包
     */
    private Packet<Message> packet;


    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Packet<Message> getPacket() {
        return packet;
    }

    public void setPacket(Packet<Message> packet) {
        this.packet = packet;
    }

    public NewFriendPacket() {
    }

    public NewFriendPacket(Integer status, Packet<Message> packet) {
        this.status = status;
        this.packet = packet;
    }
}
