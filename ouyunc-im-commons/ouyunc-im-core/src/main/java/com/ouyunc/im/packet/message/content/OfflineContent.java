package com.ouyunc.im.packet.message.content;

import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.Message;

import java.io.Serializable;
import java.util.List;

/**
 * 拉取离线信息消息内容
 */
public class OfflineContent implements Serializable {
    private static final long serialVersionUID = 100003L;


    /**
     * 联系人/群组唯一标识 (可选，不选则拉取全量数据)
     */
    private String identity;


    /**
     * 类型 identityType， 1-个人，2-群组 （可选，不选则拉取全量数据）
     */
    private Integer identityType;

    /**
     * 本次拉取消息数量
     */
    private Long pullSize;


    /**
     * 请求/响应的集合,如果是请求可以为空集合,存放离线的packet列表
     */
    private List<Packet<Message>> packetList;


    public Long getPullSize() {
        return pullSize;
    }

    public void setPullSize(Long pullSize) {
        this.pullSize = pullSize;
    }

    public List<Packet<Message>> getPacketList() {
        return packetList;
    }

    public void setPacketList(List<Packet<Message>> packetList) {

        this.packetList = packetList;
    }

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
}
