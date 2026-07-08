package com.ouyunc.mq.core.api;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 消息队列统一发送接口，屏蔽 Kafka / RocketMQ 差异。
 */
public interface MqPublisher {

    CompletableFuture<?> send(String topic, String payload);

    CompletableFuture<?> send(String topic, String key, String payload);

    CompletableFuture<?> send(String topic, String key, String payload, Map<String, Object> headers);
}
