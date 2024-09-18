package com.ouyunc.base.constant.enums;

/**
 * @author fzx
 * @description 基础消息内容类型枚举
 */
public enum OuyuncMessageContentTypeEnum implements MessageContentType {
    SYN_CONTENT(1, ProtocolTypeEnum.OUYUNC.getProtocol(), ProtocolTypeEnum.OUYUNC.getProtocolVersion(), String.class, "内部消息心跳syn消息内容"),
    ACK_CONTENT(2,ProtocolTypeEnum.OUYUNC.getProtocol(), ProtocolTypeEnum.OUYUNC.getProtocolVersion(), String.class, "内部消息心跳ack消息内容"),

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
