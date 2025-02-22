package com.ouyunc.base.constant;

/**
 * mq topic queue 等相关常量
 */
public class MqConstant {

    /**
     * ouyunc prefix
     */
    public static final String OUYUNC_PREFIX = "ouyunc";


    /**
     * kafka 保存消息 topic
     */
    public static final String KAFKA_SAVE_MESSAGE_TOPIC = OUYUNC_PREFIX + MessageConstant.UNDERLINE + "message_save";

    /**
     * kafka 撤销消息 topic
     */
    public static final String KAFKA_WITHDRAW_MESSAGE_TOPIC = OUYUNC_PREFIX + MessageConstant.UNDERLINE + "message_withdraw";

    /**
     * kafka 已读回执消息 topic
     */
    public static final String KAFKA_READ_RECEIPT_MESSAGE_TOPIC = OUYUNC_PREFIX + MessageConstant.UNDERLINE + "message_read_receipt";
}
