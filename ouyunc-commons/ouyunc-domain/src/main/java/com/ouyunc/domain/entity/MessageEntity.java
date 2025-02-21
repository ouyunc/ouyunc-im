package com.ouyunc.domain.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * mongodb message
 */
@Document(collection = "ouyunc-im-message")
public class MessageEntity {
    /**
     * 主键id
     */
    @Id
    private long id;

    /**
     * 协议
     */
    private byte protocol;

    /**
     * 协议版本
     */
    private byte protocolVersion;

    /**
     * 设备类型
     */
    private byte deviceType;

    /**
     * 网络类型
     */
    private byte networkType;

    /**
     * 加密类型
     */
    private byte encryptType;

    /**
     * 序列化算法
     */
    private byte serializeAlgorithm;

    /**
     * 消息类型
     */
    private byte messageType;

    /**
     * 保留字段
     */
    private byte retain;

    /**
     * 客户端ip
     */
    private String clientIp;

    /**
     * 发送者
     */
    private String from;

    /**
     * 接收者
     */
    private String to;

    /**
     * 内容类型
     */
    private int contentType;

    /**
     * 内容
     */
    private String content;

    /**
     * Qos
     */
    private int qos;

    /**
     * 群聊
     */
    private List<String> at;

    /**
     * 额外信息
     */
    private String extra;

    /**
     * 客户端发送时间
     */
    private long clientSendTime;

    /**
     * 服务器到达时间
     */
    private long serverArrivalTime;

    /**
     * 是否已读
     */
    private int read;

    /**
     * 是否撤回
     */
    private int withdrawn;


    /**
     * 过期时间
     */
    private LocalDateTime expireAt;

    public static final class Fields {
        public static final String id = "id";
        public static final String read = "read";
        public static final String withdrawn = "withdrawn";
        public static final String from = "from";
        public static final String to = "to";
        public static final String messageType = "messageType";
        public static final String contentType = "contentType";
    }



    // 构造函数、Getter 和 Setter 方法
    public MessageEntity() {}

    public MessageEntity(long id, byte protocol, byte protocolVersion, byte deviceType, byte networkType, byte encryptType, byte serializeAlgorithm, byte messageType, byte retain, String clientIp, String from, String to, int contentType, String content, int qos, List<String> at, String extra, long clientSendTime, long serverArrivalTime, int read, int withdrawn, LocalDateTime expireAt) {
        this.id = id;
        this.protocol = protocol;
        this.protocolVersion = protocolVersion;
        this.deviceType = deviceType;
        this.networkType = networkType;
        this.encryptType = encryptType;
        this.serializeAlgorithm = serializeAlgorithm;
        this.messageType = messageType;
        this.retain = retain;
        this.clientIp = clientIp;
        this.from = from;
        this.to = to;
        this.contentType = contentType;
        this.content = content;
        this.qos = qos;
        this.at = at;
        this.extra = extra;
        this.clientSendTime = clientSendTime;
        this.serverArrivalTime = serverArrivalTime;
        this.read = read;
        this.withdrawn = withdrawn;
        this.expireAt = expireAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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

    public byte getRetain() {
        return retain;
    }

    public void setRetain(byte retain) {
        this.retain = retain;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public int getContentType() {
        return contentType;
    }

    public void setContentType(int contentType) {
        this.contentType = contentType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getQos() {
        return qos;
    }

    public void setQos(int qos) {
        this.qos = qos;
    }

    public List<String> getAt() {
        return at;
    }

    public void setAt(List<String> at) {
        this.at = at;
    }

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }

    public long getClientSendTime() {
        return clientSendTime;
    }

    public void setClientSendTime(long clientSendTime) {
        this.clientSendTime = clientSendTime;
    }

    public long getServerArrivalTime() {
        return serverArrivalTime;
    }

    public void setServerArrivalTime(long serverArrivalTime) {
        this.serverArrivalTime = serverArrivalTime;
    }

    public int getRead() {
        return read;
    }

    public void setRead(int read) {
        this.read = read;
    }

    public int getWithdrawn() {
        return withdrawn;
    }

    public void setWithdrawn(int withdrawn) {
        this.withdrawn = withdrawn;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }
}