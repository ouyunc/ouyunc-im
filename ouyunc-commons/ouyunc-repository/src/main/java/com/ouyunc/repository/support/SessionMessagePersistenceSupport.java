package com.ouyunc.repository.support;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.QosLevelEnum;
import com.ouyunc.base.model.FiveConsumer;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.repository.SaveMessageOutcome;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializer;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 消息热 key + 会话 ZSet 索引（Redis Pipeline）。
 */
public final class SessionMessagePersistenceSupport {

    private static final Logger log = LoggerFactory.getLogger(SessionMessagePersistenceSupport.class);

    private final RepositoryInfrastructure infra;

    public SessionMessagePersistenceSupport(RepositoryInfrastructure infra) {
        this.infra = infra;
    }

    public Mono<Boolean> reactiveSaveMessage(Packet packet, String sessionId, long expireTime) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        return Mono.fromCallable(() -> saveMessageWithSession(packet, expireTime,
                CacheConstant.buildMessageCacheKey(metadata.getAppKey(), packet.getPacketId()),
                CacheConstant.buildSessionCacheKey(metadata.getAppKey(), sessionId),
                (ops) -> {
                }, (ops, msg, app, f, t) -> {
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.error("Reactive save message failed: {}", e.getMessage(), e);
                    return Mono.just(false);
                });
    }

