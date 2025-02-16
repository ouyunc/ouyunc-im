package com.ouyunc.base.constant.enums;

import com.ouyunc.base.constant.NumberConstant;

/**
 * @Author fzx
 * @Description: OUYUNC 协议的 消息类型枚举
 **/
public enum MessageTypeEnum implements MessageType {
    PING_PONG(NumberConstant.NUMBER_NEGATIVE_1, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(),"ping_pong",  "外部客户端心跳消息"),
    LOGIN(NumberConstant.NUMBER_NEGATIVE_2, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), "login",  "外部客户端登录消息") ,
    QOS_S2C_ACK(NumberConstant.NUMBER_NEGATIVE_3, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), "s2c_ack",  "qos  服务端发送给客户端的，标识服务端已收到客户端传来的消息"),
    QOS_C2S_ACK(NumberConstant.NUMBER_NEGATIVE_4, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), "c2s_ack",  "qos  客户端发送给服务端的，标识客户端已经收到服务端发来的消息"),
    QOS_DUP(NumberConstant.NUMBER_NEGATIVE_5, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), "dup",  "qos  客户端重发消息"),


    ONE_2_ONE(NumberConstant.NUMBER_NEGATIVE_6, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), "one_2_one",  "一对一消息"),
    GROUP(NumberConstant.NUMBER_NEGATIVE_7, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), "group",  "群组消息"),


    SERVER_NOTIFY(NumberConstant.NUMBER_NEGATIVE_128, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocol(), "server_notify", "服务端的通知消息"),

    ;

    private byte type;

    private byte protocol;

    private byte protocolVersion;

    private String name;
    private String description;

    MessageTypeEnum(byte messageType, byte protocol, byte protocolVersion, String name, String description) {
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
