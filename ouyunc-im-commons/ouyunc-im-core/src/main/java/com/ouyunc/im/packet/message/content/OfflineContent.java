package com.ouyunc.im.packet.message.content;

import com.ouyunc.im.packet.Packet;

import java.io.Serializable;
import java.util.List;

/**
 * 拉取离线信息消息内容
 */
public class OfflineContent implements Serializable {
    private static final long serialVersionUID = 100003L;


    /**
     * 本次拉取消息数量
     */
    private Long pullSize;


    /**
     * 请求/响应的集合,如果是请求可以为空集合，存放消息id，如果是响应，存放离线的packet列表
     */
    private List<Packet> packetList;


    public Long getPullSize() {
        return pullSize;
    }

    public void setPullSize(Long pullSize) {
        this.pullSize = pullSize;
    }

    public List<Packet> getPacketList() {
        return packetList;
    }

    public void setPacketList(List<Packet> packetList) {
        this.packetList = packetList;
    }
}
