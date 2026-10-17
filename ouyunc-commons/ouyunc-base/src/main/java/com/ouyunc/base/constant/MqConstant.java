package com.ouyunc.base.constant;

import java.util.Set;

/**
 * mq topic queue 等相关常量
 */
public class MqConstant {
    /** Kafka 归档必须等待 ISR 确认；两个值在客户端协议中等价。 */
    public static final String KAFKA_ACKS_ALL = "all";
    public static final String KAFKA_ACKS_ALL_NUMERIC = "-1";

    /**
     * 配置这些值时不加 topic 环境前缀，沿用生产原名。
     */
    public static final String MQ_TOPIC_PREFIX_PROD = "prod";
    public static final String MQ_TOPIC_PREFIX_PRODUCTION = "production";
    public static final String MQ_TOPIC_PREFIX_NONE = "none";
    public static final Set<String> MQ_TOPIC_PREFIX_DISABLED_VALUES = Set.of(
            MQ_TOPIC_PREFIX_PROD, MQ_TOPIC_PREFIX_PRODUCTION, MQ_TOPIC_PREFIX_NONE);



    /**
     * 发送消息失败异常 group
     */
    public static final String MQ_MESSAGE_SEND_FAIL_GROUP = "message_send_fail";



    /**
     * 发送消息失败异常 topic。逻辑名不再带 {@code ouyunc_}，环境隔离只走 {@link MqDestination} 前缀。
     */
    public static final String MQ_MESSAGE_SEND_FAIL_TOPIC = MQ_MESSAGE_SEND_FAIL_GROUP;

    /**
     * 异常 group
     */
    public static final String MQ_EXCEPTION_GROUP = "exception";



    /**
     * 异常 topic
     */
    public static final String MQ_EXCEPTION_TOPIC = MQ_EXCEPTION_GROUP;



    /**
     * 保存消息 group
     */
    public static final String MQ_SAVE_MESSAGE_GROUP = "message_save";



    /**
     * 保存消息 topic
     */
    public static final String MQ_SAVE_MESSAGE_TOPIC = MQ_SAVE_MESSAGE_GROUP;


    /**
     * 撤销消息 group
     */
    public static final String MQ_WITHDRAW_MESSAGE_GROUP =  "message_withdraw";



    /**
     * 撤销消息 topic
     */
    public static final String MQ_WITHDRAW_MESSAGE_TOPIC = MQ_WITHDRAW_MESSAGE_GROUP;


    /**
     * 已读回执消息 group
     */
    public static final String MQ_READ_RECEIPT_MESSAGE_GROUP =  "message_read_receipt";



    /**
     * 已读回执消息 topic
     */
    public static final String MQ_READ_RECEIPT_MESSAGE_TOPIC = MQ_READ_RECEIPT_MESSAGE_GROUP;


    /**
     * 好友请求（加入/同意/拒绝） group
     */
    public static final String MQ_FRIEND_REQUEST_GROUP =  "friend_request";



    /**
     * 好友请求（加入/同意/拒绝） topic
     */
    public static final String MQ_FRIEND_REQUEST_TOPIC = MQ_FRIEND_REQUEST_GROUP;



    /**
     * 群请求 group
     */
    public static final String MQ_GROUP_REQUEST_GROUP =  "group_request";


    /**
     * 群请求 topic
     */
    public static final String MQ_GROUP_REQUEST_TOPIC = MQ_GROUP_REQUEST_GROUP;

    /**
     * 归档翻译消费组。与 message_save 共用归档 topic，组名独立，避免改动 message_save 的位点。
     */
    public static final String MQ_TRANSLATE_MESSAGE_GROUP = "message_translate";

    /**
     * 译文落库队列。发送成功后才通知对端；消费者再写入 Mongo 与 MySQL。
     * 开发环境前缀后为 test_message_translate_persist。
     */
    public static final String MQ_TRANSLATE_PERSIST_GROUP = "message_translate_persist";

    public static final String MQ_TRANSLATE_PERSIST_TOPIC = MQ_TRANSLATE_PERSIST_GROUP;

    /**
     * 外渠归档恢复消费组：独立消费 SAVE/WITHDRAW/READ 归档，不与 message_save 位点耦合。
     */
    public static final String MQ_EXTERNAL_ARCHIVE_RELAY_GROUP = "external_archive_relay";

    /**
     * 外部渠道下行（WhatsApp / Telegram 等）group
     */
    public static final String MQ_EXTERNAL_CHANNEL_OUTBOUND_GROUP = "external_channel_outbound";

    /**
     * 外部渠道下行 topic，由外渠适配服务消费
     */
    public static final String MQ_EXTERNAL_CHANNEL_OUTBOUND_TOPIC = MQ_EXTERNAL_CHANNEL_OUTBOUND_GROUP;

    /**
     * 客服 ticket 活动通知（IM → CS），与 {@code CsKafkaTopics#TICKET_ACTIVITY} 一致。
     */
    public static final String MQ_CS_TICKET_ACTIVITY_GROUP = "cs_ticket_activity";

    public static final String MQ_CS_TICKET_ACTIVITY_TOPIC = MQ_CS_TICKET_ACTIVITY_GROUP;


    /**
     * 客服坐席 IM 通道关闭通知（IM → CS 踢技能池），与 {@code CsKafkaTopics#AGENT_PRESENCE} 一致。
     */
    public static final String MQ_CS_AGENT_PRESENCE_GROUP = "cs_agent_presence";

    public static final String MQ_CS_AGENT_PRESENCE_TOPIC = MQ_CS_AGENT_PRESENCE_GROUP;


    /**
     * 确认路径 topic（归档/撤回/已读/好友请求/群请求）要求 Kafka acks=all。
     */
    public static boolean requiresBrokerAcksAll(String topic) {
        String logical = MqDestination.logical(topic);
        return MQ_SAVE_MESSAGE_TOPIC.equals(logical)
                || MQ_WITHDRAW_MESSAGE_TOPIC.equals(logical)
                || MQ_READ_RECEIPT_MESSAGE_TOPIC.equals(logical)
                || MQ_FRIEND_REQUEST_TOPIC.equals(logical)
                || MQ_GROUP_REQUEST_TOPIC.equals(logical)
                || MQ_EXTERNAL_CHANNEL_OUTBOUND_TOPIC.equals(logical)
                || MQ_CS_TICKET_ACTIVITY_TOPIC.equals(logical)
                || MQ_TRANSLATE_PERSIST_TOPIC.equals(logical);
    }

}
