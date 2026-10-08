package com.ouyunc.repository.support;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqArchiveRouting;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.LuaScriptEnum;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.model.FiveConsumer;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.QosClaimIdentities;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.base.constant.enums.SaveMessageOutcomeEnum;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.types.Expiration;
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

    public Mono<SaveMessageOutcomeEnum> reactiveSaveMessage(Packet packet, String sessionId, long expireTime) {
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
                    return Mono.just(SaveMessageOutcomeEnum.FAILED);
                });
    }

    /**
     * 单聊/客服消息持久化，并在成功后对收件人维护 ur 未读 Hash。
     * <p>SUCCESS/DUPLICATE 均按正式正文幂等补未读；后续由完成器判断是否还需投递。</p>
     */
    public Mono<SaveMessageOutcomeEnum> reactiveSaveOne2OneMessage(Packet packet, String sessionId, long expireTime,
                                                                   UnreadIndexSupport unreadIndexSupport) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        return Mono.fromCallable(() -> {
                    SaveMessageOutcomeEnum outcome = saveMessageWithSessionOutcome(packet, expireTime,
                            CacheConstant.buildSessionCacheKey(metadata.getIngress().getAppKey(), sessionId),
                            (ops) -> {
                            }, (ops, msg, app, f, t) -> {
                            });
                    // 未读 ZADD 幂等；SUCCESS/DUPLICATE 都重放。失败不得当受理成功，否则 ACK 后重试会被前置判重截住。
                    if ((outcome.isFreshWrite() || outcome.isDuplicate()) && unreadIndexSupport != null
                            && !unreadIndexSupport.incrOne2OneOnMessage(packet)) {
                        return SaveMessageOutcomeEnum.FAILED;
                    }
                    return outcome;
                })
                .subscribeOn(Schedulers.fromExecutor(ThreadPoolManager.redisPersistenceExecutor()))
                .onErrorResume(e -> {
                    log.error("Reactive save one2one message failed: {}", e.getMessage(), e);
                    return Mono.just(SaveMessageOutcomeEnum.FAILED);
                });
    }

    public boolean saveMessageWithSession(Packet packet, long expireTime, String sessionKey,
                                          Consumer<RedisConnection> consumer,
                                          FiveConsumer<RedisConnection, Message, String, String, String> extraOperation) {
        return isSaveAccepted(saveMessageWithSessionOutcome(packet, expireTime, sessionKey, consumer, extraOperation));
    }

    /**
     * 热 key + 会话索引 Pipeline 落库。QoS 消息先按登录身份 + client messageId 原子抢占 PENDING，
     * Pipeline 成功后再 {@code COMMIT_SCRIPT} 提交为 COMMITTED；失败则 compare-and-delete 释放本次占位。
     * 返回 {@link SaveMessageOutcomeEnum#DUPLICATE} 仅可能来自已提交记录，占位（PENDING）绝不视为成功。
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
    public SaveMessageOutcomeEnum saveMessageWithSessionOutcome(Packet packet, long expireTime, String sessionKey,
                                                                Consumer<RedisConnection> consumer,
                                                                FiveConsumer<RedisConnection, Message, String, String, String> extraOperation) {
        if (packet == null || infra.redisTemplate.getConnectionFactory() == null) {
            log.error("Packet 或 RedisConnectionFactory 为空");
            return SaveMessageOutcomeEnum.FAILED;
        }

        RedisConnectionFactory connectionFactory = infra.redisTemplate.getConnectionFactory();
        RedisSerializer<String> stringSerializer = infra.stringSerializer;
        boolean qosSave = false;
        String qosOwnerToken = null;
        String appKey = null;
        String qosClaimIdentity = null;
        String clientMessageId = null;

        try (RedisConnection conn = connectionFactory.getConnection()) {
            log.debug("获取 Redis 连接成功: {}", conn.hashCode());

            Message message = packet.getMessage();
            Metadata metadata = message != null ? message.getMetadata() : null;
            if (message == null || metadata == null) {
                log.error("消息或元数据为空");
                return SaveMessageOutcomeEnum.FAILED;
            }

            // 请求命令依赖首次 Packet 中的 RequestEventContext 恢复；正文必须活过 QoS 判重窗口。
            if (MqArchiveRouting.isFriendRequestType(packet.getMessageType())
                    || MqArchiveRouting.isGroupRequestType(packet.getMessageType())) {
                expireTime = Math.max(expireTime, MessageContext.messageRecoveryTtlMillis());
            }

            appKey = metadata.getIngress().getAppKey();
            String from = message.getFrom();
            String to = message.getTo();
            qosClaimIdentity = QosClaimIdentities.resolve(message);
            clientMessageId = message.getId();
            // 入站幂等是业务正确性，不再由下行 QoS 重传等级控制。
            qosSave = StringUtils.isNotBlank(clientMessageId);
            if (qosSave && StringUtils.isBlank(qosClaimIdentity)) {
                log.error("QoS 消息缺少认证身份，拒绝建立不稳定的 packetId 占位: appKey={} messageId={}",
                        appKey, clientMessageId);
                return SaveMessageOutcomeEnum.FAILED;
            }
            boolean alreadyClaimed = qosSave && StringUtils.isNotBlank(metadata.getQosClaim().getQosOwnerToken());
            qosOwnerToken = alreadyClaimed ? metadata.getQosClaim().getQosOwnerToken()
                    : (qosSave ? QosIdempotencyHelper.newOwnerToken() : null);
            if (qosSave && !alreadyClaimed) {
                // 写入 Metadata，供失败路径 releaseQosClaim 带回同一 owner（禁止传 null）
                metadata.ensureQosClaim().setQosOwnerToken(qosOwnerToken);
                QosIdempotencyHelper.ClaimResult claim = QosIdempotencyHelper.tryClaimResult(
                        infra.redisTemplate, appKey, packet.getPacketId(),
                        qosClaimIdentity, clientMessageId, qosOwnerToken, message, packet.getMessageType());
                if (claim.state() == QosIdempotencyHelper.CLAIM_COMMITTED) {
                    // 客户端只认 messageId；服务端索引必须收敛到首次正式 packetId
                    if (!claim.isCommittedWithCanonical()) {
                        log.warn("QoS 已提交但缺少正式 packetId，拒绝按重复成功处理: appKey={} clientMessageId={}",
                                appKey, clientMessageId);
                        clearQosClaimMarks(metadata);
                        return SaveMessageOutcomeEnum.FAILED;
                    }
                    packet.setPacketId(claim.canonicalPacketId());
                    clearQosClaimMarks(metadata);
                    // 并发请求可能在热写入口才命中 COMMITTED，不能绕过正式正文恢复。
                    // 此处只读：正文暂不可用时返回失败，禁止用本次重试包重新创建已提交正文。
                    restoreStoredMessage(conn, packet, serializeOrThrow(stringSerializer,
                            CacheConstant.buildMessageCacheKey(appKey, packet.getPacketId()), "messageKey"));
                    return SaveMessageOutcomeEnum.DUPLICATE;
                }
                if (claim.state() == QosIdempotencyHelper.CLAIM_CONFLICT) {
                    clearQosClaimMarks(metadata);
                    return SaveMessageOutcomeEnum.CONFLICT;
                }
                if (claim.state() != QosIdempotencyHelper.CLAIM_ACQUIRED) {
                    // PENDING / FAILED：占位未拿到，绝不能当作成功
                    clearQosClaimMarks(metadata);
                    return SaveMessageOutcomeEnum.FAILED;
                }
                // 接管僵死 PENDING 时复用首次服务端 ID，避免换 packetId 再写一条热消息
                if (claim.canonicalPacketId() > 0L) {
                    packet.setPacketId(claim.canonicalPacketId());
                }
            }

            // 正文首次写入后不可被迟到的旧 owner 覆盖；重入复用已存正文继续维护派生索引。
            // 独立于 Pipeline 执行，以便后续索引和投递使用同一份正文，而不是本次重试包。
            retainFirstMessage(conn, packet, expireTime);
            message = packet.getMessage();
            metadata = message.getMetadata();

            // 必需字段先序列化；失败直接上抛，避免只写索引、无主体后仍判定成功
            String formatPacketId = MessageContext.idGenerator().formatLongId19Str(packet.getPacketId());
            byte[] packetIdBytes = serializeOrThrow(stringSerializer, formatPacketId, "PacketId");
            byte[] sessionKeyBytes = serializeOrThrow(stringSerializer, sessionKey, "sessionKey");

            conn.openPipeline();
            log.debug("Pipeline 已开启");

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
                releaseQosClaimQuietly(qosSave, appKey, packet.getPacketId(), qosClaimIdentity,
                        clientMessageId, qosOwnerToken, metadata);
                return SaveMessageOutcomeEnum.FAILED;
            }

            if (CollectionUtils.isEmpty(results)) {
                releaseQosClaimQuietly(qosSave, appKey, packet.getPacketId(), qosClaimIdentity,
                        clientMessageId, qosOwnerToken, metadata);
                return SaveMessageOutcomeEnum.FAILED;
            }
            QosIdempotencyHelper.CommitOutcome commitOutcome = qosSave
                    ? QosIdempotencyHelper.commit(infra.redisTemplate, appKey, packet.getPacketId(),
                    qosClaimIdentity, clientMessageId, qosOwnerToken, message,
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
                    return SaveMessageOutcomeEnum.FAILED;
                }
            }
            if (commitOutcome == QosIdempotencyHelper.CommitOutcome.REJECTED) {
                // REJECTED = 已失去幂等所有权（被接管 / 他人 PENDING / 已 COMMITTED）。
                // 禁止删 canonical 正文和会话成员：接管方可能已用同一 packetId 写完热数据。
                // 只 compare-and-delete 自己的 PENDING；首次正文保留供当前 owner 复用。
                log.warn("QoS 提交被拒绝，保留热写以免误删接管方数据: appKey={} packetId={}",
                        appKey, packet.getPacketId());
                releaseQosClaimQuietly(true, appKey, packet.getPacketId(), qosClaimIdentity,
                        clientMessageId, qosOwnerToken, metadata);
                return SaveMessageOutcomeEnum.FAILED;
            }
            if (qosSave && metadata != null) {
                metadata.ensureQosClaim().setQosOwnerToken(null);
            }
            return SaveMessageOutcomeEnum.SUCCESS;

        } catch (Exception e) {
            log.error("Redis Pipeline 操作异常: ", e);
            releaseQosClaimQuietly(qosSave, appKey, packet.getPacketId(), qosClaimIdentity,
                    clientMessageId, qosOwnerToken, metadataFromPacket(packet));
            return SaveMessageOutcomeEnum.FAILED;
        }
    }

    /**
     * 同一 packetId 的热正文只允许首次写入，避免超时接管后旧请求恢复并覆盖已提交数据。
     * 首次写入仍是一条 SET NX PX，不增加持久化键；仅重入命中时读取原正文。
     * 正文已存在不代表业务已提交，调用方仍须完成索引写入和 owner 校验，不能直接 ACK。
     */
    private void retainFirstMessage(RedisConnection conn, Packet packet, long expireTime) {
        Message incoming = packet.getMessage();
        Metadata currentMetadata = incoming.getMetadata();
        String appKey = currentMetadata.getIngress().getAppKey();
        byte[] key = serializeOrThrow(infra.stringSerializer,
                CacheConstant.buildMessageCacheKey(appKey, packet.getPacketId()), "messageKey");
        byte[] value = serializeOrThrow(infra.valueSerializer, packet, "packet");
        Boolean inserted = conn.stringCommands().set(key, value,
                expireTime > 0 ? Expiration.milliseconds(expireTime) : Expiration.persistent(),
                RedisStringCommands.SetOption.SET_IF_ABSENT);
        if (Boolean.TRUE.equals(inserted)) {
            return;
        }
        restoreStoredMessage(conn, packet, key);
    }

    /** 热写碰撞及 COMMITTED 提前返回共用；只读恢复正式正文，不补写、不延长 TTL。 */
    private void restoreStoredMessage(RedisConnection conn, Packet packet, byte[] key) {
        Message incoming = packet.getMessage();
        Metadata currentMetadata = incoming.getMetadata();
        String appKey = currentMetadata.getIngress().getAppKey();
        byte[] existing = conn.stringCommands().get(key);
        Object decoded = existing == null ? null : infra.valueSerializer.deserialize(existing);
        if (!(decoded instanceof Packet stored) || stored.getPacketId() != packet.getPacketId()
                || stored.getMessageType() != packet.getMessageType() || stored.getMessage() == null) {
            throw new IllegalStateException("首次消息正文暂不可用或身份不匹配");
        }
        Message original = stored.getMessage();
        Metadata originalMetadata = original.getMetadata();
        // 会话 key 由调用方生成，身份/会话变化时禁止用旧正文写入另一个会话索引。
        if (originalMetadata == null
                || !StringUtils.equals(appKey, originalMetadata.getIngress().getAppKey())
                || !StringUtils.equals(incoming.getId(), original.getId())
                || !StringUtils.equals(incoming.getFrom(), original.getFrom())
                || !StringUtils.equals(incoming.getTo(), original.getTo())
                || !StringUtils.equals(incoming.getCorrelationId(), original.getCorrelationId())) {
            throw new IllegalStateException("首次消息正文与当前会话不匹配");
        }
        // owner 属于本轮执行，不能从缓存恢复旧令牌；其余正式正文和发送端属性沿用首次值。
        originalMetadata.setQosClaim(currentMetadata.getQosClaim());
        packet.setMessage(original);
        packet.setDeviceType(stored.getDeviceType());
        // 已撤回的缓存不能在重入时变回可见消息。
        packet.setRetain(stored.getRetain());
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
     * 聊天投递必须使用 {@link SaveMessageOutcomeEnum#isFreshWrite()}，禁止把 DUPLICATE 当新消息扇出。
     */
    public static boolean isSaveAccepted(SaveMessageOutcomeEnum outcome) {
        return outcome == SaveMessageOutcomeEnum.SUCCESS || outcome == SaveMessageOutcomeEnum.DUPLICATE;
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
     * 必需字段序列化：失败上抛，由落库入口转为 {@link SaveMessageOutcomeEnum#FAILED}，禁止跳过写入。
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
            metadata.ensureQosClaim().setQosOwnerToken(null);
        }
    }

    private static Metadata metadataFromPacket(Packet packet) {
        if (packet == null || packet.getMessage() == null) {
            return null;
        }
        return packet.getMessage().getMetadata();
    }

    private void releaseQosClaimQuietly(boolean qosSave, String appKey, long packetId,
                                        String qosClaimIdentity, String clientMessageId, String qosOwnerToken,
                                        Metadata metadata) {
        if (!qosSave || StringUtils.isBlank(appKey) || StringUtils.isBlank(qosOwnerToken)) {
            return;
        }
        if (metadata != null && metadata.getQosClaim().isQosArchiveBound()) {
            return;
        }
        try {
            QosIdempotencyHelper.releaseClaim(infra.redisTemplate, appKey, packetId,
                    qosClaimIdentity, clientMessageId, qosOwnerToken);
            if (metadata != null) {
                metadata.ensureQosClaim().setQosOwnerToken(null);
            }
        } catch (Exception e) {
            log.warn("释放 QoS 占位异常 packetId={}", packetId, e);
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
