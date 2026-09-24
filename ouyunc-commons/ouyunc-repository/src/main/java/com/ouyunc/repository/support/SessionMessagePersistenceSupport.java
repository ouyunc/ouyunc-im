package com.ouyunc.repository.support;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.LuaScriptEnum;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.model.FiveConsumer;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.QosClaimIdentities;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.repository.SaveMessageOutcome;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.script.DefaultRedisScript;
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

    public Mono<SaveMessageOutcome> reactiveSaveMessage(Packet packet, String sessionId, long expireTime) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        return Mono.fromCallable(() -> saveMessageWithSessionOutcome(packet, expireTime,
                CacheConstant.buildSessionCacheKey(metadata.getIngress().getAppKey(), sessionId),
                (ops) -> {
                }, (ops, msg, app, f, t) -> {
                }))
                .subscribeOn(Schedulers.fromExecutor(ThreadPoolManager.redisPersistenceExecutor()))
                .onErrorResume(e -> {
                    log.error("Reactive save message failed: {}", e.getMessage(), e);
                    return Mono.just(SaveMessageOutcome.FAILED);
                });
    }

    /**
     * 单聊/客服消息持久化，并在成功后对收件人维护 ur 未读 Hash。
     * <p>DUPLICATE 不累加未读、不视为新写入（未读重放使用已收敛的正式 packetId）；调用方只应 ACK，禁止二次扇出。</p>
     */
    public Mono<SaveMessageOutcome> reactiveSaveOne2OneMessage(Packet packet, String sessionId, long expireTime,
                                                    UnreadIndexSupport unreadIndexSupport) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        return Mono.fromCallable(() -> {
                    SaveMessageOutcome outcome = saveMessageWithSessionOutcome(packet, expireTime,
                            CacheConstant.buildSessionCacheKey(metadata.getIngress().getAppKey(), sessionId),
                            (ops) -> {
                            }, (ops, msg, app, f, t) -> {
                            });
                    // 未读 ZADD 幂等；SUCCESS/DUPLICATE 都重放。失败不得当受理成功，否则 ACK 后重试会被前置判重截住。
                    if ((outcome.isFreshWrite() || outcome.isDuplicate()) && unreadIndexSupport != null
                            && !unreadIndexSupport.incrOne2OneOnMessage(packet)) {
                        return SaveMessageOutcome.FAILED;
                    }
                    return outcome;
                })
                .subscribeOn(Schedulers.fromExecutor(ThreadPoolManager.redisPersistenceExecutor()))
                .onErrorResume(e -> {
                    log.error("Reactive save one2one message failed: {}", e.getMessage(), e);
                    return Mono.just(SaveMessageOutcome.FAILED);
                });
    }

    public boolean saveMessageWithSession(Packet packet, long expireTime, String sessionKey,
                                          Consumer<RedisConnection> consumer,
                                          FiveConsumer<RedisConnection, Message, String, String, String> extraOperation) {
        return isSaveAccepted(saveMessageWithSessionOutcome(packet, expireTime, sessionKey, consumer, extraOperation));
    }

    /**
     * 热 key + 会话索引 Pipeline 落库。QoS 消息先原子抢占 PENDING（packet 键 + 稳定 client 键），
     * Pipeline 成功后再 {@code COMMIT_SCRIPT} 提交为 COMMITTED；失败则 compare-and-delete 释放本次占位。
     * 返回 {@link SaveMessageOutcome#DUPLICATE} 仅可能来自已提交记录，占位（PENDING）绝不视为成功。
     *
     * <p>主体/会话键等必需字段必须在入队前序列化成功；{@code consumer}/{@code extraOperation}
     * 视为关键副作用（好友/群关系等），异常直接导致 FAILED，不可吞掉后仍 ACK。
     * Pipeline 只降低往返，不提供多命令事务回滚；closePipeline 异常或空结果一律失败。
     * {@code COMMIT} 被拒绝表示已失去占位，不得删除共享 canonical 热数据（接管方可能已写完）。
     *
     * <p>消息正文 key 由本方法在 QoS 认领并对齐 canonical packetId 之后生成，调用方不得提前传入，
     * 否则接管场景会把正文写到旧 packetId 的 key 上，而会话 ZSET 记录的是 canonical ID。</p>
     */
    @SuppressWarnings("unchecked")
    public SaveMessageOutcome saveMessageWithSessionOutcome(Packet packet, long expireTime, String sessionKey,
                                                            Consumer<RedisConnection> consumer,
                                                            FiveConsumer<RedisConnection, Message, String, String, String> extraOperation) {
        if (packet == null || infra.redisTemplate.getConnectionFactory() == null) {
            log.error("Packet 或 RedisConnectionFactory 为空");
            return SaveMessageOutcome.FAILED;
        }

        RedisConnectionFactory connectionFactory = infra.redisTemplate.getConnectionFactory();
        RedisSerializer<String> stringSerializer = infra.stringSerializer;
        RedisSerializer<Object> valueSerializer = infra.valueSerializer;
        boolean qosSave = false;
        String qosOwnerToken = null;
        String appKey = null;
        String qosClaimIdentity = null;
        String clientMessageId = null;
        long qosClaimKeyPacketId = 0L;

        try (RedisConnection conn = connectionFactory.getConnection()) {
            log.debug("获取 Redis 连接成功: {}", conn.hashCode());

            Message message = packet.getMessage();
            Metadata metadata = message != null ? message.getMetadata() : null;
            if (message == null || metadata == null) {
                log.error("消息或元数据为空");
                return SaveMessageOutcome.FAILED;
            }

            appKey = metadata.getIngress().getAppKey();
            String from = message.getFrom();
            String to = message.getTo();
            qosClaimIdentity = QosClaimIdentities.resolve(message);
            clientMessageId = message.getId();
            // 入站幂等是业务正确性，不再由下行 QoS 重传等级控制。
            qosSave = StringUtils.isNotBlank(clientMessageId);
            boolean alreadyClaimed = qosSave && StringUtils.isNotBlank(metadata.getQosClaim().getQosOwnerToken());
            qosOwnerToken = alreadyClaimed ? metadata.getQosClaim().getQosOwnerToken()
                    : (qosSave ? QosIdempotencyHelper.newOwnerToken() : null);
            if (qosSave && alreadyClaimed) {
                Long claimKey = metadata.getQosClaim().getQosClaimPacketId();
                qosClaimKeyPacketId = claimKey != null && claimKey > 0L ? claimKey : packet.getPacketId();
            } else if (qosSave) {
                // 写入 Metadata，供失败路径 releaseQosClaim 带回同一 owner（禁止传 null）
                metadata.getQosClaim().setQosOwnerToken(qosOwnerToken);
                // 占位键按抢占时的 packetId 固定；对齐 canonical 后 commit/release 仍按此键定位
                qosClaimKeyPacketId = packet.getPacketId();
                metadata.getQosClaim().setQosClaimPacketId(qosClaimKeyPacketId);
                QosIdempotencyHelper.ClaimResult claim = QosIdempotencyHelper.tryClaimResult(
                        infra.redisTemplate, appKey, qosClaimKeyPacketId,
                        qosClaimIdentity, clientMessageId, qosOwnerToken, message, packet.getMessageType());
                if (claim.state() == QosIdempotencyHelper.CLAIM_COMMITTED) {
                    // 客户端只认 messageId；服务端索引必须收敛到首次正式 packetId
                    if (!claim.isCommittedWithCanonical()) {
                        log.warn("QoS 已提交但缺少正式 packetId，拒绝按重复成功处理: appKey={} clientMessageId={}",
                                appKey, clientMessageId);
                        clearQosClaimMarks(metadata);
                        return SaveMessageOutcome.FAILED;
                    }
                    packet.setPacketId(claim.canonicalPacketId());
                    clearQosClaimMarks(metadata);
                    return SaveMessageOutcome.DUPLICATE;
                }
                if (claim.state() == QosIdempotencyHelper.CLAIM_CONFLICT) {
                    clearQosClaimMarks(metadata);
                    return SaveMessageOutcome.CONFLICT;
                }
                if (claim.state() != QosIdempotencyHelper.CLAIM_ACQUIRED) {
                    // PENDING / FAILED：占位未拿到，绝不能当作成功
                    clearQosClaimMarks(metadata);
                    return SaveMessageOutcome.FAILED;
                }
                // 接管僵死 PENDING 时复用首次服务端 ID，避免换 packetId 再写一条热消息
                if (claim.canonicalPacketId() > 0L) {
                    packet.setPacketId(claim.canonicalPacketId());
                }
            }

            // canonical 对齐之后再建正文 key，保证正文、会话 ZSET、ACK、归档用同一个 packetId
            String messageKey = CacheConstant.buildMessageCacheKey(appKey, packet.getPacketId());

            // 必需字段先序列化；失败直接上抛，避免只写索引、无主体后仍判定成功
            String formatPacketId = MessageContext.idGenerator().formatLongId19Str(packet.getPacketId());
            byte[] packetIdBytes = serializeOrThrow(stringSerializer, formatPacketId, "PacketId");
            byte[] msgKeyBytes = serializeOrThrow(stringSerializer, messageKey, "messageKey");
            byte[] packetBytes = serializeOrThrow(valueSerializer, packet, "packet");
            byte[] sessionKeyBytes = serializeOrThrow(stringSerializer, sessionKey, "sessionKey");

            conn.openPipeline();
            log.debug("Pipeline 已开启");

            conn.commands().set(msgKeyBytes, packetBytes);
            if (expireTime > 0) {
                conn.keyCommands().pExpire(msgKeyBytes, expireTime);
            }
            log.debug("消息主体命令入队: {}", messageKey);

            conn.zAdd(sessionKeyBytes, NumberConstant.NUMBER_0, packetIdBytes);
            long sessionZSetExpireMs = MessageConstant.CACHE_SESSION_LAST_MESSAGE_KEY_EXPIRE_TIMESTAMP;
            if (sessionZSetExpireMs > 0) {
                conn.keyCommands().pExpire(sessionKeyBytes, sessionZSetExpireMs);
            }
            conn.zSetCommands().zRemRange(sessionKeyBytes, 0, -(MessageConstant.SESSION_ZSET_MAX_SIZE + 1L));
            log.debug("会话ZSet命令入队: {}", sessionKey);

            // 关键副作用：失败必须让整次落库失败（好友/群关系等不能被吞掉）
            if (extraOperation != null) {
                extraOperation.accept(conn, message, appKey, from, to);
                log.debug("额外操作入队完成");
            }
            if (consumer != null) {
                consumer.accept(conn);
                log.debug("自定义逻辑入队完成");
            }

            List<Object> results;
            try {
                results = conn.closePipeline();
                log.debug("Pipeline 关闭成功，结果数量: {}", results == null ? 0 : results.size());
            } catch (Exception e) {
                log.error("Pipeline 执行失败: ", e);
                forceClosePipeline(conn);
                releaseQosClaimQuietly(qosSave, appKey, qosClaimKeyPacketId, packet.getPacketId(), qosClaimIdentity,
                        clientMessageId, qosOwnerToken, metadata);
                return SaveMessageOutcome.FAILED;
            }

            if (CollectionUtils.isEmpty(results)) {
                releaseQosClaimQuietly(qosSave, appKey, qosClaimKeyPacketId, packet.getPacketId(), qosClaimIdentity,
                        clientMessageId, qosOwnerToken, metadata);
                return SaveMessageOutcome.FAILED;
            }
            QosIdempotencyHelper.CommitOutcome commitOutcome = qosSave
                    ? QosIdempotencyHelper.commit(infra.redisTemplate, appKey, qosClaimKeyPacketId,
                    packet.getPacketId(), qosClaimIdentity, clientMessageId, qosOwnerToken, message,
                    packet.getMessageType())
                    : QosIdempotencyHelper.CommitOutcome.COMMITTED;
            if (commitOutcome == QosIdempotencyHelper.CommitOutcome.UNKNOWN) {
                int verifiedState = QosIdempotencyHelper.checkState(
                        infra.redisTemplate, packet, qosClaimIdentity);
                if (verifiedState == QosIdempotencyHelper.CLAIM_COMMITTED) {
                    commitOutcome = QosIdempotencyHelper.CommitOutcome.COMMITTED;
                } else {
                    log.warn("QoS 提交结果未知，保留热写和占位等待重试核对: appKey={} packetId={} state={}",
                            appKey, packet.getPacketId(), verifiedState);
                    // 不能删除热数据或释放占位：Redis 可能已经提交成功但响应丢失。
                    clearQosClaimMarks(metadata);
                    return SaveMessageOutcome.FAILED;
                }
            }
            if (commitOutcome == QosIdempotencyHelper.CommitOutcome.REJECTED) {
                // REJECTED = 已失去幂等所有权（被接管 / 他人 PENDING / 已 COMMITTED）。
                // 禁止删 canonical 正文和会话成员：接管方可能已用同一 packetId 写完热数据。
                // 只 compare-and-delete 自己的 PENDING；热写留给当前 owner 覆盖或 TTL。
                log.warn("QoS 提交被拒绝，保留热写以免误删接管方数据: appKey={} packetId={}",
                        appKey, packet.getPacketId());
                releaseQosClaimQuietly(true, appKey, qosClaimKeyPacketId, packet.getPacketId(), qosClaimIdentity,
                        clientMessageId, qosOwnerToken, metadata);
                return SaveMessageOutcome.FAILED;
            }
            if (qosSave && metadata != null) {
                metadata.getQosClaim().setQosOwnerToken(null);
                metadata.getQosClaim().setQosClaimPacketId(null);
            }
            return SaveMessageOutcome.SUCCESS;

        } catch (Exception e) {
            log.error("Redis Pipeline 操作异常: ", e);
            releaseQosClaimQuietly(qosSave, appKey, qosClaimKeyPacketId, packet.getPacketId(), qosClaimIdentity,
                    clientMessageId, qosOwnerToken, metadataFromPacket(packet));
            return SaveMessageOutcome.FAILED;
        }
    }

    @SuppressWarnings("unchecked")
    public void saveLastMessageForSession(String sessionId, Packet lastPacket, long expireTime, TimeUnit timeUnit) {
        if (lastPacket == null || lastPacket.getMessage() == null || lastPacket.getMessage().getMetadata() == null
                || StringUtils.isBlank(sessionId)) {
            return;
        }
        String appKey = lastPacket.getMessage().getMetadata().getIngress().getAppKey();
        if (StringUtils.isBlank(appKey) || lastPacket.getPacketId() <= 0L) {
            return;
        }
        // 字符串 max-merge，并发旧消息晚完成不会覆盖更新的指针
        String lmKey = CacheConstant.buildSessionLastMessageCacheKey(appKey, sessionId);
        long ttlMs = timeUnit.toMillis(expireTime);
        DefaultRedisScript<String> script = new DefaultRedisScript<>(
                LuaScriptEnum.SESSION_LM_MAX_SCRIPT.getScript(), String.class);
        infra.stringRedisTemplate.execute(script, List.of(lmKey),
                String.valueOf(lastPacket.getPacketId()), String.valueOf(ttlMs));
    }

    /**
     * 好友/群申请等「写入即定性」路径：DUPLICATE 可当成功。
     * 聊天投递必须使用 {@link SaveMessageOutcome#isFreshWrite()}，禁止把 DUPLICATE 当新消息扇出。
     */
    public static boolean isSaveAccepted(SaveMessageOutcome outcome) {
        return outcome == SaveMessageOutcome.SUCCESS || outcome == SaveMessageOutcome.DUPLICATE;
    }

    /**
     * 非关键路径可用（请求会话草稿等）；消息主体落库必须用 {@link #serializeOrThrow}。
     */
    public <T> byte[] serializeOrNull(RedisSerializer<T> serializer, T value) {
        try {
            return serializer.serialize(value);
        } catch (Exception e) {
            log.warn("序列化失败: {}", value, e);
            return null;
        }
    }

    /**
     * 必需字段序列化：失败上抛，由落库入口转为 {@link SaveMessageOutcome#FAILED}，禁止跳过写入。
     */
    public <T> byte[] serializeOrThrow(RedisSerializer<T> serializer, T value, String fieldName) {
        try {
            byte[] bytes = serializer.serialize(value);
            if (bytes == null) {
                throw new IllegalArgumentException(fieldName + " 序列化结果为空: " + value);
            }
            return bytes;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(fieldName + " 序列化失败: " + value, e);
        }
    }

    private static void clearQosClaimMarks(Metadata metadata) {
        if (metadata != null) {
            metadata.getQosClaim().setQosOwnerToken(null);
            metadata.getQosClaim().setQosClaimPacketId(null);
        }
    }

    private static Metadata metadataFromPacket(Packet packet) {
        if (packet == null || packet.getMessage() == null) {
            return null;
        }
        return packet.getMessage().getMetadata();
    }

    private void releaseQosClaimQuietly(boolean qosSave, String appKey, long claimKeyPacketId, long recordPacketId,
                                        String qosClaimIdentity, String clientMessageId, String qosOwnerToken,
                                        Metadata metadata) {
        if (!qosSave || StringUtils.isBlank(appKey) || StringUtils.isBlank(qosOwnerToken)) {
            return;
        }
        if (metadata != null && metadata.getQosClaim().isQosArchiveBound()) {
            return;
        }
        long keyPacketId = claimKeyPacketId > 0L ? claimKeyPacketId : recordPacketId;
        try {
            QosIdempotencyHelper.releaseClaim(infra.redisTemplate, appKey, keyPacketId, recordPacketId,
                    qosClaimIdentity, clientMessageId, qosOwnerToken);
            if (metadata != null) {
                metadata.getQosClaim().setQosOwnerToken(null);
                metadata.getQosClaim().setQosClaimPacketId(null);
            }
        } catch (Exception e) {
            log.warn("释放 QoS 占位异常 claimKeyPacketId={} packetId={}", keyPacketId, recordPacketId, e);
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
