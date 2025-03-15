package com.ouyunc.base.constant;

/**
 * mq topic queue 等相关常量
 */
public class MqConstant {


    /**
     * kafka 发送消息失败异常 topic
     */
    public static final String KAFKA_MESSAGE_SEND_FAIL_TOPIC = MessageConstant.OUYUNC + MessageConstant.UNDERLINE + "message_send_fail";
    /**
     * kafka 发送消息失败异常 group
     */
    public static final String KAFKA_MESSAGE_SEND_FAIL_GROUP = "message_send_fail";

    /**
     * kafka 异常 topic
     */
    public static final String KAFKA_EXCEPTION_TOPIC = MessageConstant.OUYUNC + MessageConstant.UNDERLINE + "exception";

    /**
     * kafka 异常 group
     */
    public static final String KAFKA_EXCEPTION_GROUP = "exception";

    /**
     * kafka 保存消息 topic
     */
    public static final String KAFKA_SAVE_MESSAGE_TOPIC = MessageConstant.OUYUNC + MessageConstant.UNDERLINE + "message_save";

    /**
     * kafka 保存消息 group
     */
    public static final String KAFKA_SAVE_MESSAGE_GROUP = "message_save";

    /**
     * kafka 撤销消息 topic
     */
    public static final String KAFKA_WITHDRAW_MESSAGE_TOPIC =  MessageConstant.OUYUNC + MessageConstant.UNDERLINE + "message_withdraw";

    /**
     * kafka 撤销消息 group
     */
    public static final String KAFKA_WITHDRAW_MESSAGE_GROUP =  "message_withdraw";

    /**
     * kafka 已读回执消息 topic
     */
    public static final String KAFKA_READ_RECEIPT_MESSAGE_TOPIC =  MessageConstant.OUYUNC + MessageConstant.UNDERLINE + "message_read_receipt";

    /**
     * kafka 已读回执消息 group
     */
    public static final String KAFKA_READ_RECEIPT_MESSAGE_GROUP =  "message_read_receipt";

    /**
     * kafka 好友请求（加入/同意/拒绝） topic
     */
    public static final String KAFKA_FRIEND_REQUEST_TOPIC =  MessageConstant.OUYUNC + MessageConstant.UNDERLINE + "friend_request";

    /**
     * kafka 好友请求（加入/同意/拒绝） group
     */
    public static final String KAFKA_FRIEND_REQUEST_GROUP =  "friend_request";

}
