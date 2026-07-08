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


    ONE_2_ONE(NumberConstant.NUMBER_NEGATIVE_6, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), "one_2_one",  "私聊"),
    GROUP(NumberConstant.NUMBER_NEGATIVE_7, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), "group",  "群聊"),

    ONE_2_ONE_FRIEND_REQUEST_JOIN(NumberConstant.NUMBER_NEGATIVE_8, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), "friend_request_join",  "一对一加好友请求"),
    ONE_2_ONE_FRIEND_REQUEST_AGREE(NumberConstant.NUMBER_NEGATIVE_9, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), "friend_request_agree",  "一对一同意好友请求"),
    ONE_2_ONE_FRIEND_REQUEST_REFUSE(NumberConstant.NUMBER_NEGATIVE_10, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), "friend_request_refuse",  "一对一拒绝好友请求"),


    GROUP_REQUEST_JOIN(NumberConstant.NUMBER_NEGATIVE_11, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), "group_request_join",  "主动加群请求"),
    GROUP_REQUEST_INVITE_JOIN(NumberConstant.NUMBER_NEGATIVE_12, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), "group_request_invite_join",  "邀请加群请求"),
    GROUP_REQUEST_AGREE(NumberConstant.NUMBER_NEGATIVE_13, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), "group_request_agree",  "处理人同意主动加群/被邀请加群请求"),
    GROUP_REQUEST_REFUSE(NumberConstant.NUMBER_NEGATIVE_14, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), "group_request_refuse",  "处理人拒绝主动加/被邀请加群请求"),
    GROUP_REQUEST_INVITED_JOINER_AGREE(NumberConstant.NUMBER_NEGATIVE_15, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), "group_request_invited_join_agree",  "被邀请者同意加群"),
    GROUP_REQUEST_INVITED_JOINER_REFUSE(NumberConstant.NUMBER_NEGATIVE_16, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), "group_request_invited_joiner_refuse",  "被邀请者拒绝加群"),

    CUSTOMER_SERVICE(NumberConstant.NUMBER_NEGATIVE_17, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocolVersion(), "customer_service", "客服会话"),

    CLIENT_LOGIN(NumberConstant.NUMBER_NEGATIVE_100, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocol(), "server_notify", "客户端上线"),
    CLIENT_LOGOUT(NumberConstant.NUMBER_NEGATIVE_101, ProtocolTypeEnum.ZERO.getProtocol(), ProtocolTypeEnum.ZERO.getProtocol(), "server_notify", "客户端下线"),

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
