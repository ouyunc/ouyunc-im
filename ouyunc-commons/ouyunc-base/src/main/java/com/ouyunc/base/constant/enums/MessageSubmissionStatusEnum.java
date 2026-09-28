package com.ouyunc.base.constant.enums;

/** 客户端消息提交受理状态；该状态与接收方送达、已读状态彼此独立。 */
public enum MessageSubmissionStatusEnum {
    ACCEPTED,
    REJECTED,
    RETRY_LATER,
    UNKNOWN
}
