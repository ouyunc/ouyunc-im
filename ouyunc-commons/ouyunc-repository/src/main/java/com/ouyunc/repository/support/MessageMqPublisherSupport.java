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
 * 消息 MQ 投递：协议包与 JSON 共用同一套发送与失败回调，对外只保留旁路异步方法。
 */
public final class MessageMqPublisherSupport {

    private static final Logger log = LoggerFactory.getLogger(MessageMqPublisherSupport.class);

    /** 全量归档失败上下文，与历史日志保持一致。 */
    private static final String ARCHIVE_FAILURE_CONTEXT = "异步归档消息到 MQ";

    private final RepositoryInfrastructure infra;

    public MessageMqPublisherSupport(RepositoryInfrastructure infra) {
        this.infra = infra;
    }

    /**
     * 发送协议包。headers 固定带 packetId；有 key 时再带 MESSAGE_KEY。
     */
    private CompletableFuture<?> sendPacket(String topic, String key, Packet packet) {
        Map<String, Object> headers = new HashMap<>(2);
        headers.put(MqHeaderKeys.CORRELATION_ID, packet.getPacketId());
        if (StringUtils.isNotBlank(key)) {
            headers.put(MqHeaderKeys.MESSAGE_KEY, key);
        }
        return infra.mqPublisher.send(topic, key, JSON.toJSONString(packet), headers);
    }

    /**
     * 旁路异步投递协议包：不阻塞调用方；失败记日志并发布异常事件。
     */
    public void publishPacketAsync(String topic, String key, Packet packet, String failureContext) {
        publishPacket(topic, key, packet, failureContext);
    }

    /**
     * 全量归档到 {@link MqConstant#MQ_SAVE_MESSAGE_TOPIC}，对应 {@link com.ouyunc.repository.Repository#save}。
     * <p>调用线程先 {@link Packet#clone()}，再把 JSON 序列化丢到仓库线程池，避免与后续 QoS {@code copyFrom} / 业务改包并发。</p>
     */
    public CompletableFuture<?> save(Packet packet) {
        if (packet == null) {
            return CompletableFuture.completedFuture(null);
        }
        Packet snapshot = packet.clone();
        CompletableFuture<Object> result = new CompletableFuture<>();
        try {
            infra.dbExecutor().execute(() -> publishPacket(MqConstant.MQ_SAVE_MESSAGE_TOPIC, null, snapshot,
                    ARCHIVE_FAILURE_CONTEXT).whenComplete((value, ex) -> {
                if (ex != null) {
                    result.completeExceptionally(ex);
                } else {
                    result.complete(value);
                }
            }));
        } catch (Exception ex) {
            handleFailure(MqConstant.MQ_SAVE_MESSAGE_TOPIC, null, snapshot.getPacketId(), snapshot,
                    ARCHIVE_FAILURE_CONTEXT, ex);
            return CompletableFuture.failedFuture(ex);
        }
        return result;
    }

    /**
     * 发送协议包并挂失败回调；同步异常转为已完成的失败 Future。
     */
    private CompletableFuture<?> publishPacket(String topic, String key, Packet packet, String failureContext) {
        try {
            CompletableFuture<?> future = sendPacket(topic, key, packet);
            attachFailure(future, topic, key, packet.getPacketId(), packet, failureContext);
            return future;
        } catch (Exception ex) {
            handleFailure(topic, key, packet.getPacketId(), packet, failureContext, ex);
            return CompletableFuture.failedFuture(ex);
        }
    }

    /**
     * 旁路异步投递 JSON 负载（客服活动、坐席 presence、外渠下行等）。
     */
    public void publishJsonAsync(String topic, String key, String jsonBody, String failureContext) {
        try {
            attachFailure(infra.mqPublisher.send(topic, key, jsonBody, null), topic, key, null, null, failureContext);
        } catch (Exception ex) {
            handleFailure(topic, key, null, null, failureContext, ex);
        }
    }

    /**
     * Future 完成后的失败回调。
     */
    private void attachFailure(CompletableFuture<?> future, String topic, String key,
                               Long packetId, Packet packet, String failureContext) {
        future.whenComplete((ignored, ex) -> {
            if (ex != null) {
                handleFailure(topic, key, packetId, packet, failureContext, ex);
            }
        });
    }

    /**
     * Packet / JSON 发送失败：打 warn 并发布 {@link ExceptionCodeEnum#MQ_PERSISTENCE_ERROR}。
     */
    private void handleFailure(String topic, String key, Long packetId, Packet packet,
                               String failureContext, Throwable ex) {
        log.warn("MQ 旁路投递失败, topic={}, key={}, packetId={}, context={}, 原因: {}",
                topic, key, packetId, failureContext, ex.getMessage(), ex);
        MessageContext.publishEvent(new MessageEvent(
                ExceptionEventPayload.of(ExceptionCodeEnum.MQ_PERSISTENCE_ERROR,
                        failureContext + ": " + ex.getMessage(), packet),
                MessageEventTypeEnum.EXCEPTION), true);
    }
}
