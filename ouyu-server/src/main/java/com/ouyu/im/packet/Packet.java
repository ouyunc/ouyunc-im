package com.ouyu.im.packet;

import com.ouyu.im.constant.ImConstant;
import io.protostuff.Tag;

import java.io.Serializable;

/**
 * @Author fangzhenxun
 * @Description: 协议包,在本服务中的传递的实体类
 * @Version V1.0
 **/
public class Packet<T> implements Serializable {

    /**
     * 1个字节,十进制数字 102
     */
    @Tag(1)
    private byte magic = ImConstant.PACKET_MAGIC;

    /**
     * 2个字节  协议类型,ws,http,自定义,默认为内部ayn_ack
     */
    @Tag(2)
    private short protocol;

    /**
     * 1个字节  协议版本，1,2,3
     */
    @Tag(3)
    private byte protocolVersion;


    /**
     * 协议包id 8个字节 使用雪花id
     */
    @Tag(4)
    private long packetId;

    /**
     * 发送端设备类型 1个字节，m-android/m-ios/pc-windows/pc-mac/pad...
     */
    @Tag(5)
    private byte deviceType;

    /**
     * 发送者ip 4个字节
     */
    @Tag(6)
    private String ip;

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
     * 消息长度.4个字节
     */
    @Tag(10)
    private int messageLength;

    /**
     * 消息内容，n个字节, 不同的消息类型有可能是不同的数据内容
     */
    @Tag(11)
    private T message;

    public short getProtocol() {
        return protocol;
    }

    public void setProtocol(short protocol) {
        this.protocol = protocol;
    }

    public byte getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(byte protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public byte getMagic() {
        return magic;
    }

    public void setMagic(byte magic) {
        this.magic = magic;
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

    public byte getMessageType() {
        return messageType;
    }

    public void setMessageType(byte messageType) {
        this.messageType = messageType;
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

    public int getMessageLength() {
        return messageLength;
    }

    public void setMessageLength(int messageLength) {
        this.messageLength = messageLength;
    }

    public T getMessage() {
        return message;
    }

    public void setMessage(T message) {
        this.message = message;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public Packet() {
    }

    public Packet(short protocol, byte protocolVersion, long packetId, byte deviceType, String ip, byte messageType, byte encryptType, byte serializeAlgorithm, T message) {
        this.protocol = protocol;
        this.protocolVersion = protocolVersion;
        this.packetId = packetId;
        this.deviceType = deviceType;
        this.ip = ip;
        this.messageType = messageType;
        this.encryptType = encryptType;
        this.serializeAlgorithm = serializeAlgorithm;
        this.message = message;
    }

    public Packet(short protocol, byte protocolVersion, long packetId, byte deviceType, String ip, byte encryptType, byte serializeAlgorithm, byte messageType, int messageLength, T message) {
        this.protocol = protocol;
        this.protocolVersion = protocolVersion;
        this.packetId = packetId;
        this.deviceType = deviceType;
        this.ip = ip;
        this.encryptType = encryptType;
        this.serializeAlgorithm = serializeAlgorithm;
        this.messageType = messageType;
        this.messageLength = messageLength;
        this.message = message;
    }

    @Override
    public String toString() {
        return "Packet{" +
                "magic=" + magic +
                ", protocol=" + protocol +
                ", protocolVersion=" + protocolVersion +
                ", packetId=" + packetId +
                ", deviceType=" + deviceType +
                ", ip='" + ip + '\'' +
                ", encryptType=" + encryptType +
                ", serializeAlgorithm=" + serializeAlgorithm +
                ", messageType=" + messageType +
                ", messageLength=" + messageLength +
                ", message=" + message +
                '}';
    }
}
