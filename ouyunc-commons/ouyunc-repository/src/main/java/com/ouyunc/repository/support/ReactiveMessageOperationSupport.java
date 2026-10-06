package com.ouyunc.repository.support;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.ArchiveClaimEnum;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.model.MessageOperationCheckpoint;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.utils.QosClaimIdentities;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.exception.ExceptionReporter;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 控制操作恢复链：稳定消息身份 → 已校验快照 → MQ 确认 → Redis 副作用 → 幂等提交 → 通知完成。
 * 首次快照必须在任何副作用前保存。失败保留快照和 canonical ID，重试不会把已撤回误判为非法目标。
 * 返回 true 后入口才能 ACK；长连接与 HTTP 共用，通知异常不得吞掉后返回成功。
 */
public final class ReactiveMessageOperationSupport {
    private static final Logger log = LoggerFactory.getLogger(ReactiveMessageOperationSupport.class);
    private static final String SCENE = "ReactiveMessageOperationSupport.reactiveHandleOperation";

    public Mono<Boolean> reactiveHandleOperation(ChannelHandlerContext ctx, Packet packet,
            Mono<List<Packet>> preparer, ExceptionCodeEnum verifyCode, String topic, String scopeId,
            Function<List<Packet>, Mono<Boolean>> processor,
            BiConsumer<ChannelHandlerContext, Packet> after, ExceptionCodeEnum processCode) {
        return Mono.defer(() -> {
            ArchiveClaimEnum claim = RepositorySupports.QOS.claimForArchive(packet);
            if (claim != ArchiveClaimEnum.READY) {
                return Mono.just(false);
            }
            // 接管到 canonical ID 后，即使另一执行者仍占有 delivery 锁，也不得释放这个身份。
            // 否则其 Redis 副作用已执行而本次删掉新 owner，会让下一次重试换 ID 丢失恢复快照。
            packet.getMessage().ensureMetadata().ensureQosClaim().setQosArchiveBound(true);
            String owner = UUID.randomUUID().toString();
            DeliveryCompletionSupport.RunState state = RepositorySupports.DELIVERY_COMPLETION.tryStartDelivery(packet, owner);
            if (state == DeliveryCompletionSupport.RunState.DONE) {
                return Mono.just(true);
            }
            if (state != DeliveryCompletionSupport.RunState.ACQUIRED) {
                return Mono.just(false);
            }
            return Mono.defer(() -> loadOrPrepare(packet, preparer, topic, scopeId))
                    .flatMap(checkpoint -> apply(packet, checkpoint, processor, owner)
                            .map(applied -> {
                                if (!applied) {
                                    return false;
                                }
                                renewOrThrow(packet, owner);
                                commitOrThrow(packet);
                                after.accept(ctx, packet);
                                return RepositorySupports.DELIVERY_COMPLETION.finishDelivery(packet, owner);
                            }))
                    .switchIfEmpty(Mono.fromSupplier(() -> {
                        // 本次持有执行权且目标未通过校验，没有建立快照/副作用，才可以释放占位。
                        packet.getMessage().ensureMetadata().ensureQosClaim().setQosArchiveBound(false);
                        RepositorySupports.QOS.releaseQosClaim(packet);
                        ExceptionReporter.reportBusiness(verifyCode, null, SCENE, packet);
                        return false;
                    }))
                    // 取消/失败也释放本次执行权；已完成锁已删除，不会伤及新 owner。
                    .doFinally(ignored -> abortQuietly(packet, owner));
        }).onErrorResume(error -> {
            log.error("控制操作尚未完成，保留快照供同一消息重试, messageId={}", packet.getMessage().getId(), error);
            ExceptionReporter.reportSystem(processCode, error.getMessage(), SCENE, packet, error);
            return Mono.just(false);
        });
    }

