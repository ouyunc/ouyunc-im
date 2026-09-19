package com.ouyunc.base.constant.enums;

import com.ouyunc.base.constant.NumberConstant;

/**
 * @Author fzx
 * @Description: OUYUNC 协议的 消息类型枚举
 **/
public enum OuyuncMessageTypeEnum implements MessageType {
    SYN_ACK(NumberConstant.NUMBER_0, ProtocolTypeEnum.OUYUNC.getProtocol(), ProtocolTypeEnum.OUYUNC.getProtocolVersion(), "syn_ack",  "集群内部使用的心跳消息类型"),
    /** 集群 TCP 连接 HMAC 认证首包；由 ServerHandler 消费，不进入业务 Processor。 */
    CLUSTER_AUTH(NumberConstant.NUMBER_2, ProtocolTypeEnum.OUYUNC.getProtocol(), ProtocolTypeEnum.OUYUNC.getProtocolVersion(), "cluster_auth", "集群连接认证首包"),
    /** 落地节点把 C2S ACK 转回始发节点，取消该端下行重试定时器。 */
    QOS_RETRY_CANCEL(NumberConstant.NUMBER_3, ProtocolTypeEnum.OUYUNC.getProtocol(), ProtocolTypeEnum.OUYUNC.getProtocolVersion(), "qos_retry_cancel", "集群内取消始发节点 QoS 下行重试"),

    ;

    private byte type;

    private byte protocol;

    private byte protocolVersion;

    private String name;
    private String description;

    OuyuncMessageTypeEnum(byte messageType, byte protocol, byte protocolVersion, String name, String description) {
        this.type = messageType;
        this.protocol = protocol;
        this.protocolVersion = protocolVersion;
        this.name = name;
        this.description = description;
    }

    @Override
    public byte getProtocol() {
        return protocol;
    }

    public void setProtocol(byte protocol) {
        this.protocol = protocol;
    }

    @Override
    public byte getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(byte protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public Byte getType() {
        return type;
    }

    public void setType(Byte type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}
