package com.ouyunc.base.constant;

/**
 * mq topic queue 等相关常量
 */
public class MqConstant {


    /**
     * kafka 发送消息失败异常 topic
     */
    public static final String KAFKA_SEND_FAIL_TOPIC = MessageConstant.OUYUNC + MessageConstant.UNDERLINE + "send_fail";

    /**
     * kafka 异常 topic
     */
    public static final String KAFKA_EXCEPTION_TOPIC = MessageConstant.OUYUNC + MessageConstant.UNDERLINE + "exception";

    /**
     * kafka 保存消息 topic
     */
    public static final String KAFKA_SAVE_MESSAGE_TOPIC = MessageConstant.OUYUNC + MessageConstant.UNDERLINE + "message_save";

    /**
     * kafka 撤销消息 topic
     */
    public static final String KAFKA_WITHDRAW_MESSAGE_TOPIC =  MessageConstant.OUYUNC + MessageConstant.UNDERLINE + "message_withdraw";

    /**
     * kafka 已读回执消息 topic
     */
    public static final String KAFKA_READ_RECEIPT_MESSAGE_TOPIC =  MessageConstant.OUYUNC + MessageConstant.UNDERLINE + "message_read_receipt";
}
