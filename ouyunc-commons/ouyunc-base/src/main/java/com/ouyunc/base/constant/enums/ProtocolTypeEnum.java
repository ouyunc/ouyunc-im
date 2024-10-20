package com.ouyunc.base.constant.enums;

/**
 * 协议类型
 */
public enum ProtocolTypeEnum {

    WS((byte) 1, (byte) 1),
    HTTP((byte) 2, (byte) 1),
    OUYUNC((byte) 3, (byte) 1),
    MQTT((byte) 4, (byte) 0),
    ;


    /**
     * 协议编号
     */
    private byte protocol;

    /**
     * 协议版本
     */
    private byte protocolVersion;


    ProtocolTypeEnum(byte protocol, byte protocolVersion) {
        this.protocol = protocol;
        this.protocolVersion = protocolVersion;
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

}
