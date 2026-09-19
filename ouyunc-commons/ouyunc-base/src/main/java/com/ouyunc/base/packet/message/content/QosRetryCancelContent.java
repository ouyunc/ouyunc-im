package com.ouyunc.base.packet.message.content;

import java.io.Serial;
import java.io.Serializable;

/**
 * 集群内取消始发节点 SERVER QoS 下行重试。
 * identity / deviceType 必须来自落地节点已认证 Channel，不能信客户端 ACK 正文。
 */
public class QosRetryCancelContent implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String appKey;
    private long packetId;
    private String identity;
    private byte deviceType;

    public QosRetryCancelContent() {
    }

    public QosRetryCancelContent(String appKey, long packetId, String identity, byte deviceType) {
        this.appKey = appKey;
        this.packetId = packetId;
        this.identity = identity;
        this.deviceType = deviceType;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public long getPacketId() {
        return packetId;
    }

    public void setPacketId(long packetId) {
        this.packetId = packetId;
    }

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    public byte getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(byte deviceType) {
        this.deviceType = deviceType;
    }
}
