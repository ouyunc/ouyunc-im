package com.ouyu.im.constant.enums;

import com.ouyu.im.packet.message.*;
import com.ouyu.im.processor.*;

/**
 * @Author fangzhenxun
 * @Description: 协议包中的消息类型枚举, RPC 框架中有请求、响应、心跳类型。IM 通讯场景中有登陆、创建群聊、发送消息、接收消息、退出群聊等类型。
 * // 在这里面可以做认证授权
 * @Version V1.0
 **/
public enum MessageEnum {

    // 都是单例

    // im内部使用的消息类型,主要使用场景是注册表的使用
    SYN_ACK((byte) 1, SynAckMessageProcessor.class, HeartBeatMessage.class, "im内部使用的心跳消息类型"),

    // im外部客户端使用的消息类型
    RPC_REQUEST((byte) 2, null, null, "RPC中有请求消息"),
    RPC_RESPONSE((byte) 3, null, null, "RPC中有响应消息"),
    RPC_HEART_BEAT((byte) 4, null, null, "RPC中心跳消息"),

    //======================================== im消息类型=======================================
    IM_LOGIN((byte) 5, LoginMessageProcessor.class, LoginMessage.class, "im中登录消息") ,

    IM_PING_PONG((byte) 6, PingPongMessageProcessor.class, HeartBeatMessage.class, "外部客户端心跳消息"),

    // @todo 需要优化做处理
    IM_P_CHAT((byte) 7, PChatMessageProcessor.class, PChatMessage.class, "im私聊消息"),
    IM_G_CHAT((byte) 8, GChatMessageProcessor.class, GChatMessage.class, "im群聊消息") ,
    IM_BROADCAST((byte) 9, null, null, "im广播消息"),

    // 接收到目标服务器的应答
    IM_ACKNOWLEDGE((byte) 10, AcknowledgeMessageProcessor.class, AcknowledgeMessage.class, "外部客户端的消息ack") ,

    // 好友添加包含，添加好友请求（req），同意添加（resolve），拒绝添加（reject）,删除好友（delete）
    IM_PUSH_FRIEND((byte) 11, FriendMessageProcessor.class, PushMessage.class, "im推送消息好友相关处理"),

    // 加群（req），同意添加（resolve），拒绝添加（reject），退群/删除群（delete），解散群（disband）
    IM_PUSH_GROUP((byte) 12, FriendMessageProcessor.class, PushMessage.class, "im推送消息群组相关处理");

    private byte value;
    private Class<? extends Message> messageClass;
    private Class<? extends MessageProcessor> messageProcessorClass;
    private String description;

    MessageEnum(byte value, Class messageProcessorClass, Class messageClass, String description) {
        this.value = value;
        this.messageClass = messageClass;
        this.messageProcessorClass = messageProcessorClass;
        this.description = description;
    }

    public byte getValue() {
        return value;
    }

    public void setValue(byte value) {
        this.value = value;
    }

    public Class<? extends Message> getMessageClass() {
        return messageClass;
    }

    public void setMessageClass(Class<? extends Message> messageClass) {
        this.messageClass = messageClass;
    }

    public Class<? extends MessageProcessor> getMessageProcessorClass() {
        return messageProcessorClass;
    }

    public void setMessageProcessorClass(Class<? extends MessageProcessor> messageProcessorClass) {
        this.messageProcessorClass = messageProcessorClass;
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
