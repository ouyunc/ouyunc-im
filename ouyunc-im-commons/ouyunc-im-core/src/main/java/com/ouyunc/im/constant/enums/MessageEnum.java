package com.ouyunc.im.constant.enums;

/**
 * @Author fangzhenxun
 * @Description: 协议包中的消息类型枚举, RPC 框架中有请求、响应、心跳类型。IM 通讯场景中有登陆、创建群聊、发送消息、接收消息、退出群聊等类型。
 **/
public enum MessageEnum {


    SYN_ACK((byte) 0, "syn_ack",  "内部使用的心跳消息类型"),

    // ====================================im使用的消息类型===========================================
    IM_PING_PONG((byte) 1, "im_ping_pong",  "外部客户端心跳消息"),
    IM_LOGIN((byte) 2, "im_login",  "外部客户端等录消息") ,
    IM_QOS((byte) 3, "im_qos",  "qos消息"),
    IM_READ_RECEIPT((byte) 4, "im_read_receipt",  "已读回执消息"),
    IM_PRIVATE_CHAT((byte) 5, "im_private_chat",  "私聊消息"),
    IM_GROUP_CHAT((byte) 6, "im_group_chat",  "群聊消息"),
    IM_WITHDRAW((byte) 7, "im_withdraw",  "消息撤回"),
    IM_FRIEND_REQUEST((byte) 8, "im_friend_request",  "好友请求相关消息"),
    IM_GROUP_REQUEST((byte) 9, "im_group_request",  "群请求相关消息"),

    MQTT_CONNECT((byte) 2, "1",  "mqtt 连接消息"),
    MQTT_CONNACK((byte)22, "2", "mqtt 连接回复消息"),
    MQTT_PUBLISH((byte)23, "3", "mqtt 发布消息"),
    MQTT_PUBACK((byte)24, "4", "mqtt 发布消息"),
    MQTT_PUBREC((byte)25, "5", "mqtt 发布消息"),
    MQTT_PUBREL((byte)26, "6", "mqtt 发布消息"),
    MQTT_PUBCOMP((byte)27, "7", "mqtt 发布消息"),
    MQTT_SUBSCRIBE((byte)28, "8", "mqtt 订阅消息"),
    MQTT_SUBACK((byte)29, "9", "mqtt 订阅回复消息"),
    MQTT_UNSUBSCRIBE((byte)30, "10", "mqtt 解除订阅消息"),
    MQTT_UNSUBACK((byte)31, "11", "mqtt 解除订阅回复消息"),
    MQTT_PINGREQ((byte)1, "12", "mqtt 心跳ping"),
    MQTT_PINGRESP((byte)33, "13", "mqtt 心跳pong"),
    MQTT_DISCONNECT((byte)34, "14", "mqtt 断开连接消息"),
    MQTT_AUTH((byte)35, "15", "mqtt 认证权限消息"),




    BROADCAST((byte) 50, "broadcast",  "广播消息"),

    IM_QOS_RETRY((byte) 66, "im_qos_retry",  "im qos消息重试"),

    IM_CHAT_BOT((byte) 80, "im_chat_bot",  "聊天机器人消息"),

    IM_CUSTOMER((byte) 90, "im_customer",  "客服消息处理"),



    // =======================================http使用的消息类型================================
    RPC_REQUEST((byte) 101,  "rpc_request", "RPC中有请求消息"),
    RPC_RESPONSE((byte) 102, "rpc_response", "RPC中有响应消息"),
    RPC_HEART_BEAT((byte) 103,  "rpc_heart_beat", "RPC中心跳消息"),


    IM_SERVER_NOTIFY((byte) 124,  "im_server_notify", "im 服务端的通知消息"),

    IM_SERVER_WARNING((byte) 125,  "im_server_warning", "im 服务端的警告消息");

    private byte value;
    private String name;
    private String description;

    MessageEnum(byte value, String name, String description) {
        this.value = value;
        this.name = name;
        this.description = description;
    }

    public byte getValue() {
        return value;
    }

    public void setValue(byte value) {
        this.value = value;
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

    public static MessageEnum prototype(byte value) {
        for (MessageEnum messageEnum : MessageEnum.values()) {
            if (messageEnum.value == value) {
                return messageEnum;
            }
        }
        return null;
    }

    public static MessageEnum getMessageEnumByName(String name) {
        for (MessageEnum messageEnum : MessageEnum.values()) {
            if (messageEnum.name.equals(name)) {
                return messageEnum;
            }
        }
        return null;
    }

}
