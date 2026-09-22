package com.ouyunc.base.constant;

/**
 * mq topic queue 等相关常量
 */
public class MqConstant {
    /** Kafka 归档必须等待 ISR 确认；两个值在客户端协议中等价。 */
    public static final String KAFKA_ACKS_ALL = "all";
    public static final String KAFKA_ACKS_ALL_NUMERIC = "-1";



    /**
     * 发送消息失败异常 group
     */
    public static final String MQ_MESSAGE_SEND_FAIL_GROUP = "message_send_fail";



    /**
     * 发送消息失败异常 topic
     */
    public static final String MQ_MESSAGE_SEND_FAIL_TOPIC = MessageConstant.OUYUNC + MessageConstant.UNDERLINE + MQ_MESSAGE_SEND_FAIL_GROUP;

    /**
     * 异常 group
     */
    public static final String MQ_EXCEPTION_GROUP = "exception";



    /**
     * 异常 topic
     */
    public static final String MQ_EXCEPTION_TOPIC = MessageConstant.OUYUNC + MessageConstant.UNDERLINE + MQ_EXCEPTION_GROUP;



    /**
     * 保存消息 group
     */
    public static final String MQ_SAVE_MESSAGE_GROUP = "message_save";



    /**
     * 保存消息 topic
     */
    public static final String MQ_SAVE_MESSAGE_TOPIC = MessageConstant.OUYUNC + MessageConstant.UNDERLINE + MQ_SAVE_MESSAGE_GROUP;


    /**
     * 撤销消息 group
     */
    public static final String MQ_WITHDRAW_MESSAGE_GROUP =  "message_withdraw";



    /**
     * 撤销消息 topic
     */
    public static final String MQ_WITHDRAW_MESSAGE_TOPIC =  MessageConstant.OUYUNC + MessageConstant.UNDERLINE + MQ_WITHDRAW_MESSAGE_GROUP;


    /**
     * 已读回执消息 group
     */
    public static final String MQ_READ_RECEIPT_MESSAGE_GROUP =  "message_read_receipt";



    /**
     * 已读回执消息 topic
     */
    public static final String MQ_READ_RECEIPT_MESSAGE_TOPIC =  MessageConstant.OUYUNC + MessageConstant.UNDERLINE + MQ_READ_RECEIPT_MESSAGE_GROUP;


    /**
     * 好友请求（加入/同意/拒绝） group
     */
    public static final String MQ_FRIEND_REQUEST_GROUP =  "friend_request";



    /**
     * 好友请求（加入/同意/拒绝） topic
     */
    public static final String MQ_FRIEND_REQUEST_TOPIC =  MessageConstant.OUYUNC + MessageConstant.UNDERLINE + MQ_FRIEND_REQUEST_GROUP;



    /**
     * 群请求 group
     */
    public static final String MQ_GROUP_REQUEST_GROUP =  "group_request";


    /**
     * 群请求 topic
     */
    public static final String MQ_GROUP_REQUEST_TOPIC =  MessageConstant.OUYUNC + MessageConstant.UNDERLINE + MQ_GROUP_REQUEST_GROUP;



    /**
     * 外部渠道下行（WhatsApp / Telegram 等）group
     */
    public static final String MQ_EXTERNAL_CHANNEL_OUTBOUND_GROUP = "external_channel_outbound";

    /**
     * 外部渠道下行 topic，由外渠适配服务消费
     */
    public static final String MQ_EXTERNAL_CHANNEL_OUTBOUND_TOPIC = MessageConstant.OUYUNC + MessageConstant.UNDERLINE + MQ_EXTERNAL_CHANNEL_OUTBOUND_GROUP;

    /**
     * 客服 ticket 活动通知（IM → CS），与 {@code CsKafkaTopics#TICKET_ACTIVITY} 一致。
     */
    public static final String MQ_CS_TICKET_ACTIVITY_GROUP = "cs_ticket_activity";

    public static final String MQ_CS_TICKET_ACTIVITY_TOPIC = MessageConstant.OUYUNC + MessageConstant.UNDERLINE + MQ_CS_TICKET_ACTIVITY_GROUP;


    /**
     * 客服坐席 IM 通道关闭通知（IM → CS 踢技能池），与 {@code CsKafkaTopics#AGENT_PRESENCE} 一致。
     */
    public static final String MQ_CS_AGENT_PRESENCE_GROUP = "cs_agent_presence";

    public static final String MQ_CS_AGENT_PRESENCE_TOPIC = MessageConstant.OUYUNC + MessageConstant.UNDERLINE + MQ_CS_AGENT_PRESENCE_GROUP;


    /**
     * 确认路径 topic（归档/撤回/已读/好友请求/群请求）要求 Kafka acks=all。
     */
    public static boolean requiresBrokerAcksAll(String topic) {
        return MQ_SAVE_MESSAGE_TOPIC.equals(topic)
                || MQ_WITHDRAW_MESSAGE_TOPIC.equals(topic)
                || MQ_READ_RECEIPT_MESSAGE_TOPIC.equals(topic)
                || MQ_FRIEND_REQUEST_TOPIC.equals(topic)
                || MQ_GROUP_REQUEST_TOPIC.equals(topic);
    }

}
