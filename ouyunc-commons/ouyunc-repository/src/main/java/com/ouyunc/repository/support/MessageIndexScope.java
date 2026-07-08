package com.ouyunc.repository.support;

/**
 * 消息索引 / 特殊消息校验作用域。
 */
public enum MessageIndexScope {

    /** 单聊 channel：{@code IdentityUtil.sessionId(from, to)}。 */
    CHANNEL_SESSION,

    /** 客服咨询单：{@code ticketId}，目标消息须 {@code correlationId == ticketId}。 */
    CS_TICKET
}
