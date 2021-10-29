package com.ouyu.im.constant.enums;

/**
 * @Author fangzhenxun
 * @Description: 协议包中的消息类型枚举, RPC 框架中有请求、响应、心跳类型。IM 通讯场景中有登陆、创建群聊、发送消息、接收消息、退出群聊等类型。
 * // 在这里面可以做认证授权
 * @Version V1.0
 **/
public enum MessageEnum {

    // im内部使用的消息类型,主要使用场景是注册表的使用
    SYN_ACK((byte) 1, "syn_ack",  "im内部使用的心跳消息类型"),

    // im外部客户端使用的消息类型
    RPC_REQUEST((byte) 2,  "rpc_request", "RPC中有请求消息"),
    RPC_RESPONSE((byte) 3, "rpc_response", "RPC中有响应消息"),
    RPC_HEART_BEAT((byte) 4,  "rpc_heart_beat", "RPC中心跳消息"),

    //======================================== im消息类型=======================================
    IM_LOGIN((byte) 5, "im_login",  "im中登录消息") ,

    IM_PING_PONG((byte) 6, "im_ping_pong",  "外部客户端心跳消息"),

    // @todo 需要优化做处理
    IM_P_CHAT((byte) 7, "im_p_chat",  "im私聊消息"),
    IM_G_CHAT((byte) 8, "im_g_chat",  "im群聊消息") ,
    IM_BROADCAST((byte) 9, "im_broadcast",  "im广播消息"),

    // 接收到目标服务器的应答ack
    IM_ACK((byte) 10, "im_ack",  "外部客户端的消息ack") ,


    // 添加好友请求
    IM_FRIEND_ADD_REQ((byte)11, "im_friend_add_req","添加好友请求"),
    // 同意添加好友
    IM_FRIEND_ADD_RESOLVE((byte)12, "im_friend_add_resolve","同意添加好友"),
    // 拒绝添加好友
    IM_FRIEND_ADD_REJECT((byte)13, "im_friend_add_reject","拒绝添加好友"),
    // 删除好友
    IM_FRIEND_ADD_DELETE((byte)14, "im_friend_add_delete","删除好友"),

    // 加群（req），同意添加（resolve），拒绝添加（reject），退群/删除群（delete），解散群（disband）
    IM_PUSH_GROUP((byte) 15, "im_push_group",  "im推送消息群组相关处理");


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

}
