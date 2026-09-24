package com.ouyunc.message.helper;

import com.ouyunc.core.exception.ExceptionReporter;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqArchiveRouting;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.safety.ContentSafetyIngress;
import com.ouyunc.repository.DefaultRepository;
import com.ouyunc.repository.ArchiveClaimResult;
import com.ouyunc.repository.SaveMessageOutcome;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;

/**
 * 消息受理管线公共能力：MQ → Redis → ACK → 投递。
 * <p>从 {@code AbstractMessageBiProcessor} 抽离，保持抽象处理器只保留三阶段 API。</p>
 * <p>子类可整体覆写 preProcess/process，但成功路径应复用本类方法，避免颠倒顺序。</p>
 * <p>单聊/群聊正式聊天：身份权限 → 内容/引用规范化 → 内容安全 → 归档 → 热写 → ACK → 投递；
 * 勿在内容安全前打 SAVE 归档（REJECT/MASK 会导致冷热不一致）。被拒原文若需留存应走独立审计事件。</p>
 */
public final class MessageAcceptPipelineHelper {

    private static final Logger log = LoggerFactory.getLogger(MessageAcceptPipelineHelper.class);

    private MessageAcceptPipelineHelper() {
    }

    private static DefaultRepository repository() {
        return DefaultRepository.INSTANCE;
    }

    /**
     * 旁路 SAVE 归档并等待 MQ 确认。SAVE 幂等键为 appKey + messageId。
     * <p>调用方须保证：权限已过、内容已规范化、内容安全已通过（或本方法前紧挨着
     * {@link #archiveAfterContentReady}）。已读/撤回/好友/群只确认领域 topic。</p>
     */
    public static Mono<Void> archiveAfterAuth(Packet packet) {
        if (MqArchiveRouting.usesDomainConfirmOnly(packet)) {
            return Mono.empty();
        }
        if (packet == null || packet.getMessage() == null
                || StringUtils.isBlank(packet.getMessage().getId())) {
            log.error("SAVE 归档缺少客户端 messageId, packetId={}",
                    packet == null ? null : packet.getPacketId());
            return Mono.error(new IllegalStateException("SAVE 归档缺少客户端 messageId"));
        }
        ArchiveClaimResult claimResult = repository().claimForArchive(packet);
        if (claimResult != ArchiveClaimResult.READY) {
            log.error("SAVE 归档前未能稳定 packetId, messageId={} packetId={}",
                    packet.getMessage().getId(), packet.getPacketId());
            return Mono.error(new ArchiveClaimException(claimResult));
        }
        return MessageArchiveHelper.confirm(() -> repository().save(packet))
                .doOnSuccess(ignored -> markArchiveBound(packet));
    }

    private static void markArchiveBound(Packet packet) {
        if (packet != null && packet.getMessage() != null && packet.getMessage().getMetadata() != null) {
            packet.getMessage().getMetadata().getQosClaim().setQosArchiveBound(true);
        }
    }

    /**
     * 内容安全检查通过后旁路归档，再执行后续热写。
     * <p>REJECT：回写拒绝结果、释放 QoS 占位、不归档。MASK 已原地改写 content，归档与热写同文。</p>
     *
     * @param next 归档确认后的热写/投递链
     */
    public static Mono<Void> archiveAfterContentReady(ChannelHandlerContext ctx, Packet packet,
                                                      Mono<Void> next) {
        if (!ContentSafetyIngress.applyOnWorker(ctx, packet)) {
            releaseQosOnFailure(packet);
            return Mono.empty();
        }
        return archiveAfterAuth(packet).thenReturn(true)
                .onErrorResume(error -> {
                    if (error instanceof ArchiveClaimException claimError) {
                        releaseQosOnFailure(packet);
                        if (claimError.result == ArchiveClaimResult.CONFLICT) {
                            MessageSendResultHelper.rejected(ctx, packet, ExceptionCodeEnum.MESSAGE_ID_CONFLICT);
                        } else if (claimError.result == ArchiveClaimResult.PENDING) {
                            MessageSendResultHelper.retryLater(ctx, packet, ExceptionCodeEnum.UNKNOWN_ERROR);
                        } else {
                            MessageSendResultHelper.unknown(ctx, packet, ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR);
                        }
                        return Mono.just(false);
                    }
                    log.error("消息归档确认结果未知, messageId={}", packet.getMessage().getId(), error);
                    MessageSendResultHelper.unknown(ctx, packet, ExceptionCodeEnum.MQ_PERSISTENCE_ERROR);
                    return Mono.just(false);
                })
                .flatMap(archived -> Boolean.TRUE.equals(archived) ? next : Mono.empty());
    }

    /**
     * 业务校验通过后仅放行，不归档。单聊/群聊应在 process 内规范化与内容安全后再归档。
     *
     * @param shouldReject true 表示拦截
     * @param onReject     拦截时回调（如释放 QoS claim），可为 null
     * @param rejectLog    拒绝日志模板，可含一个 {@code {}} 占位 packet
     */
    public static Mono<Boolean> gateWhenPassed(ChannelHandlerContext ctx, Packet packet, Mono<Boolean> shouldReject,
                                               Runnable onReject, String rejectLog) {
        return shouldReject
                .onErrorResume(error -> {
                    log.error("校验过程中出现异常: {}", error.getMessage(), error);
                    MessageSendResultHelper.retryLater(ctx, packet, ExceptionCodeEnum.UNKNOWN_ERROR);
                    if (onReject != null) {
                        onReject.run();
                    }
                    return Mono.empty();
                })
                .map(reject -> {
                    if (Boolean.TRUE.equals(reject)) {
                        log.warn(rejectLog, packet);
                        MessageSendResultHelper.rejected(ctx, packet, ExceptionCodeEnum.MESSAGE_SEND_BUSINESS_REJECT);
                        if (onReject != null) {
                            onReject.run();
                        }
                        return false;
                    }
                    return true;
                });
    }