    /**
     * 单聊/客服消息持久化，并在成功后对收件人维护 ur 未读 Hash。
     */
    public Mono<Boolean> reactiveSaveOne2OneMessage(Packet packet, String sessionId, long expireTime,
                                                    UnreadIndexSupport unreadIndexSupport) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        return Mono.fromCallable(() -> {
                    SaveMessageOutcome outcome = saveMessageWithSessionOutcome(packet, expireTime,
                            CacheConstant.buildMessageCacheKey(metadata.getAppKey(), packet.getPacketId()),
                            CacheConstant.buildSessionCacheKey(metadata.getAppKey(), sessionId),
                            (ops) -> {
                            }, (ops, msg, app, f, t) -> {
                            });
                    if (outcome == SaveMessageOutcome.SUCCESS && unreadIndexSupport != null) {
                        unreadIndexSupport.incrOne2OneOnMessage(packet);
                    }
                    return isSaveAccepted(outcome);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    log.error("Reactive save one2one message failed: {}", e.getMessage(), e);
                    return Mono.just(false);
                });
    }

    public boolean saveMessageWithSession(Packet packet, long expireTime, String messageKey, String sessionKey,
                                          Consumer<RedisConnection> consumer,
                                          FiveConsumer<RedisConnection, Message, String, String, String> extraOperation) {
        return isSaveAccepted(saveMessageWithSessionOutcome(packet, expireTime, messageKey, sessionKey, consumer, extraOperation));
    }

    @SuppressWarnings("unchecked")
    public SaveMessageOutcome saveMessageWithSessionOutcome(Packet packet, long expireTime, String messageKey, String sessionKey,
                                                            Consumer<RedisConnection> consumer,
                                                            FiveConsumer<RedisConnection, Message, String, String, String> extraOperation) {
        if (packet == null || infra.redisTemplate.getConnectionFactory() == null) {
            log.error("Packet 或 RedisConnectionFactory 为空");
            return SaveMessageOutcome.FAILED;
        }

        RedisConnectionFactory connectionFactory = infra.redisTemplate.getConnectionFactory();
        RedisSerializer<String> stringSerializer = infra.stringSerializer;
        RedisSerializer<Object> valueSerializer = infra.valueSerializer;

        try (RedisConnection conn = connectionFactory.getConnection()) {
            log.debug("获取 Redis 连接成功: {}", conn.hashCode());

            conn.openPipeline();
            log.debug("Pipeline 已开启");

            Message message = packet.getMessage();
            Metadata metadata = message != null ? message.getMetadata() : null;
            if (message == null || metadata == null) {
                log.error("消息或元数据为空");
                return SaveMessageOutcome.FAILED;
            }

            String appKey = metadata.getAppKey();
            String from = message.getFrom();
            String to = message.getTo();
            boolean qosSave = MessageContext.isQosEnable() && message.getQos() > QosLevelEnum.QOS_0.getLevel();
            if (qosSave && !QosIdempotencyHelper.tryClaim(infra.redisTemplate, appKey, packet.getPacketId(), from, message.getId())) {
                return SaveMessageOutcome.DUPLICATE;
            }
            String formatPacketId = MessageContext.idGenerator().formatLongId19Str(packet.getPacketId());

            byte[] packetIdBytes = serializeOrThrow(stringSerializer, formatPacketId, "PacketId");
            byte[] msgKeyBytes = serializeOrNull(stringSerializer, messageKey);
            byte[] packetBytes = serializeOrNull(valueSerializer, packet);

            if (msgKeyBytes != null && packetBytes != null) {
                conn.commands().set(msgKeyBytes, packetBytes);
                if (expireTime > 0) {
                    conn.keyCommands().pExpire(msgKeyBytes, expireTime);
                }
                log.debug("消息主体命令入队: {}", messageKey);
            } else {
                log.warn("消息主体序列化失败，跳过");
            }

            byte[] sessionKeyBytes = serializeOrNull(stringSerializer, sessionKey);
            if (sessionKeyBytes != null) {
                conn.zAdd(sessionKeyBytes, NumberConstant.NUMBER_0, packetIdBytes);
                long sessionZSetExpireMs = MessageConstant.CACHE_SESSION_LAST_MESSAGE_KEY_EXPIRE_TIMESTAMP;
                if (sessionZSetExpireMs > 0) {
                    conn.keyCommands().pExpire(sessionKeyBytes, sessionZSetExpireMs);
                }
                conn.zSetCommands().zRemRange(sessionKeyBytes, 0, -(MessageConstant.SESSION_ZSET_MAX_SIZE + 1L));
                log.debug("会话ZSet命令入队: {}", sessionKey);
            }

            if (extraOperation != null) {
                safelyExecute(() -> extraOperation.accept(conn, message, appKey, from, to), "额外操作");
            }

            if (consumer != null) {
                safelyExecute(() -> consumer.accept(conn), "自定义逻辑");
            }

            List<Object> results;
            try {
                results = conn.closePipeline();
                log.debug("Pipeline 关闭成功，结果数量: {}", results.size());
            } catch (Exception e) {
                log.error("Pipeline 执行失败: ", e);
                forceClosePipeline(conn);
                if (qosSave) {
                    QosIdempotencyHelper.releaseClaim(infra.redisTemplate, appKey, packet.getPacketId(), from, message.getId());
                }
                return SaveMessageOutcome.FAILED;
            }

            if (CollectionUtils.isEmpty(results)) {
                if (qosSave) {
                    QosIdempotencyHelper.releaseClaim(infra.redisTemplate, appKey, packet.getPacketId(), from, message.getId());
                }
                return SaveMessageOutcome.FAILED;
            }
            return SaveMessageOutcome.SUCCESS;

        } catch (Exception e) {
            log.error("Redis Pipeline 操作异常: ", e);
            try {
                Message message = packet != null ? packet.getMessage() : null;
                Metadata metadata = message != null ? message.getMetadata() : null;
                if (metadata != null && MessageContext.isQosEnable()
                        && message.getQos() > QosLevelEnum.QOS_0.getLevel()) {
                    QosIdempotencyHelper.releaseClaim(infra.redisTemplate, metadata.getAppKey(),
                            packet.getPacketId(), message.getFrom(), message.getId());
                }
            } catch (Exception ignored) {
                log.warn("释放 QoS 占位异常", ignored);
            }
            return SaveMessageOutcome.FAILED;
        }
    }

    @SuppressWarnings("unchecked")
    public void saveLastMessageForSession(String sessionId, Packet lastPacket, long expireTime, TimeUnit timeUnit) {
        infra.redisTemplate.opsForValue().set(
                CacheConstant.buildSessionLastMessageCacheKey(lastPacket.getMessage().getMetadata().getAppKey(), sessionId),
                lastPacket.getPacketId(), expireTime, timeUnit);
    }

    public static boolean isSaveAccepted(SaveMessageOutcome outcome) {
        return outcome == SaveMessageOutcome.SUCCESS || outcome == SaveMessageOutcome.DUPLICATE;
    }

    public <T> byte[] serializeOrNull(RedisSerializer<T> serializer, T value) {
        try {
            return serializer.serialize(value);
        } catch (Exception e) {
            log.warn("序列化失败: {}", value, e);
            return null;
        }
    }

    public <T> byte[] serializeOrThrow(RedisSerializer<T> serializer, T value, String fieldName) {
        byte[] bytes = serializeOrNull(serializer, value);
        if (bytes == null) {
            throw new IllegalArgumentException(fieldName + " 序列化失败: " + value);
        }
        return bytes;
    }

    private void safelyExecute(Runnable runnable, String opName) {
        try {
            runnable.run();
            log.debug("{} 执行完成", opName);
        } catch (Exception e) {
            log.error("{} 执行失败: {}", opName, e.getMessage(), e);
        }
    }

    private void forceClosePipeline(RedisConnection conn) {
        try {
            if (!conn.isClosed()) {
                conn.closePipeline();
            }
        } catch (Exception e) {
            log.error("强制关闭Pipeline失败", e);
        }
    }
}
