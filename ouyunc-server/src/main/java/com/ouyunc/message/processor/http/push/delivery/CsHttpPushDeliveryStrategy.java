package com.ouyunc.message.processor.http.push.delivery;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.message.helper.CsHelper;
import com.ouyunc.message.helper.CsHelper.PrepareOutcome;
import com.ouyunc.message.http.HttpPipelineException;
import com.ouyunc.message.processor.http.push.HttpPushFailures;
import com.ouyunc.message.processor.http.push.HttpPushValidatorChain;
import com.ouyunc.repository.DefaultRepository;
import com.ouyunc.repository.cs.CsImSessionRoute;
import com.ouyunc.repository.support.MessageIndexScope;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * HTTP 推送：客服会话投递（ticket 消息 scope），与 {@link com.ouyunc.message.processor.CsMessageBiProcessor} 行为对齐。
 */
public final class CsHttpPushDeliveryStrategy implements HttpProcessor {

    public static final CsHttpPushDeliveryStrategy INSTANCE = new CsHttpPushDeliveryStrategy();

    private static final Logger log = LoggerFactory.getLogger(CsHttpPushDeliveryStrategy.class);

    private CsHttpPushDeliveryStrategy() {
    }

    @Override
    public MessageTypeEnum messageType() {
        return MessageTypeEnum.CUSTOMER_SERVICE;
    }

    @Override
    public void preProcess(Packet packet) throws HttpPipelineException {
        HttpPushValidatorChain.verifyCustomerService(packet);
        HttpPushDeliverySupport.requireValidMessageRef(packet);
        PrepareOutcome prepared = CsHelper.prepare(packet);
        if (!prepared.accepted()) {
            throw HttpPushFailures.forbidden(packet, ExceptionCodeEnum.CS_SESSION_ROUTE_ERROR,
                    prepared.rejectReason() != null ? prepared.rejectReason() : "客服会话路由校验失败");
        }
        // process 复用，不再二次 prepare / getRoute 使用缓存即可
        HttpPushDeliverySupport.stashCsRoute(packet, prepared.route());
    }

    @Override
    public void process(Packet packet) {
        HttpPushDeliverySupport.subscribeDelivery(packet, doProcess(packet));
    }

    private Mono<Boolean> doProcess(Packet packet) {
        CsImSessionRoute route = HttpPushDeliverySupport.takeCsRoute(packet);
        if (route == null) {
            log.error("HTTP 推送客服缺少 preProcess 缓存的路由, packetId={}", packet.getPacketId());
            HttpPushDeliverySupport.publishException(ExceptionCodeEnum.CS_SESSION_ROUTE_ERROR,
                    "客服会话路由丢失", packet);
            return Mono.just(false);
        }
        PrepareOutcome live = CsHelper.refreshDelivery(packet, route);
        if (!live.accepted()) {
            HttpPushDeliverySupport.publishException(ExceptionCodeEnum.CS_SESSION_ROUTE_ERROR,
                    live.rejectReason() != null ? live.rejectReason() : "客服会话路由已失效", packet);
            return Mono.just(false);
        }
        route = live.route();
        CsHelper.rewriteAgentFrom(packet, route);
        Message message = packet.getMessage();
        int contentType = message.getContentType();
        if (MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType() == contentType) {
            return handleReadReceipt(packet, route);
        }
        if (MessageContentTypeEnum.WITHDRAW_CONTENT.getType() == contentType) {
            return saveThenWithdraw(packet, route);
        }
        return saveAndDeliverChat(packet, route);
    }

    private Mono<Boolean> saveAndDeliverChat(Packet packet, CsImSessionRoute route) {
        return DefaultRepository.INSTANCE.reactiveSaveCsTicketMessage(packet, route,
                        MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP)
                .flatMap(saved -> {
                    if (!Boolean.TRUE.equals(saved)) {
                        log.error("HTTP 推送客服消息落库失败: {}", packet);
                        HttpPushDeliverySupport.publishException(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR,
                                "客服消息写入 ticket 失败", packet);
                        return Mono.just(false);
                    }
                    CsHelper.saveChatLastMessage(DefaultRepository.INSTANCE, route, packet);
                    CsHelper.notifyAfterSave(packet, route);
                    DefaultRepository.INSTANCE.reactiveAdvanceCsSenderReadOffsetOnSend(
                                    packet, route, packet.getDeviceType(),
                                    MessageConstant.CACHE_MESSAGE_READ_RECEIPT_KEY_EXPIRE_TIMESTAMP)
                            .subscribe(ignored -> { }, e -> log.warn(
                                    "HTTP 推送更新客服 ticket 已读 offset 失败, packetId={}", packet.getPacketId(), e));
                    CsHelper.deliverMessage(packet, route, false);
                    return Mono.just(true);
                })
                .onErrorResume(error -> {
                    log.error("HTTP 推送客服落库异常, packetId={}", packet.getPacketId(), error);
                    HttpPushDeliverySupport.publishException(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR,
                            "客服持久化异常: " + error.getMessage(), packet);
                    return Mono.just(false);
                });
    }

