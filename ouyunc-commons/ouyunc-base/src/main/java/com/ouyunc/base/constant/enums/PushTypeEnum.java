package com.ouyunc.base.constant.enums;

/**
 * 推送类型：目标维度 +（HTTP 推送场景下）与内线 {@link MessageTypeEnum} 绑定。
 * <ul>
 *   <li>{@link #USER} → 一对一消息 {@link MessageTypeEnum#ONE_2_ONE}，内容类型 {@link MessageContentTypeEnum#TEXT_CONTENT}</li>
 *   <li>{@link #CUSTOMER_SERVICE} → 客服会话 {@link MessageTypeEnum#CUSTOMER_SERVICE}，内容类型 {@link MessageContentTypeEnum#TEXT_CONTENT}</li>
 *   <li>其它值由具体通道解析，HTTP 推送见服务端校验</li>
 * </ul>
 */
public enum PushTypeEnum {
    /** 广播 */
    BROADCAST(0, MessageTypeEnum.SERVER_NOTIFY, MessageContentTypeEnum.TEXT_CONTENT),

    ;

    private final int type;

    private final MessageType messageType;

    private final MessageContentType messageContentType;

    PushTypeEnum(int type, MessageType messageType, MessageContentType messageContentType) {
        this.type = type;
        this.messageType = messageType;
        this.messageContentType = messageContentType;
    }

    public int getType() {
        return type;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public MessageContentType getMessageContentType() {
        return messageContentType;
    }

    public static PushTypeEnum getPushTypeEnum(int type) {
        for (PushTypeEnum pushTypeEnum : PushTypeEnum.values()) {
            if (pushTypeEnum.type == type) {
                return pushTypeEnum;
            }
        }
        return null;
    }
}
