package com.ouyunc.repository.support;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.packet.Packet;
import org.apache.commons.lang3.StringUtils;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 消息 MQ 投递。
 */
public final class MessageMqPublisherSupport {

    private final RepositoryInfrastructure infra;

    public MessageMqPublisherSupport(RepositoryInfrastructure infra) {
        this.infra = infra;
    }

    public CompletableFuture<?> save(Packet packet) {
        return savePacket2Mq(MqConstant.KAFKA_SAVE_MESSAGE_TOPIC, null, packet);
    }

    public CompletableFuture<?> savePacket2Mq(String topic, String key, Packet packet) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(KafkaHeaders.CORRELATION_ID, packet.getPacketId());
        if (StringUtils.isNotBlank(key)) {
            headers.put(KafkaHeaders.KEY, key);
        }
        headers.put(KafkaHeaders.TOPIC, topic);
        return infra.kafkaTemplate.send(MessageBuilder.withPayload(JSON.toJSONString(packet)).copyHeadersIfAbsent(headers).build());
    }
}
