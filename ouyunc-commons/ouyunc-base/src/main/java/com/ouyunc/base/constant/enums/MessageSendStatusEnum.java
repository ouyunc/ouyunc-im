package com.ouyunc.base.constant.enums;

/** 客户端发送结果；受理结果与接收方送达、已读状态彼此独立。 */
public enum MessageSendStatusEnum {
    ACCEPTED,
    REJECTED,
    RETRY_LATER,
    UNKNOWN
}
