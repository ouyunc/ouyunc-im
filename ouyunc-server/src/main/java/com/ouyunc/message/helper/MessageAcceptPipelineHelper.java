package com.ouyunc.message.helper;

import com.ouyunc.core.exception.ExceptionReporter;

import com.ouyunc.base.constant.MqArchiveRouting;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.message.safety.ContentSafetyIngress;
import com.ouyunc.repository.DefaultRepository;
import com.ouyunc.repository.SaveMessageOutcome;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

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
        return MessageArchiveHelper.confirm(() -> repository().save(packet));
    }

    /**
     * 内容安全检查通过后旁路归档，再执行后续热写。
     * <p>REJECT：回写通知、释放 QoS 占位、不 ACK、不归档。MASK 已原地改写 content，归档与热写同文。</p>
     *
     * @param next 归档确认后的热写/投递链
     */
    public static Mono<Void> archiveAfterContentReady(ChannelHandlerContext ctx, Packet packet,
                                                      Mono<Void> next) {
        if (!ContentSafetyIngress.applyOnWorker(ctx, packet)) {
            releaseQosOnFailure(packet);
            return Mono.empty();
        }
        return archiveAfterAuth(packet).then(Mono.defer(() -> next));
    }

    /**
     * 业务校验通过后仅放行，不归档。单聊/群聊应在 process 内规范化与内容安全后再归档。
     *
     * @param shouldReject true 表示拦截
     * @param onReject     拦截时回调（如释放 QoS claim），可为 null；聊天消息拒绝时不要 ACK
     * @param rejectLog    拒绝日志模板，可含一个 {@code {}} 占位 packet
     */
    public static Mono<Boolean> gateWhenPassed(Packet packet, Mono<Boolean> shouldReject,
                                               Runnable onReject, String rejectLog) {
        return shouldReject
                .onErrorResume(error -> {
                    log.error("校验过程中出现异常: {}", error.getMessage());
                    return Mono.just(true);
                })
                .map(reject -> {
                    if (Boolean.TRUE.equals(reject)) {
                        log.warn(rejectLog, packet);
                        if (onReject != null) {
                            onReject.run();
                        }
                        return false;
                    }
                    return true;
                });
    }

    /**
     * 业务校验通过后归档并返回 true；拒绝则回调 onReject 并返回 false。
     * <p>好友/群请求等仍可用；单聊/群聊请用 {@link #gateWhenPassed} + {@link #archiveAfterContentReady}。</p>
     *
     * @param shouldReject true 表示拦截
     * @param onReject     拦截时回调（如释放 QoS claim），可为 null；聊天消息拒绝时不要 ACK
     * @param rejectLog    拒绝日志模板，可含一个 {@code {}} 占位 packet
     */
    public static Mono<Boolean> continueWhenPassed(Packet packet, Mono<Boolean> shouldReject,
                                                   Runnable onReject, String rejectLog) {
        return gateWhenPassed(packet, shouldReject, onReject, rejectLog)
                .flatMap(passed -> {
                    if (!Boolean.TRUE.equals(passed)) {
                        return Mono.just(false);
                    }
                    return archiveAfterAuth(packet).thenReturn(true);
                });
    }

    /**
     * 好友/群请求：权限拒绝视为已定性，回 S2C ACK 停 QoS 重试。
     */
    public static Mono<Boolean> continueWhenPassedOrAck(ChannelHandlerContext ctx, Packet packet,
                                                        Mono<Boolean> shouldReject, String rejectLog) {
        return continueWhenPassed(packet, shouldReject, () -> ackRequestSettled(ctx, packet), rejectLog);
    }

    /**
     * 请求已定性或 Redis 成功：回 ACK。写库/绑定失败不要调用。
     */
    public static void ackRequestSettled(ChannelHandlerContext ctx, Packet packet) {
        QosAckHelper.sendS2cAck(ctx, packet);
    }

    /**
     * Redis 热写结果收口：仅 SUCCESS/DUPLICATE 可 ACK；FAILED/CONFLICT 绝不 ACK。
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
            return Mono.empty();
        }
        qosAckOnSuccess(ctx, packet);
        if (onFreshWrite != null) {
            onFreshWrite.run();
        }
        return Mono.empty();
    }

    /** 热写成功或幂等命中后回 ACK；QoS 关闭时 no-op。 */
    public static void qosAckOnSuccess(ChannelHandlerContext ctx, Packet packet) {
        if (MessageContext.isQosEnable()) {
            QosAckHelper.sendS2cAck(ctx, packet);
        }
    }

    /** 热写/校验失败时释放尚未 commit 的 QoS 占位。 */
    public static void releaseQosOnFailure(Packet packet) {
        if (MessageContext.isQosEnable()) {
            repository().releaseQosClaim(packet);
        }
    }

    /**
     * 业务事件先等 MQ broker 确认，再跑 Redis/通知。
     * 失败不 ACK，交给客户端重试。
     */
    public static Mono<Void> confirmThenRun(String topic, String key, Packet packet, Runnable next) {
        return MessageArchiveHelper.confirm(() -> repository().publishPacketConfirmed(topic, key, packet))
                .then(Mono.fromRunnable(next));
    }
}
