package com.ouyunc.im.base;

import com.ouyunc.im.packet.Packet;

import java.io.Serializable;
import java.util.Objects;

/**
 * 丢失的packet 信息
 */
public class MissingPacket implements Serializable {
    private static final long serialVersionUID = 501;

    /**
     * 丢失的包
     */
    private Packet packet;

    /**
     * 服务器地址：ip:port, 从哪个服务器上丢失的
     */
    private String serverAddress;

    /**
     * 消息丢失的时间,时间戳，毫秒
     */
    private long createTime;

    public MissingPacket() {
    }

    public MissingPacket(Packet packet, String serverAddress, long createTime) {
        this.packet = packet;
        this.serverAddress = serverAddress;
        this.createTime = createTime;
    }

    public Packet getPacket() {
        return packet;
    }

    public void setPacket(Packet packet) {
        this.packet = packet;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public void setServerAddress(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MissingPacket that = (MissingPacket) o;
        return Objects.equals(packet.getPacketId(), that.packet.getPacketId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(packet.getPacketId());
    }
}