    private Mono<Boolean> saveThenWithdraw(Packet packet, CsImSessionRoute route) {
        return DefaultRepository.INSTANCE.reactiveSaveCsTicketMessage(packet, route,
                        MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP)
                .flatMap(saved -> {
                    if (!Boolean.TRUE.equals(saved)) {
                        log.error("HTTP 推送客服撤回消息落库失败: {}", packet);
                        HttpPushDeliverySupport.publishException(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR,
                                "客服撤回消息写入 ticket 失败", packet);
                        return Mono.just(false);
                    }
                    return handleWithdraw(packet, route);
                })
                .onErrorResume(error -> {
                    log.error("HTTP 推送客服撤回落库异常, packetId={}", packet.getPacketId(), error);
                    HttpPushDeliverySupport.publishException(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR,
                            "客服撤回持久化异常: " + error.getMessage(), packet);
                    return Mono.just(false);
                });
    }

    private Mono<Boolean> handleWithdraw(Packet packet, CsImSessionRoute route) {
        String ticketScopeId = CsHelper.ticketMessageScopeId(route);
        String appKey = packet.getMessage().getMetadata().getAppKey();
        return DefaultRepository.INSTANCE.reactiveHandleOperation(null, packet,
                        DefaultRepository.INSTANCE.reactiveLoadWithdrawTargetPackets(
                                packet, ticketScopeId, MessageIndexScope.CS_TICKET, true),
                        ExceptionCodeEnum.WITHDRAW_MESSAGE_VERIFY_ERROR,
                        MqConstant.MQ_WITHDRAW_MESSAGE_TOPIC, ticketScopeId,
                        packets -> DefaultRepository.INSTANCE.reactiveWithdrawMessage(
                                packet, ticketScopeId, MessageIndexScope.CS_TICKET, packets),
                        (ctx, packet0) -> {
                            CsHelper.deliverMessage(packet0, route, true);
                            if (StringUtils.isNoneBlank(ticketScopeId, appKey)) {
                                DefaultRepository.INSTANCE.refreshCsTicketLastMessageAfterWithdraw(appKey, ticketScopeId);
                            }
                        },
                        HttpPushDeliverySupport::publishExceptionEvent,
                        ExceptionCodeEnum.WITHDRAW_MESSAGE_ERROR)
                .map(Boolean.TRUE::equals);
    }

    private Mono<Boolean> handleReadReceipt(Packet packet, CsImSessionRoute route) {
        String ticketScopeId = CsHelper.ticketMessageScopeId(route);
        return DefaultRepository.INSTANCE.reactiveHandleOperation(null, packet,
                        DefaultRepository.INSTANCE.reactiveLoadValidatedCsReadReceiptPackets(
                                packet, route, packet.getDeviceType()),
                        ExceptionCodeEnum.READ_RECEIPT_MESSAGE_VERIFY_ERROR,
                        MqConstant.MQ_READ_RECEIPT_MESSAGE_TOPIC, ticketScopeId,
                        packets -> DefaultRepository.INSTANCE.reactiveCsReadReceiptMessage(
                                packet, route, packet.getDeviceType(),
                                MessageConstant.CACHE_MESSAGE_READ_RECEIPT_KEY_EXPIRE_TIMESTAMP, packets),
                        (ctx, packet0) -> CsHelper.deliverMessage(packet0, route),
                        HttpPushDeliverySupport::publishExceptionEvent,
                        ExceptionCodeEnum.READ_RECEIPT_MESSAGE_ERROR)
                .map(Boolean.TRUE::equals);
    }
}
