package com.ouyunc.base.constant.enums;

/**
 * @Author fzx
 * @Description: OUYUNC 协议的 消息类型枚举
 **/
public enum OuyuncMessageTypeEnum implements MessageType {
    SYN_ACK((byte) 0, ProtocolTypeEnum.OUYUNC.getProtocol(), ProtocolTypeEnum.OUYUNC.getProtocolVersion(), "syn_ack",  "集群内部使用的心跳消息类型"),

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
