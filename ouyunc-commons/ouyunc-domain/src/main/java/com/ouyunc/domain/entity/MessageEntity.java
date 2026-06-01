package com.ouyunc.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serial;
import java.io.Serializable;

/**
 * mongodb message
 */
@TableName("ouyunc_im_message")
@Document(collection = "ouyunc_im_message")
public class MessageEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * 主键id
     */
    @Id
    private long id;

    /**
     * 协议
     */
    @Field("protocol")
    @Indexed
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
    @Indexed
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
    @Indexed
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
    @Indexed
    private String clientIp;

    /**
     * 发送者,mysql 关键字段
     */
    @Field("message_id")
    @Indexed
    private String messageId;

    /**
     * 发送者,mysql 关键字段
     */
    @Field("from")
    @Indexed
    @TableField("`from`")
    private String from;


    /**
     * 发送者类型
     */
    @Field("from_type")
    @Indexed
    private int fromType;

    /**
     * 接收者
     */
    @Field("to")
    @Indexed
    @TableField("`to`")
    private String to;


    /**
     * 接收者类型
     */
    @Field("to_type")
    @Indexed
    private int toType;

    /**
     * 内容类型
     */
    @Field("content_type")
    @Indexed
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
     * 群聊
     */
    @Field("ref")
    @TableField("ref")
    private String ref;

    /**
     * 额外信息
     */
    @Field("extra")
    private String extra;
    /**
     * 额外信息
     */
    @Field("correlation_id")
    private String correlationId;
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
     * appKey
     */
    @Field("app_key")
    @Indexed
    private String appKey;



    public static final class Fields {
        public static final String id = "id";
        public static final String ids = "ids";
        public static final String from = "from";
        public static final String fromType = "from_type";
        public static final String to = "to";
        public static final String toType = "to_type";
        public static final String messageId = "message_id";
        public static final String messageType = "message_type";
        public static final String contentType = "content_type";
        public static final String correlationId = "correlation_id";
        public static final String appKey = "appKey";
        public static final String retain = "retain";
    }



    // 构造函数、Getter 和 Setter 方法
    public MessageEntity() {}



    public MessageEntity(long id, byte protocol, byte protocolVersion, byte deviceType, byte networkType, byte encryptType, byte serializeAlgorithm, byte messageType, byte retain, String clientIp, String messageId, String from, int fromType, String to, int toType, int contentType, String content, int qos, String at, String ref, String extra, String correlationId, long clientSendTime, long serverArrivalTime, String appKey) {
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
        this.messageId = messageId;
        this.from = from;
        this.fromType = fromType;
        this.to = to;
        this.toType = toType;
        this.contentType = contentType;
        this.content = content;
        this.qos = qos;
        this.at = at;
        this.ref = ref;
        this.extra = extra;
        this.correlationId = correlationId;
        this.clientSendTime = clientSendTime;
        this.serverArrivalTime = serverArrivalTime;
        this.appKey = appKey;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
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

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
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

    public int getFromType() {
        return fromType;
    }

    public void setFromType(int fromType) {
        this.fromType = fromType;
    }

    public int getToType() {
        return toType;
    }

    public void setToType(int toType) {
        this.toType = toType;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }
}