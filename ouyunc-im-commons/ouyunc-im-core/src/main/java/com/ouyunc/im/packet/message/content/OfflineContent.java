package com.ouyunc.im.packet.message.content;

import java.io.Serializable;
import java.util.List;

/**
 * 拉取离线信息消息内容
 */
public class OfflineContent<T> implements Serializable {
    private static final long serialVersionUID = 100003L;


    /**
     * 拉取的 packetId, 对于拉取请求是从这里开始，对于返回是拉取到这里，下个拉取开始的地方
     */
    private Long pullPacketId;


    /**
     * 本次拉取消息数量
     */
    private Long pullSize;



    /**
     * 请求/响应的集合,如果是请求可以为空集合，存放消息id，如果是响应，存放离线的packet列表
     */
    private List<T> packetList;

    public Long getPullPacketId() {
        return pullPacketId;
    }

    public void setPullPacketId(Long pullPacketId) {
        this.pullPacketId = pullPacketId;
    }

    public Long getPullSize() {
        return pullSize;
    }

    public void setPullSize(Long pullSize) {
        this.pullSize = pullSize;
    }

    public List<T> getPacketList() {
        return packetList;
    }

    public void setPacketList(List<T> packetList) {
        this.packetList = packetList;
    }
}
