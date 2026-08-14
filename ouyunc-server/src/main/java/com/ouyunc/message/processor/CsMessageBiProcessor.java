package com.ouyunc.message.processor;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.*;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.*;
import com.ouyunc.message.helper.CsHelper.PrepareOutcome;
import com.ouyunc.message.validator.AuthValidator;
import com.ouyunc.repository.cs.CsImSessionRoute;
import com.ouyunc.repository.support.MessageIndexScope;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * 客服会话消息处理器（MessageType=CUSTOMER_SERVICE）。
 *
 * <p>路由主键 = {@code ticketId}（消息 {@code correlationId}）。</p>
 * <p>通道语义 sessionId = {@code sessionId(userId, serviceIdentity)}，存在路由 Hash 字段中。</p>
 * <p>消息 scope：{@code ticketMessageScopeId = ticketId}，用于 msgs ZSet / 撤回 / 已读 / lm。</p>
 */
public final class CsMessageBiProcessor extends AbstractMessageBiProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(CsMessageBiProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.CUSTOMER_SERVICE;
    }

    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
            log.error("客服消息校验失败: {} 认证未通过, 关闭 channel", packet);
            MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.LOGIN_AUTH_ERROR, "登录认证未通过!", packet), MessageEventTypeEnum.EXCEPTION), true);
            ctx.close();
            return;
        }
        // 认证通过后再归档，避免未登录/非法包进入数仓
        ThreadPoolManager.messageProcessorExecutor().execute(() -> repository().publishArchiveAsync(packet));
        if (MessageContext.isQosEnable() && qosPreHandle(ctx, packet)) {
            return;
        }
        ctx.fireChannelRead(packet);
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        // 同步 Redis 路由校验不能堵在 Netty EventLoop
        ThreadPoolManager.messageProcessorExecutor().execute(() -> {
            try {
                processOffloaded(ctx, packet);
            } catch (Exception e) {
                log.error("客服消息处理异常, packetId={}", packet.getPacketId(), e);
                releaseQosOnFailure(packet);
            }
        });
    }

    private void processOffloaded(ChannelHandlerContext ctx, Packet packet) {
        log.debug("Processing customer service message...");
        if (processWithContentProcessor(ctx, packet)) {
            return;
        }
        PrepareOutcome prepared = validateAndPrepare(packet);
        if (!prepared.accepted()) {
            return;
        }
        PrepareOutcome live = CsHelper.refreshDelivery(packet, prepared.route());
        if (!live.accepted()) {
            log.warn("客服投递前路由刷新失败: {} | packetId={}", live.rejectReason(), packet.getPacketId());
            CsHelper.publishReject(packet, live.rejectReason());
            releaseQosOnFailure(packet);
            return;
        }
        CsImSessionRoute route = live.route();
        CsHelper.rewriteAgentFrom(packet, route);
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
                    if (MessageContentTypeEnum.WITHDRAW_CONTENT.getType() == contentType) {
                        handleWithdrawMessage(ctx, packet, route);
                    } else {
                        if (MessageContext.isQosEnable()) {
                            qosPostHandle(ctx, packet);
                        }
                        CsHelper.saveChatLastMessage(repository(), route, packet);
                        CsHelper.notifyAfterSave(packet, route);
                        repository().reactiveAdvanceCsSenderReadOffsetOnSend(
                                        packet, route, packet.getDeviceType(),
                                        MessageConstant.CACHE_MESSAGE_READ_RECEIPT_KEY_EXPIRE_TIMESTAMP)
                                .subscribe(
                                        ignored -> {
                                        },
                                        e -> log.warn("客服发消息静默更新 ticket 已读 offset 失败, packetId={}", packet.getPacketId(), e));
                        CsHelper.deliverMessage(packet, route, false);
                        fireReadOnEventLoop(ctx, packet);
                    }
                },
                error -> {
                    log.error("客服消息持久化异常, packetId={}", packet.getPacketId(), error);
                    MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "客服持久化异常: " + error.getMessage(), packet), MessageEventTypeEnum.EXCEPTION), true);
                    releaseQosOnFailure(packet);
                });

    }

    /** 后续 pipeline 必须回到该连接的 EventLoop，避免跨线程 fireChannelRead。 */
    private static void fireReadOnEventLoop(ChannelHandlerContext ctx, Packet packet) {
        if (ctx.executor().inEventLoop()) {
            ctx.fireChannelRead(packet);
            return;
        }
        ctx.executor().execute(() -> ctx.fireChannelRead(packet));
    }


    private PrepareOutcome validateAndPrepare(Packet packet) {
        if (!MessageRefHelper.normalizeMessageRefOrReject(packet)) {
            releaseQosOnFailure(packet);
            return PrepareOutcome.reject("引用校验失败");
        }
        PrepareOutcome outcome = CsHelper.prepare(packet);
        if (!outcome.accepted()) {
            log.warn("客服会话路由校验失败: {} | packetId={}", outcome.rejectReason(), packet.getPacketId());
            CsHelper.publishReject(packet, outcome.rejectReason());
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
        String ticketScopeId = CsHelper.ticketMessageScopeId(route);
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
                            CsHelper.deliverMessage(packet0, route, true);
                            if (StringUtils.isNoneBlank(ticketScopeId, appKey)) {
                                repository().refreshCsTicketLastMessageAfterWithdraw(appKey, ticketScopeId);
                            }
                            fireReadOnEventLoop(ctx0, packet0);
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
        String ticketScopeId = CsHelper.ticketMessageScopeId(route);
        repository().reactiveHandleOperation(ctx, packet,
                        repository().reactiveLoadValidatedCsReadReceiptPackets(
                                packet, route, packet.getDeviceType()),
                        ExceptionCodeEnum.READ_RECEIPT_MESSAGE_VERIFY_ERROR,
                        () -> repository().savePacket2Mq(MqConstant.MQ_READ_RECEIPT_MESSAGE_TOPIC, ticketScopeId, packet),
                        packets -> repository().reactiveCsReadReceiptMessage(
                                packet, route, packet.getDeviceType(),
                                MessageConstant.CACHE_MESSAGE_READ_RECEIPT_KEY_EXPIRE_TIMESTAMP, packets),
                        (ctx0, packet0) -> {
                            qosAckOnSuccess(ctx0, packet0);
                            CsHelper.deliverMessage(packet0, route);
                            fireReadOnEventLoop(ctx0, packet0);
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

    private boolean processWithContentProcessor(ChannelHandlerContext ctx, Packet packet) {
        AbstractBaseBiProcessor<? extends Number> processor =
                MessageServerContext.messageContentProcessorCache.get(packet.getMessage().getContentType());
        if (processor != null) {
            processor.process(ctx, packet);
            return true;
        }
        return false;
    }
}
