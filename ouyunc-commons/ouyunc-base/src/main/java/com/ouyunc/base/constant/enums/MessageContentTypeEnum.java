package com.ouyunc.base.constant.enums;

import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.content.LoginContent;

/**
 * @author fzx
 * @description 消息内容类型枚举
 */
public enum MessageContentTypeEnum implements MessageContentType {
    QOS_DUP_CONTENT(NumberConstant.NUMBER_NEGATIVE_3, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), Packet.class,  "qos  客户端重发消息的消息内容"),
    LOGIN_REQUEST_CONTENT(NumberConstant.NUMBER_10,ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), LoginContent.class, "外部客户端登录消息内容"),
    LOGIN_RESPONSE_FAIL_CONTENT(NumberConstant.NUMBER_11,ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), String.class, "外部客户端登录失败消息内容"),
    LOGIN_RESPONSE_SUCCESS_CONTENT(NumberConstant.NUMBER_12,ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), String.class, "客户端登录成功"),
    PING_CONTENT(NumberConstant.NUMBER_13,ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), String.class, "外部消息心跳ping消息内容"),

    TEXT_CONTENT(NumberConstant.NUMBER_127, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), String.class, "文本内容类型"),

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

    MessageContentTypeEnum(int messageContentType, byte protocol, byte protocolVersion, Class<?> contentClass, String description) {
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
