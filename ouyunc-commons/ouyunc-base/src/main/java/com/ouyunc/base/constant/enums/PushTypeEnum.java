package com.ouyunc.base.constant.enums;

import java.util.Objects;

/**
 * 推送类型：目标维度 +（HTTP 推送场景下）与内线 {@link MessageTypeEnum} 绑定。
 */
public enum PushTypeEnum implements Type<Integer>{
    /** 系统通知（单播） */
    SERVER_NOTIFY_TEXT(0, MessageTypeEnum.SERVER_NOTIFY, MessageContentTypeEnum.TEXT_CONTENT),
    /** 私聊代发 */
    ONE2ONE_TEXT(1, MessageTypeEnum.ONE_2_ONE, MessageContentTypeEnum.TEXT_CONTENT),
    /** 群聊代发 */
    GROUP_TEXT(2, MessageTypeEnum.GROUP, MessageContentTypeEnum.TEXT_CONTENT),
    /** 客服会话代发 */
    CUSTOMER_SERVICE_TEXT(3, MessageTypeEnum.CUSTOMER_SERVICE, MessageContentTypeEnum.TEXT_CONTENT),
    /** 广播系统通知（appKey 下在线用户） */
    BROADCAST_SERVER_NOTIFY(4, MessageTypeEnum.SERVER_NOTIFY, MessageContentTypeEnum.TEXT_CONTENT),

    ;

    private final Integer type;

    private final MessageType messageType;

    private final MessageContentType messageContentType;

    PushTypeEnum(Integer type, MessageType messageType, MessageContentType messageContentType) {
        this.type = type;
        this.messageType = messageType;
        this.messageContentType = messageContentType;
    }

    public Integer getType() {
        return type;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public MessageContentType getMessageContentType() {
        return messageContentType;
    }

    public static PushTypeEnum getPushTypeEnum(Integer type) {
        for (PushTypeEnum pushTypeEnum : PushTypeEnum.values()) {
            if (Objects.equals(pushTypeEnum.type, type)) {
                return pushTypeEnum;
            }
        }
        return null;
    }
}
