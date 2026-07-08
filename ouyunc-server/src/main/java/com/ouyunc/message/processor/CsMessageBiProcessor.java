package com.ouyunc.message.processor;

import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.message.helper.CsCustomerServiceLastMessageHelper;
import com.ouyunc.repository.cs.CsImSessionRoute;
import com.ouyunc.repository.cs.CsMessageScopeHelper;
import com.ouyunc.repository.support.MessageIndexScope;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.CsMessageDeliveryRouter;
import com.ouyunc.message.helper.CsSessionMessageHelper;
import com.ouyunc.message.helper.CsSessionMessageHelper.PrepareOutcome;
import com.ouyunc.message.helper.MessageRefHelper;
import com.ouyunc.message.validator.AuthValidator;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * 客服会话消息处理器（MessageType=CUSTOMER_SERVICE）。
 *
 * <p>通道 scope：{@code channelSessionId = sessionId(user_id, service_identity)}，用于 Route 查找。</p>
 * <p>消息 scope：{@code ticketMessageScopeId = ticketId}，用于 msgs ZSet / 撤回 / 已读 / lm。</p>
 * <p>每条聊天消息 {@code correlationId = ticketId}。</p>
 */
public final class CsMessageBiProcessor extends AbstractMessageBiProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(CsMessageBiProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.CUSTOMER_SERVICE;
    }

    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        ThreadPoolManager.messageProcessorExecutor().execute(() -> repository().publishArchiveAsync(packet));
        if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
            log.error("客服消息校验失败: {} 认证未通过, 关闭 channel", packet);
            MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.LOGIN_AUTH_ERROR, "登录认证未通过!", packet), MessageEventTypeEnum.EXCEPTION), true);
            ctx.close();
            return;
        }
        if (MessageContext.isQosEnable() && qosPreHandle(ctx, packet)) {
            return;
        }
        ctx.fireChannelRead(packet);
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        log.debug("Processing customer service message...");
        PrepareOutcome prepared = validateAndPrepare(packet);
        if (!prepared.accepted()) {
            return;
        }
        CsImSessionRoute route = prepared.route();
        Message message = packet.getMessage();
        int contentType = message.getContentType();
        if (MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType() == contentType) {
            handleReadReceipt(ctx, packet, route);
            return;
        }
        saveMessage(packet, route).subscribe(
                result -> {
                    if (!result) {
                        log.error("客服 ticket 消息索引写入失败: {}", packet);
                        MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "客服消息写入 ticket 失败", packet), MessageEventTypeEnum.EXCEPTION), true);
                        releaseQosOnFailure(packet);
                        return;
                    }
                    if (MessageContext.isQosEnable() && MessageContentTypeEnum.WITHDRAW_CONTENT.getType() != contentType) {
                        qosPostHandle(ctx, packet);
                    }
                    if (MessageContentTypeEnum.WITHDRAW_CONTENT.getType() != contentType) {
                        CsCustomerServiceLastMessageHelper.saveChatLastMessage(repository(), route, packet);
                    }
                    if (MessageContentTypeEnum.WITHDRAW_CONTENT.getType() != contentType) {
                        repository().reactiveAdvanceCsSenderReadOffsetOnSend(
                                        packet, route, packet.getDeviceType(),
                                        MessageConstant.CACHE_MESSAGE_READ_RECEIPT_KEY_EXPIRE_TIMESTAMP)
                                .subscribe(
                                        ignored -> {
                                        },
                                        e -> log.warn("客服发消息静默更新 ticket 已读 offset 失败, packetId={}", packet.getPacketId(), e));
                    }
                    if (MessageContentTypeEnum.WITHDRAW_CONTENT.getType() == contentType) {
                        handleWithdrawMessage(ctx, packet, route);
                    } else {
                        CsMessageDeliveryRouter.deliverCustomerServiceMessage(packet, route, false);
                        ctx.fireChannelRead(packet);
                    }
                },
                error -> {
                    log.error("客服消息持久化异常, packetId={}", packet.getPacketId(), error);
                    MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "客服持久化异常: " + error.getMessage(), packet), MessageEventTypeEnum.EXCEPTION), true);
                    releaseQosOnFailure(packet);
                });

    }


    private PrepareOutcome validateAndPrepare(Packet packet) {
        if (!MessageRefHelper.normalizeMessageRefOrReject(packet)) {
            releaseQosOnFailure(packet);
            return PrepareOutcome.reject("引用校验失败");
        }
        Message message = packet.getMessage();
        if (message == null || StringUtils.isAnyBlank(message.getFrom(), message.getTo())
                || StringUtils.equals(message.getFrom(), message.getTo())) {
            log.warn("客服消息 from/to 无效或与相同, packetId={}", packet.getPacketId());
            releaseQosOnFailure(packet);
            return PrepareOutcome.reject("from/to 无效");
        }
        PrepareOutcome outcome = CsSessionMessageHelper.prepare(packet);
        if (!outcome.accepted()) {
            log.warn("客服会话路由校验失败: {} | packetId={}", outcome.rejectReason(), packet.getPacketId());
            CsSessionMessageHelper.publishReject(packet, outcome.rejectReason());
            releaseQosOnFailure(packet);
        }
        return outcome;
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

    private void handleWithdrawMessage(ChannelHandlerContext ctx, Packet packet, CsImSessionRoute route) {
        String ticketScopeId = CsMessageScopeHelper.ticketMessageScopeId(route);
        String appKey = packet.getMessage().getMetadata().getAppKey();
        repository().reactiveHandleOperation(ctx, packet,
                        repository().reactiveLoadWithdrawTargetPackets(
                                packet, ticketScopeId, MessageIndexScope.CS_TICKET, true),
                        ExceptionCodeEnum.WITHDRAW_MESSAGE_VERIFY_ERROR,
                        () -> repository().savePacket2Mq(MqConstant.MQ_WITHDRAW_MESSAGE_TOPIC, ticketScopeId, packet),
                        packets -> repository().reactiveWithdrawMessage(
                                packet, ticketScopeId, MessageIndexScope.CS_TICKET, packets),
                        (ctx0, packet0) -> {
                            qosAckOnSuccess(ctx0, packet0);
                            CsMessageDeliveryRouter.deliverCustomerServiceMessage(packet0, route, true);
                            if (StringUtils.isNoneBlank(ticketScopeId, appKey)) {
                                repository().refreshCsTicketLastMessageAfterWithdraw(appKey, ticketScopeId);
                            }
                            ctx0.fireChannelRead(packet0);
                        },
                        (exceptionEvent) -> MessageServerContext.publishEvent(exceptionEvent, true),
                        ExceptionCodeEnum.WITHDRAW_MESSAGE_ERROR)
                .subscribe(success -> {
                    if (!Boolean.TRUE.equals(success)) {
                        releaseQosOnFailure(packet);
                    }
                });

    }


    private void handleReadReceipt(ChannelHandlerContext ctx, Packet packet, CsImSessionRoute route) {
        String ticketScopeId = CsMessageScopeHelper.ticketMessageScopeId(route);
        if (packet.getMessage().getMetadata() != null) {
            packet.getMessage().getMetadata().setCsReaderId(
                    CsMessageScopeHelper.resolveReaderId(packet.getMessage(), route));
        }
        repository().reactiveHandleOperation(ctx, packet,
                        repository().reactiveValidCsReadReceiptMessage(packet, route, packet.getDeviceType()),
                        () -> repository().savePacket2Mq(MqConstant.MQ_READ_RECEIPT_MESSAGE_TOPIC, ticketScopeId, packet),
                        repository().reactiveCsReadReceiptMessage(
                                packet, route, packet.getDeviceType(),
                                MessageConstant.CACHE_MESSAGE_READ_RECEIPT_KEY_EXPIRE_TIMESTAMP),
                        (ctx0, packet0) -> {
                            qosAckOnSuccess(ctx0, packet0);
                            CsMessageDeliveryRouter.deliverReadReceiptToOriginalSender(packet0, route);
                            ctx0.fireChannelRead(packet0);
                        },
                        (exceptionEvent) -> MessageServerContext.publishEvent(exceptionEvent, true),
                        ExceptionCodeEnum.READ_RECEIPT_MESSAGE_ERROR)
                .subscribe(success -> {
                    if (!Boolean.TRUE.equals(success)) {
                        releaseQosOnFailure(packet);
                    }
                });
    }

    private Mono<Boolean> saveMessage(Packet packet, CsImSessionRoute route) {
        return repository().reactiveSaveCsTicketMessage(
                packet, route, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP);
    }
}
