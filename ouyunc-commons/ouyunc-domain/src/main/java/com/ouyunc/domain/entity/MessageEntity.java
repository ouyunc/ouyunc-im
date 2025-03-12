package com.ouyunc.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serializable;

/**
 * mongodb message
 */
@TableName("ouyunc_im_message")
@Document(collection = "ouyunc_im_message")
public class MessageEntity implements Serializable {
    /**
     * 主键id
     */
    @Id
    private long id;

    /**
     * 协议
     */
    @Field("protocol")
    private byte protocol;

    /**
     * 协议版本
     */
    @Field("protocol_version")
    private byte protocolVersion;

    /**
     * 设备类型
     */
    @Field("device_type")
    private byte deviceType;

    /**
     * 网络类型
     */
    @Field("network_type")
    private byte networkType;

    /**
     * 加密类型
     */
    @Field("encrypt_type")
    private byte encryptType;

    /**
     * 序列化算法
     */
    @Field("serialize_algorithm")
    private byte serializeAlgorithm;

    /**
     * 消息类型
     */
    @Field("message_type")
    private byte messageType;

    /**
     * 保留字段
     */
    @Field("retain")
    private byte retain;

    /**
     * 客户端ip
     */
    @Field("client_ip")
    private String clientIp;

    /**
     * 发送者,mysql 关键字段
     */
    @Field("from")
    @TableField("`from`")
    private String from;

    /**
     * 接收者
     */
    @Field("to")
    @TableField("`to`")
    private String to;

    /**
     * 内容类型
     */
    @Field("content_type")
    private int contentType;

    /**
     * 内容
     */
    @Field("content")
    private String content;

    /**
     * Qos
     */
    @Field("qos")
    private int qos;

    /**
     * 群聊
     */
    @Field("at")
    @TableField("`at`")
    private String at;

    /**
     * 额外信息
     */
    @Field("extra")
    private String extra;

    /**
     * 客户端发送时间
     */
    @Field("client_send_time")
    private long clientSendTime;

    /**
     * 服务器到达时间
     */
    @Field("server_arrival_time")
    private long serverArrivalTime;

    /**
     * 是否已读
     */
    @Field("read")
    @TableField("`read`")
    private int read;

    /**
     * 是否撤回
     */
    @Field("withdrawn")
    private int withdrawn;

    /**
     * appKey
     */
    @Field("app_key")
    private String appKey;



    public static final class Fields {
        public static final String id = "id";
        public static final String ids = "ids";
        public static final String read = "read";
        public static final String withdrawn = "withdrawn";
        public static final String from = "from";
        public static final String to = "to";
        public static final String messageType = "messageType";
        public static final String contentType = "contentType";
        public static final String appKey = "appKey";
    }



    // 构造函数、Getter 和 Setter 方法
    public MessageEntity() {}


    public MessageEntity(long id, byte protocol, byte protocolVersion, byte deviceType, byte networkType, byte encryptType, byte serializeAlgorithm, byte messageType, byte retain, String clientIp, String from, String to, int contentType, String content, int qos, String at, String extra, long clientSendTime, long serverArrivalTime, int read, int withdrawn, String appKey) {
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
        this.appKey = appKey;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
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

    public String getAt() {
        return at;
    }

    public void setAt(String at) {
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

}