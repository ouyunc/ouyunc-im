package com.ouyunc.base.constant.enums;

import com.ouyunc.base.constant.NumberConstant;

/**
 * 协议类型
 */
public enum ProtocolTypeEnum {

    WS(NumberConstant.NUMBER_1, NumberConstant.NUMBER_1),
    HTTP(NumberConstant.NUMBER_2, NumberConstant.NUMBER_1),
    OUYUNC(NumberConstant.NUMBER_3, NumberConstant.NUMBER_1),
    MQTT(NumberConstant.NUMBER_4, NumberConstant.NUMBER_0),
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
