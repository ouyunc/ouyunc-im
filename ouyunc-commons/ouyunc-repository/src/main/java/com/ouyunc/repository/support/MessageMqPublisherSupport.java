package com.ouyunc.repository.support;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.mq.core.MqHeaderKeys;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 消息 MQ 投递：协议包与 JSON 共用发送实现。
 * <p>确认路径 {@link #publishPacketConfirmed}/{@link #save} 只等 broker ACK，失败交给客户端重试，不写 Outbox。
 * 旁路路径 {@link #publishPacketAsync}/{@link #publishJsonAsync} 失败仍入 MySQL Outbox。</p>
 */
public final class MessageMqPublisherSupport {

    private static final Logger log = LoggerFactory.getLogger(MessageMqPublisherSupport.class);

    private final RepositoryInfrastructure infra;
    private final MqOutboxSupport mqOutbox;

    public MessageMqPublisherSupport(RepositoryInfrastructure infra) {
        this.infra = infra;
        this.mqOutbox = new MqOutboxSupport(infra);
    }

    MessageMqPublisherSupport(RepositoryInfrastructure infra, MqOutboxSupport mqOutbox) {
        this.infra = infra;
        this.mqOutbox = mqOutbox;
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
     * 旁路异步投递协议包：不阻塞调用方；失败记日志、发异常事件并入 MySQL Outbox。
     */
    public void publishPacketAsync(String topic, String key, Packet packet, String failureContext) {
        publishPacket(topic, key, packet, failureContext);
    }

    /**
     * 确认投递：等 broker ACK，失败不写 Outbox（由客户端 QoS 重试）。
     * <p>调用线程先 {@link Packet#clone()}，JSON 与发送丢到仓库线程池，避免与后续 QoS {@code copyFrom} / 业务改包并发。</p>
     */
    public CompletableFuture<?> publishPacketConfirmed(String topic, String key, Packet packet) {
        if (packet == null) {
            return CompletableFuture.completedFuture(null);
        }
        Packet snapshot = packet.clone();
        CompletableFuture<Object> result = new CompletableFuture<>();
        try {
            infra.dbExecutor().execute(() -> {
                try {
                    sendPacket(topic, key, snapshot).whenComplete((value, ex) -> {
                        if (ex != null) {
                            log.warn("MQ 确认发送失败 topic={} packetId={}", topic, snapshot.getPacketId(), ex);
                            MessageContext.publishEvent(new MessageEvent(
                                    ExceptionEventPayload.of(ExceptionCodeEnum.MQ_PERSISTENCE_ERROR,
                                            "MQ 确认发送失败: " + ex.getMessage(), snapshot),
                                    MessageEventTypeEnum.EXCEPTION), true);
                            result.completeExceptionally(ex);
                        } else {
                            result.complete(value);
                        }
                    });
                } catch (Exception ex) {
                    result.completeExceptionally(ex);
                    log.error("MQ 确认执行异常 topic={} packetId={}", topic, snapshot.getPacketId(), ex);
                }
            });
        } catch (Exception ex) {
            return CompletableFuture.failedFuture(ex);
        }
        return result;
    }

    /**
     * 确认投递并切回业务线程：超时不撤销已发出的消息；消费者按 packetId 幂等。
     */
    public Mono<Void> confirmPacket(String topic, String key, Packet packet) {
        return Mono.defer(() -> Mono.fromFuture(publishPacketConfirmed(topic, key, packet), true))
                .timeout(Duration.ofMillis(MessageConstant.MESSAGE_ARCHIVE_CONFIRM_TIMEOUT_MS))
                .publishOn(Schedulers.fromExecutor(ThreadPoolManager.messageProcessorExecutor()))
                .then();
    }

    /**
     * 全量归档到 {@link MqConstant#MQ_SAVE_MESSAGE_TOPIC}，对应 {@link com.ouyunc.repository.Repository#save}。
     */
    public CompletableFuture<?> save(Packet packet) {
        return publishPacketConfirmed(MqConstant.MQ_SAVE_MESSAGE_TOPIC, null, packet);
    }

    /**
     * 发送协议包并挂失败回调；同步异常转为已完成的失败 Future。
     */
    private CompletableFuture<?> publishPacket(String topic, String key, Packet packet, String failureContext) {
        String payload = JSON.toJSONString(packet);
        try {
            CompletableFuture<?> future = sendPacket(topic, key, packet);
            attachFailure(future, topic, key, packet.getPacketId(), payload, packet, failureContext);
            return future;
        } catch (Exception ex) {
            handleFailure(topic, key, packet.getPacketId(), payload, packet, failureContext, ex);
            return CompletableFuture.failedFuture(ex);
        }
    }

    /**
     * 旁路异步投递 JSON 负载（客服活动、坐席 presence、外渠下行等）。
     */
    public void publishJsonAsync(String topic, String key, String jsonBody, String failureContext) {
        try {
            attachFailure(infra.mqPublisher.send(topic, key, jsonBody, null), topic, key, null, jsonBody, null, failureContext);
        } catch (Exception ex) {
            handleFailure(topic, key, null, jsonBody, null, failureContext, ex);
        }
    }

    /**
     * Future 完成后的失败回调。
     */
    private void attachFailure(CompletableFuture<?> future, String topic, String key,
                               Long packetId, String payload, Packet packet, String failureContext) {
        future.whenComplete((ignored, ex) -> {
            if (ex != null) {
                handleFailure(topic, key, packetId, payload, packet, failureContext, ex);
            }
        });
    }

    /**
     * Packet / JSON 发送失败：打 warn、发布异常事件，并把 MySQL Outbox 补偿提交到有界仓库池。
     */
    private void handleFailure(String topic, String key, Long packetId, String payload, Packet packet,
                               String failureContext, Throwable ex) {
        log.warn("MQ 旁路投递失败, topic={}, key={}, packetId={}, context={}, 原因: {}",
                topic, key, packetId, failureContext, ex.getMessage(), ex);
        MessageContext.publishEvent(new MessageEvent(
                ExceptionEventPayload.of(ExceptionCodeEnum.MQ_PERSISTENCE_ERROR,
                        failureContext + ": " + ex.getMessage(), packet),
                MessageEventTypeEnum.EXCEPTION), true);
        try {
            infra.dbExecutor().execute(() ->
                    mqOutbox.enqueueAsync(topic, key, packetId, payload, failureContext, ex.getMessage()));
        } catch (java.util.concurrent.RejectedExecutionException rejected) {
            log.error("Outbox 补偿提交被拒绝 topic={} packetId={}，归档未确认", topic, packetId, rejected);
        }
    }
}
