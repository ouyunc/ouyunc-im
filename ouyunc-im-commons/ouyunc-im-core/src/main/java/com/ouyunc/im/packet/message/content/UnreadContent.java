package com.ouyunc.im.packet.message.content;

import com.ouyunc.im.packet.Packet;

import java.io.Serializable;
import java.util.List;

/**
 * （离线消息）未读消息
 */
public class UnreadContent implements Serializable {
    private static final long serialVersionUID = 100009L;

    /**
     * 联系人/群组唯一标识
     */
    private String identity;


    /**
     * 类型 identityType， 1-个人，2-群组
     */
    private Integer identityType;


    /**
     * 未读消息数量
     */
    private Integer count;


    /**
     * 最后几条未读消息，默认一条
     */
    private List<Packet> packetList;

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    public Integer getIdentityType() {
        return identityType;
    }

    public void setIdentityType(Integer identityType) {
        this.identityType = identityType;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public List<Packet> getPacketList() {
        return packetList;
    }

    public void setPacketList(List<Packet> packetList) {
        this.packetList = packetList;
    }

    public UnreadContent() {
    }

    public UnreadContent(String identity, Integer identityType, Integer count, List<Packet> packetList) {
        this.identity = identity;
        this.identityType = identityType;
        this.count = count;
        this.packetList = packetList;
    }
}