    private Mono<MessageOperationCheckpoint> loadOrPrepare(Packet packet, Mono<List<Packet>> preparer,
                                                           String topic, String scopeId) {
        Object cached = RepositorySupports.INFRA.redisTemplate.opsForValue().get(key(packet));
        if (cached != null) {
            if (!(cached instanceof MessageOperationCheckpoint checkpoint)
                    || !Objects.equals(checkpoint.getTopic(), topic)
                    || !Objects.equals(checkpoint.getScopeId(), scopeId)
                    || !Objects.equals(checkpoint.getPayloadHash(), QosIdempotencyHelper.payloadHash(packet.getMessage()))) {
                return Mono.error(new IllegalStateException("控制操作快照与当前命令不一致"));
            }
            packet.getMessage().ensureMetadata().ensureQosClaim().setQosArchiveBound(true);
            return Mono.just(checkpoint);
        }
        return preparer.map(targets -> {
            MessageOperationCheckpoint checkpoint = new MessageOperationCheckpoint();
            checkpoint.setCommand(packet.clone());
            checkpoint.setTargets(targets);
            checkpoint.setTopic(topic);
            checkpoint.setScopeId(scopeId);
            checkpoint.setPayloadHash(QosIdempotencyHelper.payloadHash(packet.getMessage()));
            // SET 响应超时也可能已生效；先保留 QoS 身份，避免下一次重试换 canonical ID。
            packet.getMessage().ensureMetadata().ensureQosClaim().setQosArchiveBound(true);
            if (!Boolean.TRUE.equals(RepositorySupports.INFRA.redisTemplate.opsForValue()
                    .setIfAbsent(key(packet), checkpoint, ttlMillis(), TimeUnit.MILLISECONDS))) {
                throw new IllegalStateException("控制操作快照已被另一执行者建立，请重试");
            }
            return checkpoint;
        });
    }

    private Mono<Boolean> apply(Packet packet, MessageOperationCheckpoint checkpoint,
                                 Function<List<Packet>, Mono<Boolean>> processor, String owner) {
        if (checkpoint.isApplied()) {
            return Mono.just(true);
        }
        return RepositorySupports.MQ.confirmPacket(checkpoint.getTopic(), checkpoint.getScopeId(), checkpoint.getCommand())
                .then(Mono.defer(() -> {
                    renewOrThrow(packet, owner);
                    return processor.apply(checkpoint.getTargets());
                }))
                .map(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        renewOrThrow(packet, owner);
                        checkpoint.setApplied(true);
                        RepositorySupports.INFRA.redisTemplate.opsForValue()
                                .set(key(packet), checkpoint, ttlMillis(), TimeUnit.MILLISECONDS);
                    }
                    return Boolean.TRUE.equals(success);
                });
    }

    /** 已提交重放没有 owner；其他情况必须校验本次 owner，不能把失败提交当成功。 */
    private static void commitOrThrow(Packet packet) {
        var message = packet.getMessage();
        var metadata = message.getMetadata();
        String identity = QosClaimIdentities.resolve(message);
        if (RepositorySupports.QOS.checkDup(packet, identity)) {
            return;
        }
        String token = metadata.getQosClaim().getQosOwnerToken();
        if (StringUtils.isBlank(token) || StringUtils.isBlank(identity)) {
            throw new IllegalStateException("控制操作缺少幂等 owner");
        }
        var result = QosIdempotencyHelper.commit(RepositorySupports.INFRA.redisTemplate,
                metadata.getIngress().getAppKey(), packet.getPacketId(), identity,
                message.getId(), token, message, packet.getMessageType());
        if (result != QosIdempotencyHelper.CommitOutcome.COMMITTED
                && !RepositorySupports.QOS.checkDup(packet, identity)) {
            throw new IllegalStateException("控制操作幂等提交未确认");
        }
        metadata.ensureQosClaim().setQosOwnerToken(null);
    }

    private static void renewOrThrow(Packet packet, String owner) {
        if (!RepositorySupports.DELIVERY_COMPLETION.renewDelivery(packet, owner)) {
            throw new IllegalStateException("控制操作执行权已失效");
        }
    }

    private static void abortQuietly(Packet packet, String owner) {
        try {
            RepositorySupports.DELIVERY_COMPLETION.abortDelivery(packet, owner);
        } catch (RuntimeException error) {
            log.warn("释放控制操作执行权失败，等待租期届满, packetId={}", packet.getPacketId(), error);
        }
    }

    private static String key(Packet packet) {
        return CacheConstant.buildMessageOperationKey(packet.getMessage().getMetadata().getIngress().getAppKey(),
                packet.getPacketId());
    }

    private static long ttlMillis() {
        return MessageContext.messageRecoveryTtlMillis();
    }
}
