package com.ouyunc.im.packet.message.content;

import com.alibaba.fastjson2.annotation.JSONField;
import com.ouyunc.im.serialize.Long2StringSerializer;

import java.io.Serializable;

/**
 * 客户端 回信 ack消息内容
 */
public class ClientReplyAckContent implements Serializable {
    private static final long serialVersionUID = 100004L;

    /**
     * 原消息包packet id
     */
    @JSONField(serializeUsing = Long2StringSerializer.class)
    private long packetId;

    /**
     * 原发消息方登录设备类型
     */
    private byte deviceType;

    public long getPacketId() {
        return packetId;
    }

    public void setPacketId(long packetId) {
        this.packetId = packetId;
    }

    public byte getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(byte deviceType) {
        this.deviceType = deviceType;
    }
}
