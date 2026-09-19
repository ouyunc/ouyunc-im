package com.ouyunc.base.constant.enums;

import com.ouyunc.base.constant.NumberConstant;

/**
 * @author fzx
 * @description 基础消息内容类型枚举
 */
public enum OuyuncMessageContentTypeEnum implements MessageContentType {
    SYN_CONTENT(NumberConstant.NUMBER_1, ProtocolTypeEnum.OUYUNC.getProtocol(), ProtocolTypeEnum.OUYUNC.getProtocolVersion(), String.class, "内部消息心跳syn消息内容"),
    ACK_CONTENT(NumberConstant.NUMBER_2,ProtocolTypeEnum.OUYUNC.getProtocol(), ProtocolTypeEnum.OUYUNC.getProtocolVersion(), String.class, "内部消息心跳ack消息内容"),
    QOS_RETRY_CANCEL_CONTENT(NumberConstant.NUMBER_3, ProtocolTypeEnum.OUYUNC.getProtocol(), ProtocolTypeEnum.OUYUNC.getProtocolVersion(), String.class, "集群内取消 QoS 下行重试载荷"),
    /** 认证 Proof JSON 字符串；Packet 体用 PROTO_STUFF，content 内仍为签名用的规范 JSON。 */
    AUTH_CONTENT(NumberConstant.NUMBER_4, ProtocolTypeEnum.OUYUNC.getProtocol(), ProtocolTypeEnum.OUYUNC.getProtocolVersion(), String.class, "集群连接认证 Proof 内容"),

    ;
    /**
     * 唯一标识code
     */
    private int type;

    private byte protocol;

    private byte protocolVersion;
    /**
     * 枚举对应的内容具体类
     */
    private Class<?> contentClass;
    /**
     * 描述
     */
    private String description;

    OuyuncMessageContentTypeEnum(int messageContentType, byte protocol, byte protocolVersion, Class<?> contentClass, String description) {
        this.type = messageContentType;
        this.protocol = protocol;
        this.protocolVersion = protocolVersion;
        this.contentClass = contentClass;
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

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public Class<?> getContentClass() {
        return contentClass;
    }

    public void setContentClass(Class<?> contentClass) {
        this.contentClass = contentClass;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}