    /** 好友/群请求权限拒绝时返回明确拒绝结果。 */
    public static Mono<Boolean> continueWhenPassedOrAck(ChannelHandlerContext ctx, Packet packet,
                                                        Mono<Boolean> shouldReject, String rejectLog) {
        return gateWhenPassed(ctx, packet, shouldReject, null, rejectLog)
                .flatMap(passed -> Boolean.TRUE.equals(passed)
                        ? archiveAfterAuth(packet).thenReturn(true) : Mono.just(false))
                .onErrorResume(ArchiveClaimException.class, error -> {
                    releaseQosOnFailure(packet);
                    if (error.result == ArchiveClaimResult.CONFLICT) {
                        MessageSendResultHelper.rejected(ctx, packet, ExceptionCodeEnum.MESSAGE_ID_CONFLICT);
                    } else if (error.result == ArchiveClaimResult.PENDING) {
                        MessageSendResultHelper.retryLater(ctx, packet, ExceptionCodeEnum.UNKNOWN_ERROR);
                    } else {
                        MessageSendResultHelper.unknown(ctx, packet, ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR);
                    }
                    return Mono.just(false);
                });
    }

    /** 请求成功落库或确认已处理后回已受理结果；业务拒绝不可调用。 */
    public static void requestAccepted(ChannelHandlerContext ctx, Packet packet) {
        MessageSendResultHelper.accepted(ctx, packet);
    }

    /**
     * Redis 热写结果收口：仅 SUCCESS/DUPLICATE 可回已受理；FAILED/CONFLICT 返回失败结果。
     * 仅 {@link SaveMessageOutcome#isFreshWrite()} 时执行 {@code onFreshWrite}。
     */
    public static Mono<Void> afterHotSave(ChannelHandlerContext ctx, Packet packet, SaveMessageOutcome result,
                                          Runnable onFreshWrite, String failEventMessage) {
        if (result != null && result.isDuplicate()) {
            qosAckOnSuccess(ctx, packet);
            return Mono.empty();
        }
        if (result == null || !result.isFreshWrite()) {
            log.error("热写未成功，拒绝 ACK/投递: outcome={} packetId={}", result, packet.getPacketId());
            ExceptionReporter.reportSystem(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, failEventMessage != null ? failEventMessage : "消息热写失败", "MessageAcceptPipelineHelper.afterHotSave", packet);
            releaseQosOnFailure(packet);
            if (result == SaveMessageOutcome.CONFLICT) {
                MessageSendResultHelper.rejected(ctx, packet, ExceptionCodeEnum.MESSAGE_ID_CONFLICT);
            } else {
                // Redis 写入超时可能已提交，不能向客户端承诺“未写入”。
                MessageSendResultHelper.unknown(ctx, packet, ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR);
            }
            return Mono.empty();
        }
        if (onFreshWrite != null) {
            try {
                onFreshWrite.run();
            } catch (ExternalDeliveryConfirmException error) {
                log.error("外部渠道未确认，不回受理成功, messageId={}", packet.getMessage().getId(), error);
                MessageSendResultHelper.unknown(ctx, packet, ExceptionCodeEnum.MQ_PERSISTENCE_ERROR);
                return Mono.empty();
            } catch (Exception error) {
                log.error("消息已写入，但后续副作用失败, messageId={}", packet.getMessage().getId(), error);
            }
        }
        qosAckOnSuccess(ctx, packet);
        return Mono.empty();
    }

    /** 热写成功或幂等命中后回受理结果，独立于业务 QoS 开关。 */
    public static void qosAckOnSuccess(ChannelHandlerContext ctx, Packet packet) {
        MessageSendResultHelper.accepted(ctx, packet);
    }

    /** 热写/校验失败时释放尚未 commit 的 QoS 占位。 */
    public static void releaseQosOnFailure(Packet packet) {
        repository().releaseQosClaim(packet);
    }

    /**
     * 调用方须已在同一把关系锁内写完 Redis。此处只确认 MQ，失败回 UNKNOWN，不在确认前改状态。
     *
     * @return false 时调用方不得再通知或回受理成功
     */
    public static boolean publishRequestCommand(ChannelHandlerContext ctx, String topic, String key, Packet packet) {
        try {
            if (packet.getMessage().getMetadata().getRequestEventContext() == null) {
                RequestEventContextFactory.ensure(packet);
            }
            repository().publishPacketConfirmed(topic, key, packet)
                    .get(MessageConstant.MESSAGE_ARCHIVE_CONFIRM_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return true;
        } catch (Exception error) {
            log.error("请求归档结果未知, messageId={}", packet.getMessage().getId(), error);
            MessageSendResultHelper.unknown(ctx, packet, ExceptionCodeEnum.MQ_PERSISTENCE_ERROR);
            return false;
        }
    }

    /**
     * 在当前订阅线程执行业务。Redis 提交和 {@link #publishRequestCommand} 必须放在同一把锁里，
     * 不能先确认 MQ 再改会话。
     */
    public static Mono<Void> confirmThenRun(ChannelHandlerContext ctx, String topic, String key,
                                            Packet packet, Runnable next) {
        return Mono.fromRunnable(next);
    }

    /** 只在受理管线内部传播，用于保留 Redis claim 的精确结果。 */
    private static final class ArchiveClaimException extends RuntimeException {
        private final ArchiveClaimResult result;

        private ArchiveClaimException(ArchiveClaimResult result) {
            super("archive claim failed: " + result);
            this.result = result;
        }
    }
}
