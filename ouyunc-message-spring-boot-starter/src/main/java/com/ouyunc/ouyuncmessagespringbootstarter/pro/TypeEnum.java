package com.ouyunc.ouyuncmessagespringbootstarter.pro;

import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.ProtocolTypeEnum;

/**
 * @Author fzx
 * @Description: ws 协议的消息类型枚举
 **/
public enum TypeEnum implements MessageType {
    PING_PONG((byte) 10, ProtocolTypeEnum.WS.getProtocol(), ProtocolTypeEnum.WS.getProtocolVersion(),"ping_pong",  "外部客户端心跳消息"),
    ;

    private byte type;

    private byte protocol;

    private byte protocolVersion;

    private String name;
    private String description;

    TypeEnum(byte messageType, byte protocol, byte protocolVersion, String name, String description) {
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
