package com.ouyunc.mq.kafka;

import com.ouyunc.mq.core.MqHeaderKeys;
import com.ouyunc.base.constant.MqConstant;
import org.apache.kafka.clients.producer.ProducerConfig;
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
        return send(topic, null, payload, null);
    }

    @Override
    public CompletableFuture<?> send(String topic, String key, String payload) {
        return send(topic, key, payload, null);
    }

    @Override
    public CompletableFuture<?> send(String topic, String key, String payload, Map<String, Object> headers) {
        // extra 配置可能覆盖 typed ack；必须校验最终 ProducerFactory 配置。
        Object acks = kafkaTemplate.getProducerFactory().getConfigurationProperties().get(ProducerConfig.ACKS_CONFIG);
        if (MqConstant.MQ_SAVE_MESSAGE_TOPIC.equals(topic)
                && !MqConstant.KAFKA_ACKS_ALL.equals(String.valueOf(acks))
                && !MqConstant.KAFKA_ACKS_ALL_NUMERIC.equals(String.valueOf(acks))) {
            return CompletableFuture.failedFuture(new IllegalStateException("消息归档要求 Kafka acks=all 或 -1"));
        }
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
