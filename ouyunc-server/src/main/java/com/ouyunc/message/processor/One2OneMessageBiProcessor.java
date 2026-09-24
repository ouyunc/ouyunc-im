package com.ouyunc.message.processor;

import com.ouyunc.core.exception.ExceptionReporter;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.base.constant.enums.IdentityType;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.AtMentionHelper;
import com.ouyunc.message.helper.MessageAcceptPipelineHelper;
import com.ouyunc.message.helper.MessageSendResultHelper;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageDeliveryRouteHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.helper.MessageRefHelper;
import com.ouyunc.message.validator.*;
import com.ouyunc.repository.support.MessageIndexScope;
import com.ouyunc.repository.SaveMessageOutcome;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 * 一对一（单聊）消息处理器。
 * <p>标准管线：身份权限 → ref 规范化 → 内容安全 → MQ 归档 → Redis 热写 → 仅成功/重复时 ACK → 新写入才扇出。
 * 已读/撤回在对应操作成功后再 ACK（不走 SAVE）。子类级覆写见 {@link AbstractMessageBiProcessor}。</p>
 */
public final class One2OneMessageBiProcessor extends AbstractMessageBiProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(One2OneMessageBiProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.ONE_2_ONE;
    }


    @Override
    public Mono<Boolean> preProcess(ChannelHandlerContext ctx, Packet packet) {
        if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
            log.error("校验消息失败: {} 认证未通过,开始关闭channel", packet);
            ExceptionReporter.reportBusiness(ExceptionCodeEnum.LOGIN_AUTH_ERROR, "登录认证未通过!", "One2OneMessageBiProcessor.process", packet);
            ctx.close();
            return Mono.just(false);
        }
        // QoS 判重占位；正式归档挪到 process（规范化 + 内容安全之后），避免 REJECT/MASK 冷热不一致
        if (qosPreHandle(ctx, packet)) {
            return Mono.just(false);
        }
        return MessageAcceptPipelineHelper.gateWhenPassed(ctx, packet,
                PermissionValidator.INSTANCE.negate()
                        .or(One2OneChatAccessValidator.INSTANCE)
                        .or(FromToValidator.INSTANCE)
                        .verify(packet, ctx),
                () -> MessageAcceptPipelineHelper.releaseQosOnFailure(packet),
                "权限不足/不是好友/在黑名单中/被屏蔽/发送方和接收方相同, 请知悉。该消息 {} 被忽略");
    }

    /** COMMITTED 重入：用正式 packetId 幂等补未读，失败则 qosPreHandle 不 ACK。 */
    @Override
    protected boolean repairDerivedIndexOnQosDuplicate(Packet packet) {
        return repository().repairOne2OneUnread(packet);
    }

    /**
     * 处理一对一消息
     */
    @Override
    public Mono<Void> process(ChannelHandlerContext ctx, Packet packet) {
        log.debug("Processing one-to-one message...");
        AbstractBaseBiProcessor<Mono<Void>, ? extends Number> content = MessageServerContext.messageContentProcessorCache.get(packet.getMessage().getContentType());
        if (content != null) {
            // 插件内容处理器：内容安全 + 归档后再交给插件，与默认聊天路径语义一致
            return MessageAcceptPipelineHelper.archiveAfterContentReady(ctx, packet,
                    Mono.defer(() -> content.process(ctx, packet)));
        }
        AtMentionHelper.clearAtIfPresent(packet.getMessage());
        if (!MessageRefHelper.normalizeMessageRefOrReject(packet)) {
            MessageSendResultHelper.rejected(ctx, packet, ExceptionCodeEnum.MESSAGE_REF_INVALID_ERROR);
            MessageAcceptPipelineHelper.releaseQosOnFailure(packet);
            return Mono.empty();
        }
        int contentType = packet.getMessage().getContentType();
        if (MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType() == contentType) {
            return handleReadReceipt(ctx, packet);
        }
        if (MessageContentTypeEnum.WITHDRAW_CONTENT.getType() == contentType) {
            return handleWithdrawMessage(ctx, packet);
        }
        return MessageAcceptPipelineHelper.archiveAfterContentReady(ctx, packet,
                saveMessage(packet)
                        .flatMap(result -> MessageAcceptPipelineHelper.afterHotSave(ctx, packet, result,
                                () -> afterOne2OneFreshWrite(packet),
                                "单聊消息写入会话失败"))
                        .onErrorResume(error -> {
                            log.error("单聊消息持久化异常, packetId={}", packet.getPacketId(), error);
                            ExceptionReporter.reportSystem(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR,
                                    "单聊持久化异常: " + error.getMessage(),
                                    "One2OneMessageBiProcessor.process", packet, error);
                            MessageAcceptPipelineHelper.releaseQosOnFailure(packet);
                            MessageSendResultHelper.unknown(ctx, packet, ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR);
                            return Mono.empty();
                        }));
    }

    private void afterOne2OneFreshWrite(Packet packet) {
        try {
            repository().saveLastMessageForSession(
                    IdentityUtil.sessionId(packet.getMessage().getFrom(), packet.getMessage().getTo()),
                    packet, MessageConstant.CACHE_SESSION_LAST_MESSAGE_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            // 最后消息是可重建派生索引，失败不能阻断已提交消息的实时投递。
            log.warn("更新单聊最后消息失败，继续投递 packetId={}", packet.getPacketId(), e);
        }
        repository().reactiveAdvanceSenderReadOffsetOnSend(
                        packet, IdentityType.ONE_2_ONE, MessageConstant.CACHE_MESSAGE_READ_RECEIPT_KEY_EXPIRE_TIMESTAMP)
                .subscribe(
                        ignored -> { },
                        e -> log.warn("发送消息静默更新本端已读 offset 失败, packetId={}", packet.getPacketId(), e));
        deliver(packet, false);
    }

    /**
     * 处理消息内容类型是撤回消息
     *
     * @param ctx
     * @param packet
     */
    private Mono<Void> handleWithdrawMessage(ChannelHandlerContext ctx, Packet packet) {
        String from = packet.getMessage().getFrom();
        String to = packet.getMessage().getTo();
        String sessionId = IdentityUtil.sessionId(from, to);
        return repository().reactiveHandleOperation(ctx, packet,
                repository().reactiveLoadWithdrawTargetPackets(
                        packet, sessionId, MessageIndexScope.CHANNEL_SESSION, true),
                ExceptionCodeEnum.WITHDRAW_MESSAGE_VERIFY_ERROR,
                MqConstant.MQ_WITHDRAW_MESSAGE_TOPIC, sessionId,
                packets -> repository().reactiveWithdrawMessage(
                        packet, sessionId, MessageIndexScope.CHANNEL_SESSION, packets),
                (ctx0, packet0) -> {
                    MessageAcceptPipelineHelper.qosAckOnSuccess(ctx0, packet0);
                    Message msg = packet0.getMessage();
                    if (msg != null && msg.getMetadata() != null) {
                        String appKey = msg.getMetadata().getIngress().getAppKey();
                        if (StringUtils.isNoneBlank(appKey, sessionId)) {
                            repository().refreshSessionLastMessageAfterWithdraw(appKey, sessionId);
                        }
                    }
                    deliver(packet0, true);
                },
                ExceptionCodeEnum.WITHDRAW_MESSAGE_ERROR)
                .doOnNext(success -> {
                    if (!Boolean.TRUE.equals(success)) {
                        MessageSendResultHelper.unknown(ctx, packet, ExceptionCodeEnum.UNKNOWN_ERROR);
                        MessageAcceptPipelineHelper.releaseQosOnFailure(packet);
                    }
                })
                .then();
    }

    /**
     * 处理已读回执：不落库为对端「聊天消息」，但需推送给 {@link Message#getTo()}（消息发送方），
     * 以便发送方多端将己方气泡更新为「已读」。阅读方 {@link Message#getFrom()} 不再重复投递。
     * <p>对端亦可根据对方聊天消息 packetId 推断已读，见 {@code docs/read-receipt-session-offset.md}。</p>
     */
    private Mono<Void> handleReadReceipt(ChannelHandlerContext ctx, Packet packet) {
        String sessionId = IdentityUtil.sessionId(packet.getMessage().getFrom(), packet.getMessage().getTo());
        return repository().reactiveHandleOperation(ctx, packet,
                repository().reactiveLoadValidatedReadReceiptPackets(
                        packet, sessionId, IdentityType.ONE_2_ONE, false),
                ExceptionCodeEnum.READ_RECEIPT_MESSAGE_VERIFY_ERROR,
                MqConstant.MQ_READ_RECEIPT_MESSAGE_TOPIC, sessionId,
                packets -> repository().reactiveReadReceiptMessage(
                        packet, IdentityType.ONE_2_ONE,
                        MessageConstant.CACHE_MESSAGE_READ_RECEIPT_KEY_EXPIRE_TIMESTAMP, packets),
                (ctx0, packet0) -> {
                    MessageAcceptPipelineHelper.qosAckOnSuccess(ctx0, packet0);
                    deliverReadReceiptToSender(packet0);
                },
                ExceptionCodeEnum.READ_RECEIPT_MESSAGE_ERROR)
                .doOnNext(success -> {
                    if (!Boolean.TRUE.equals(success)) {
                        MessageSendResultHelper.unknown(ctx, packet, ExceptionCodeEnum.UNKNOWN_ERROR);
                        MessageAcceptPipelineHelper.releaseQosOnFailure(packet);
                    }
                })
                .then();
    }

    /** 将已读回执推送给会话中的消息发送方（packet.message.to），与私聊普通消息投递 to 一致 */
    private void deliverReadReceiptToSender(Packet packet) {
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getIngress().getAppKey();
        List<LoginClientInfo> senderClients = ClientHelper.onlineAll(appKey, message.getTo());
        if (CollectionUtils.isNotEmpty(senderClients)) {
            MessageHelper.asyncSendMessage(packet, senderClients);
        }
    }



    /**
     * 发送消息给接收方（投递仅此职责）
     *
     * @param packet
     * @param forceSelfSync
     */
    private void deliver(Packet packet, Boolean forceSelfSync) {
        MessageDeliveryRouteHelper.deliverPeerMessage(packet, Boolean.TRUE.equals(forceSelfSync));
    }

    @Override
    protected void replayExternalDelivery(Packet packet) {
        deliver(packet, false);
    }

    /**
     * 保存消息
     */
    private Mono<SaveMessageOutcome> saveMessage(Packet packet) {
        Message message = packet.getMessage();
        String sessionId = IdentityUtil.sessionId(message.getFrom(), message.getTo());
        return repository().reactiveSaveOne2OneMessage(packet, sessionId, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP);
    }



}
