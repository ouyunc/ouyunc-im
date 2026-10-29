package com.ouyunc.base.constant;

import java.util.Set;

/**
 * MQ topic 逻辑名。物理名由 {@link MqDestination#topic(String)} 加环境前缀并转成大写。
 * 消费组不在这里定义，由订阅者自己拼。
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

    /** 发送失败。 */
    public static final String MQ_MESSAGE_SEND_FAIL_TOPIC = "MESSAGE_SEND_FAIL";

    /** 异常隔离。 */
    public static final String MQ_EXCEPTION_TOPIC = "EXCEPTION";

    /** 消息归档。 */
    public static final String MQ_SAVE_MESSAGE_TOPIC = "MESSAGE_SAVE";

    /** 撤回。 */
    public static final String MQ_WITHDRAW_MESSAGE_TOPIC = "MESSAGE_WITHDRAW";

    /** 已读回执。 */
    public static final String MQ_READ_RECEIPT_MESSAGE_TOPIC = "MESSAGE_READ_RECEIPT";

    /** 好友请求（加入/同意/拒绝）。 */
    public static final String MQ_FRIEND_REQUEST_TOPIC = "FRIEND_REQUEST";

    /** 群请求。 */
    public static final String MQ_GROUP_REQUEST_TOPIC = "GROUP_REQUEST";

    /**
     * 译文落库。发送成功后才通知对端，消费者再写入 Mongo 与 MySQL。
     * 测试环境前缀后为 {@code TEST_MESSAGE_TRANSLATE_PERSIST}。
     */
    public static final String MQ_TRANSLATE_PERSIST_TOPIC = "MESSAGE_TRANSLATE_PERSIST";

    /** 外部渠道下行（WhatsApp / Telegram 等），由外渠适配服务消费。 */
    public static final String MQ_EXTERNAL_CHANNEL_OUTBOUND_TOPIC = "EXTERNAL_CHANNEL_OUTBOUND";

    /** 客服 ticket 活动通知（IM → CS）。 */
    public static final String MQ_CS_TICKET_ACTIVITY_TOPIC = "CS_TICKET_ACTIVITY";

    /** 客服坐席 IM 通道关闭通知（IM → CS 踢技能池）。 */
    public static final String MQ_CS_AGENT_PRESENCE_TOPIC = "CS_AGENT_PRESENCE";

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
