package com.ouyunc.mq.core;

/**
 * Broker 无关的消息 Header 键，由具体实现映射到 Kafka / RocketMQ 原生 Header。
 */
public final class MqHeaderKeys {

    public static final String CORRELATION_ID = "correlationId";

    public static final String MESSAGE_KEY = "messageKey";

    private MqHeaderKeys() {
    }
}
