package com.ouyunc.base.constant.enums;

/**
 * @Author fzx
 * @Description: mqtt 协议的消息类型枚举
 **/
public enum MqttMessageTypeEnum implements MessageType {
    MQTT((byte) 51, ProtocolTypeEnum.MQTT.getProtocol(), ProtocolTypeEnum.MQTT.getProtocolVersion(), "MQTT",  "mqtt消息类型"),

    ;

    private byte type;

    private byte protocol;

    private byte protocolVersion;

    private String name;
    private String description;

    MqttMessageTypeEnum(byte type, byte protocol, byte protocolVersion, String name, String description) {
        this.type = type;
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
