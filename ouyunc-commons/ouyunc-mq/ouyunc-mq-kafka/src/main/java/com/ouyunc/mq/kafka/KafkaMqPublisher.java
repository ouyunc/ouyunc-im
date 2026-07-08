package com.ouyunc.mq.kafka;

import com.ouyunc.mq.core.MqHeaderKeys;
import com.ouyunc.mq.core.api.MqPublisher;
import org.apache.commons.lang3.StringUtils;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Kafka {@link MqPublisher} 实现。
 */
public class KafkaMqPublisher implements MqPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate = KafkaFactory.KAFKA_TEMPLATE.instance();

    @Override
    public CompletableFuture<?> send(String topic, String payload) {
        return kafkaTemplate.send(topic, payload);
    }

    @Override
    public CompletableFuture<?> send(String topic, String key, String payload) {
        if (StringUtils.isBlank(key)) {
            return send(topic, payload);
        }
        return kafkaTemplate.send(topic, key, payload);
    }

    @Override
    public CompletableFuture<?> send(String topic, String key, String payload, Map<String, Object> headers) {
        Map<String, Object> kafkaHeaders = new HashMap<>();
        if (headers != null) {
            headers.forEach((name, value) -> mapHeader(kafkaHeaders, name, value));
        }
        if (StringUtils.isNotBlank(key)) {
            kafkaHeaders.put(KafkaHeaders.KEY, key);
        }
        kafkaHeaders.put(KafkaHeaders.TOPIC, topic);
        return kafkaTemplate.send(MessageBuilder.withPayload(payload).copyHeadersIfAbsent(kafkaHeaders).build());
    }

    private static void mapHeader(Map<String, Object> kafkaHeaders, String name, Object value) {
        if (value == null) {
            return;
        }
        if (MqHeaderKeys.CORRELATION_ID.equals(name)) {
            kafkaHeaders.put(KafkaHeaders.CORRELATION_ID, value);
            return;
        }
        if (MqHeaderKeys.MESSAGE_KEY.equals(name)) {
            kafkaHeaders.put(KafkaHeaders.KEY, value);
            return;
        }
        kafkaHeaders.put(name, value);
    }
}
