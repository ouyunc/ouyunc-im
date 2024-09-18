package com.ouyunc.base.constant.enums;

import com.ouyunc.base.packet.message.content.LoginContent;

/**
 * @author fzx
 * @description 基础消息内容类型枚举
 */
public enum WsMessageContentTypeEnum implements MessageContentType {
    LOGIN_REQUEST_CONTENT(10,ProtocolTypeEnum.WS.getProtocol(), ProtocolTypeEnum.WS.getProtocolVersion(), LoginContent.class, "外部客户端登录消息内容"),
    LOGIN_RESPONSE_FAIL_CONTENT(11,ProtocolTypeEnum.WS.getProtocol(), ProtocolTypeEnum.WS.getProtocolVersion(), String.class, "外部客户端登录失败消息内容"),
    LOGIN_RESPONSE_SUCCESS_CONTENT(12,ProtocolTypeEnum.WS.getProtocol(), ProtocolTypeEnum.WS.getProtocolVersion(), String.class, "外部客户端登录成功消息内容"),

    PING_CONTENT(13,ProtocolTypeEnum.WS.getProtocol(), ProtocolTypeEnum.WS.getProtocolVersion(), String.class, "外部消息心跳ping消息内容"),

    SERVER_NOTIFY_CONTENT(14,ProtocolTypeEnum.WS.getProtocol(), ProtocolTypeEnum.WS.getProtocolVersion(), String.class, "服务端发给客户端的通知内容"),
    TEXT_CONTENT(15,ProtocolTypeEnum.WS.getProtocol(), ProtocolTypeEnum.WS.getProtocolVersion(), String.class, "文本内容类型"),

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

    WsMessageContentTypeEnum(int messageContentType, byte protocol, byte protocolVersion, Class<?> contentClass, String description) {
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
