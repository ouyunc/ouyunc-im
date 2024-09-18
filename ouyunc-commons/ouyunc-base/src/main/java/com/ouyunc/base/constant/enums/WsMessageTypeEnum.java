package com.ouyunc.base.constant.enums;

/**
 * @Author fzx
 * @Description: ws 协议的消息类型枚举
 **/
public enum WsMessageTypeEnum implements MessageType {
    PING_PONG((byte) 10, ProtocolTypeEnum.WS.getProtocol(), ProtocolTypeEnum.WS.getProtocolVersion(),"ping_pong",  "外部客户端心跳消息"),
    LOGIN((byte) 11, ProtocolTypeEnum.WS.getProtocol(), ProtocolTypeEnum.WS.getProtocolVersion(), "login",  "外部客户端登录消息") ,

    SERVER_NOTIFY((byte) 12, ProtocolTypeEnum.WS.getProtocol(), ProtocolTypeEnum.WS.getProtocolVersion(), "server_notify", "服务端的通知消息"),

    ;

    private byte type;

    private byte protocol;

    private byte protocolVersion;

    private String name;
    private String description;

    WsMessageTypeEnum(byte messageType, byte protocol, byte protocolVersion, String name, String description) {
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
