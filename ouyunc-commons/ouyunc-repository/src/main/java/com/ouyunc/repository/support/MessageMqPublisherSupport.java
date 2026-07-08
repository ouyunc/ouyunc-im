package com.ouyunc.repository.support;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.mq.core.MqHeaderKeys;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 消息 MQ 投递。
 */
public final class MessageMqPublisherSupport {

    private static final Logger log = LoggerFactory.getLogger(MessageMqPublisherSupport.class);

    private final RepositoryInfrastructure infra;

    public MessageMqPublisherSupport(RepositoryInfrastructure infra) {
        this.infra = infra;
    }

    public CompletableFuture<?> save(Packet packet) {
        return savePacket2Mq(MqConstant.MQ_SAVE_MESSAGE_TOPIC, null, packet);
    }

    public CompletableFuture<?> savePacket2Mq(String topic, String key, Packet packet) {
        Map<String, Object> headers = new HashMap<>();
        headers.put(MqHeaderKeys.CORRELATION_ID, packet.getPacketId());
        if (StringUtils.isNotBlank(key)) {
            headers.put(MqHeaderKeys.MESSAGE_KEY, key);
        }
        return infra.mqPublisher.send(topic, key, JSON.toJSONString(packet), headers);
    }

    /**
     * 异步旁路投递 MQ：不阻塞调用方；失败时记录日志并发布异常事件。
     */
    public void publishPacketAsync(String topic, String key, Packet packet, String failureContext) {
        savePacket2Mq(topic, key, packet).whenComplete((ignored, ex) -> {
            if (ex != null) {
                log.warn("MQ 旁路投递失败, topic={}, packetId={}, context={}, 原因: {}",
                        topic, packet.getPacketId(), failureContext, ex.getMessage(), ex);
                MessageContext.publishEvent(new MessageEvent(
                        ExceptionEventPayload.of(ExceptionCodeEnum.MQ_PERSISTENCE_ERROR,
                                failureContext + ": " + ex.getMessage(), packet),
                        MessageEventTypeEnum.EXCEPTION), true);
            }
        });
    }

    public void publishArchiveAsync(Packet packet) {
        publishPacketAsync(MqConstant.MQ_SAVE_MESSAGE_TOPIC, null, packet, "异步归档消息到 MQ");
    }

    public void publishJsonAsync(String topic, String key, String jsonBody, String failureContext) {
        infra.mqPublisher.send(topic, key, jsonBody, null).whenComplete((ignored, ex) -> {
            if (ex != null) {
                log.warn("MQ JSON 投递失败, topic={}, key={}, context={}, 原因: {}",
                        topic, key, failureContext, ex.getMessage(), ex);
            }
        });
    }
}