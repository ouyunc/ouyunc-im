package com.ouyunc.base.packet;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.packet.message.Message;
import io.protostuff.Tag;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * @author fzx
 * @description 协议包
 */
public class Packet implements Serializable, Cloneable{
    @Serial
    private static final long serialVersionUID = 1L;


    /**
     * 1个字节, 魔数 十进制数字 102
     */
    @Tag(1)
    private byte magic = MessageConstant.PACKET_MAGIC;

    /**
     * 1个字节  协议类型,ws,http,自定义等
     */
    @Tag(2)
    private byte protocol;

    /**
     * 1个字节  协议版本，1,2,3
     */
    @Tag(3)
    private byte protocolVersion;

    /**
     * 协议包id 8个字节 使用雪花id
     */
    @Tag(4)
    @JSONField(serializeUsing = ToStringSerializer.class)
    private long packetId;

    /**
     * 发送端设备类型 1个字节，m-android/m-ios/pc-windows/pc-mac/pad...
     */
    @Tag(5)
    private byte deviceType;

    /**
     * 发送端设网络类型，1个字节：0-其他，1-有线， 2-wifi，3-5g, 4-4g, 5-3g, 6-2g
     */
    @Tag(6)
    private byte networkType;

    /**
     * 消息加密，1个字节，加密方式，不加密/AES/...对称加密，防止消息泄密, 默认没有加密
     */
    @Tag(7)
    private byte encryptType;

    /**
     * 序列化算法 1 个字节，json/jdk/hessian/kryo/protoStuff(protoBUf),默认是protobuf
     */
    @Tag(8)
    private byte serializeAlgorithm;

    /**
     * 消息类型 1 个字节，如 RPC 框架中有请求、响应、心跳类型。IM 通讯场景中有登陆、创建群聊、发送消息、接收消息、退出群聊等类型。
     */
    @Tag(9)
    private byte messageType;

    /**
     * 加密后的消息长度.4个字节
     */
    @Tag(10)
    private int messageLength;

    /**
     * 消息内容，n个字节, 不同的消息类型有可能是不同的数据内容
     */
    @Tag(11)
    private Message message;


    public byte getMagic() {
        return magic;
    }

    public void setMagic(byte magic) {
        this.magic = magic;
    }

    public byte getProtocol() {
        return protocol;
    }

    public void setProtocol(byte protocol) {
        this.protocol = protocol;
    }

    public byte getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(byte protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

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

    public byte getNetworkType() {
        return networkType;
    }

    public void setNetworkType(byte networkType) {
        this.networkType = networkType;
    }

    public byte getEncryptType() {
        return encryptType;
    }

    public void setEncryptType(byte encryptType) {
        this.encryptType = encryptType;
    }

    public byte getSerializeAlgorithm() {
        return serializeAlgorithm;
    }

    public void setSerializeAlgorithm(byte serializeAlgorithm) {
        this.serializeAlgorithm = serializeAlgorithm;
    }

    public byte getMessageType() {
        return messageType;
    }

    public void setMessageType(byte messageType) {
        this.messageType = messageType;
    }

    public int getMessageLength() {
        return messageLength;
    }

    public void setMessageLength(int messageLength) {
        this.messageLength = messageLength;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public Packet() {
    }

    public Packet(byte protocol, byte protocolVersion, long packetId, byte deviceType, byte networkType, byte encryptType, byte serializeAlgorithm, byte messageType, Message message) {
        this.protocol = protocol;
        this.protocolVersion = protocolVersion;
        this.packetId = packetId;
        this.deviceType = deviceType;
        this.networkType = networkType;
        this.encryptType = encryptType;
        this.serializeAlgorithm = serializeAlgorithm;
        this.messageType = messageType;
        this.message = message;
    }

    public Packet(byte protocol, byte protocolVersion, long packetId, byte deviceType, byte networkType, byte encryptType, byte serializeAlgorithm, byte messageType, int messageLength, Message message) {
        this.protocol = protocol;
        this.protocolVersion = protocolVersion;
        this.packetId = packetId;
        this.deviceType = deviceType;
        this.networkType = networkType;
        this.encryptType = encryptType;
        this.serializeAlgorithm = serializeAlgorithm;
        this.messageType = messageType;
        this.messageLength = messageLength;
        this.message = message;
    }

    public Packet(byte magic, byte protocol, byte protocolVersion, long packetId, byte deviceType, byte networkType, byte encryptType, byte serializeAlgorithm, byte messageType, int messageLength, Message message) {
        this.magic = magic;
        this.protocol = protocol;
        this.protocolVersion = protocolVersion;
        this.packetId = packetId;
        this.deviceType = deviceType;
        this.networkType = networkType;
        this.encryptType = encryptType;
        this.serializeAlgorithm = serializeAlgorithm;
        this.messageType = messageType;
        this.messageLength = messageLength;
        this.message = message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Packet packet)) return false;
        return packetId == packet.packetId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(packetId);
    }

    @Override
    public String toString() {
        return "Packet{" +
                "  magic=" + magic +
                ", protocol=" + protocol +
                ", protocolVersion=" + protocolVersion +
                ", packetId=" + packetId +
                ", deviceType=" + deviceType +
                ", networkType=" + networkType +
                ", encryptType=" + encryptType +
                ", serializeAlgorithm=" + serializeAlgorithm +
                ", messageType=" + messageType +
                ", messageLength=" + messageLength +
                ", message=" + message +
                '}';
    }

    @Override
    public Packet clone() {
        try {
            Packet packet = (Packet) super.clone();
            if (this.message != null) {
                packet.setMessage(this.message.clone());
            }
            return packet;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
