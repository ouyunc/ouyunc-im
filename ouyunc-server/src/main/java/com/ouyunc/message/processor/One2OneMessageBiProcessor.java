package com.ouyunc.message.processor;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.base.constant.enums.IdentityType;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.AtMentionHelper;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageDeliveryRouteHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.helper.MessageRefHelper;
import com.ouyunc.message.helper.PacketChannelWriter;
import com.ouyunc.message.validator.*;
import com.ouyunc.repository.support.MessageIndexScope;
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
 * <p>普通聊天消息持久化成功后回 QoS ACK；已读回执、撤回在对应操作成功后再 ACK。</p>
 * <p>如果在使用过程中存在 Redis 瓶颈，可使用响应式 Redis 改造提高吞吐量。</p>
 */
public final class One2OneMessageBiProcessor extends AbstractMessageBiProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(One2OneMessageBiProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.ONE_2_ONE;
    }


    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        preProcessStage(ctx, packet).subscribe();
    }

    @Override
    public Mono<Void> preProcessStage(ChannelHandlerContext ctx, Packet packet) {
        repository().save(packet);
        if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
            log.error("校验消息失败: {} 认证未通过,开始关闭channel", packet);
            MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.LOGIN_AUTH_ERROR, "登录认证未通过!", packet), MessageEventTypeEnum.EXCEPTION), true);
            ctx.close();
            return Mono.empty();
        }
        if (MessageContext.isQosEnable() && qosPreHandle(ctx, packet)) {
            return Mono.empty();
        }
        return fireWhenPassed(ctx, packet,
                PermissionValidator.INSTANCE.negate()
                        .or(One2OneChatAccessValidator.INSTANCE)
                        .or(FromToValidator.INSTANCE)
                        .verify(packet, ctx),
                () -> releaseQosOnFailure(packet),
                "权限不足/不是好友/在黑名单中/被屏蔽/发送方和接收方相同, 请知悉。该消息 {} 被忽略");
    }

    /**
     * 处理一对一消息
     */
    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        processStage(ctx, packet).subscribe();
    }

    @Override
    public Mono<Void> processStage(ChannelHandlerContext ctx, Packet packet) {
        log.debug("Processing one-to-one message...");
        if (processWithContentProcessor(ctx, packet)) {
            return Mono.empty();
        }
        AtMentionHelper.clearAtIfPresent(packet.getMessage());
        if (!MessageRefHelper.normalizeMessageRefOrReject(packet)) {
            releaseQosOnFailure(packet);
            return Mono.empty();
        }
        int contentType = packet.getMessage().getContentType();
        if (MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType() == contentType) {
            return handleReadReceipt(ctx, packet);
        }
        return saveMessage(packet)
                .flatMap(result -> afterOne2OneSaved(ctx, packet, contentType, result))
                .onErrorResume(error -> {
                    log.error("单聊消息持久化异常, packetId={}", packet.getPacketId(), error);
                    MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "单聊持久化异常: " + error.getMessage(), packet), MessageEventTypeEnum.EXCEPTION), true);
                    releaseQosOnFailure(packet);
                    return Mono.empty();
                });
    }

    private Mono<Void> afterOne2OneSaved(ChannelHandlerContext ctx, Packet packet, int contentType, Boolean result) {
        if (!Boolean.TRUE.equals(result)) {
            log.error("单聊会话索引写入失败: {}", packet);
            MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "单聊消息写入会话失败", packet), MessageEventTypeEnum.EXCEPTION), true);
            releaseQosOnFailure(packet);
            return Mono.empty();
        }
        if (MessageContext.isQosEnable()
                && MessageContentTypeEnum.WITHDRAW_CONTENT.getType() != contentType) {
            qosPostHandle(ctx, packet);
        }
        if (MessageContentTypeEnum.WITHDRAW_CONTENT.getType() != contentType
                && MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType() != contentType) {
            repository().saveLastMessageForSession(IdentityUtil.sessionId(packet.getMessage().getFrom(), packet.getMessage().getTo()), packet, MessageConstant.CACHE_SESSION_LAST_MESSAGE_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
        }
        repository().reactiveAdvanceSenderReadOffsetOnSend(
                        packet, IdentityType.ONE_2_ONE, MessageConstant.CACHE_MESSAGE_READ_RECEIPT_KEY_EXPIRE_TIMESTAMP)
                .subscribe(
                        ignored -> { },
                        e -> log.warn("发送消息静默更新本端已读 offset 失败, packetId={}", packet.getPacketId(), e));
        if (MessageContentTypeEnum.WITHDRAW_CONTENT.getType() == contentType) {
            return handleWithdrawMessage(ctx, packet);
        }
        deliverAndFireNext(ctx, packet, false);
        return Mono.empty();
    }

    private void qosAckOnSuccess(ChannelHandlerContext ctx, Packet packet) {
        if (MessageContext.isQosEnable()) {
            qosPostHandle(ctx, packet);
        }
    }

    private void releaseQosOnFailure(Packet packet) {
        if (MessageContext.isQosEnable()) {
            repository().releaseQosClaim(packet);
        }
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
        repository().reactiveHandleOperation(ctx, packet,
                repository().reactiveLoadWithdrawTargetPackets(
                        packet, sessionId, MessageIndexScope.CHANNEL_SESSION, true),
                ExceptionCodeEnum.WITHDRAW_MESSAGE_VERIFY_ERROR,
                MqConstant.MQ_WITHDRAW_MESSAGE_TOPIC, sessionId,
                packets -> repository().reactiveWithdrawMessage(
                        packet, sessionId, MessageIndexScope.CHANNEL_SESSION, packets),
                (ctx0, packet0) -> {
                    qosAckOnSuccess(ctx0, packet0);
                    Message msg = packet0.getMessage();
                    if (msg != null && msg.getMetadata() != null) {
                        String appKey = msg.getMetadata().getAppKey();
                        if (StringUtils.isNoneBlank(appKey, sessionId)) {
                            repository().refreshSessionLastMessageAfterWithdraw(appKey, sessionId);
                        }
                    }
                    deliverAndFireNext(ctx0, packet0, true);
                },
                (exceptionEvent) -> MessageServerContext.publishEvent(exceptionEvent, true),
                ExceptionCodeEnum.WITHDRAW_MESSAGE_ERROR)
                .doOnNext(success -> {
                    if (!Boolean.TRUE.equals(success)) {
                        releaseQosOnFailure(packet);
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
        repository().reactiveHandleOperation(ctx, packet,
                repository().reactiveLoadValidatedReadReceiptPackets(
                        packet, sessionId, IdentityType.ONE_2_ONE, false),
                ExceptionCodeEnum.READ_RECEIPT_MESSAGE_VERIFY_ERROR,
                MqConstant.MQ_READ_RECEIPT_MESSAGE_TOPIC, sessionId,
                packets -> repository().reactiveReadReceiptMessage(
                        packet, IdentityType.ONE_2_ONE,
                        MessageConstant.CACHE_MESSAGE_READ_RECEIPT_KEY_EXPIRE_TIMESTAMP, packets),
                (ctx0, packet0) -> {
                    qosAckOnSuccess(ctx0, packet0);
                    deliverReadReceiptToSender(packet0);
                    PacketChannelWriter.fireChannelRead(ctx0, packet0);
                },
                (exceptionEvent)-> MessageServerContext.publishEvent(exceptionEvent, true),
                ExceptionCodeEnum.READ_RECEIPT_MESSAGE_ERROR)
                .doOnNext(success -> {
                    if (!Boolean.TRUE.equals(success)) {
                        releaseQosOnFailure(packet);
                    }
                })
                .then();
    }

    /** 将已读回执推送给会话中的消息发送方（packet.message.to），与私聊普通消息投递 to 一致 */
    private void deliverReadReceiptToSender(Packet packet) {
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        List<LoginClientInfo> senderClients = ClientHelper.onlineAll(appKey, message.getTo());
        if (CollectionUtils.isNotEmpty(senderClients)) {
            MessageHelper.asyncSendMessage(packet, senderClients);
        }
    }



    /**
     * 发送消息给接收方
     *
     * @param ctx
     * @param packet
     */
    private void deliverAndFireNext(ChannelHandlerContext ctx, Packet packet, Boolean forceSelfSync) {
        MessageDeliveryRouteHelper.deliverPeerMessage(packet, Boolean.TRUE.equals(forceSelfSync));
        PacketChannelWriter.fireChannelRead(ctx, packet);
    }

    /**
     * 使用内容处理器处理消息
     */
    private boolean processWithContentProcessor(ChannelHandlerContext ctx, Packet packet) {
        AbstractBaseBiProcessor<? extends Number> processor = MessageServerContext.messageContentProcessorCache.get(packet.getMessage().getContentType());
        if (processor != null) {
            processor.process(ctx, packet);
            return true;
        }
        return false;
    }

    /**
     * 保存消息
     */
    private Mono<Boolean> saveMessage(Packet packet) {
        Message message = packet.getMessage();
        String sessionId = IdentityUtil.sessionId(message.getFrom(), message.getTo());
        return repository().reactiveSaveOne2OneMessage(packet, sessionId, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP);
    }



}
