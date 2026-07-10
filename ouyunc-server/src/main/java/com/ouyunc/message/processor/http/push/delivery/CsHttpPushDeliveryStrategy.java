package com.ouyunc.message.processor.http.push.delivery;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.CsCustomerServiceLastMessageHelper;
import com.ouyunc.message.helper.CsMessageDeliveryRouteHelper;
import com.ouyunc.message.helper.CsSessionMessageHelper;
import com.ouyunc.message.helper.CsSessionMessageHelper.PrepareOutcome;
import com.ouyunc.message.helper.CsTicketActivityNotifyHelper;
import com.ouyunc.message.helper.MessageRefHelper;
import com.ouyunc.repository.DefaultRepository;
import com.ouyunc.repository.cs.CsImSessionRoute;
import com.ouyunc.repository.cs.CsMessageScopeHelper;
import com.ouyunc.repository.support.MessageIndexScope;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP 推送：客服会话投递（ticket 消息 scope），与 {@link com.ouyunc.message.processor.CsMessageBiProcessor} 行为对齐。
 */
public enum CsHttpPushDeliveryStrategy implements HttpProcessor {

    INSTANCE;

    private static final Logger log = LoggerFactory.getLogger(CsHttpPushDeliveryStrategy.class);

    @Override
    public MessageTypeEnum messageType() {
        return MessageTypeEnum.CUSTOMER_SERVICE;
    }

    @Override
    public void process(Packet packet) {
        if (!MessageRefHelper.normalizeMessageRefOrReject(packet)) {
            return;
        }
        Message message = packet.getMessage();
        if (message == null || StringUtils.isAnyBlank(message.getFrom(), message.getTo())
                || StringUtils.equals(message.getFrom(), message.getTo())) {
            log.warn("HTTP 推送客服消息 from/to 无效, packetId={}", packet.getPacketId());
            HttpPushDeliverySupport.publishException(ExceptionCodeEnum.CS_SESSION_ROUTE_ERROR, "from/to 无效", packet);
            return;
        }
        PrepareOutcome prepared = CsSessionMessageHelper.prepare(packet);
        if (!prepared.accepted()) {
            log.warn("HTTP 推送客服路由校验失败: {}", prepared.rejectReason());
            HttpPushDeliverySupport.publishException(ExceptionCodeEnum.CS_SESSION_ROUTE_ERROR, prepared.rejectReason(), packet);
            return;
        }
        CsImSessionRoute route = prepared.route();
        int contentType = message.getContentType();
        if (MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType() == contentType) {
            handleReadReceipt(packet, route);
            return;
        }
        if (MessageContentTypeEnum.WITHDRAW_CONTENT.getType() == contentType) {
            handleWithdraw(packet, route);
            return;
        }
        saveAndDeliverChat(packet, route);
    }

    private void saveAndDeliverChat(Packet packet, CsImSessionRoute route) {
        DefaultRepository.INSTANCE.reactiveSaveCsTicketMessage(packet, route,
                        MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP)
                .subscribe(
                        saved -> {
                            if (!Boolean.TRUE.equals(saved)) {
                                log.error("HTTP 推送客服消息落库失败: {}", packet);
                                HttpPushDeliverySupport.publishException(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR,
                                        "客服消息写入 ticket 失败", packet);
                                return;
                            }
                            CsCustomerServiceLastMessageHelper.saveChatLastMessage(
                                    DefaultRepository.INSTANCE, route, packet);
                            CsTicketActivityNotifyHelper.notifyAfterSave(packet, route);
                            DefaultRepository.INSTANCE.reactiveAdvanceCsSenderReadOffsetOnSend(
                                            packet, route, packet.getDeviceType(),
                                            MessageConstant.CACHE_MESSAGE_READ_RECEIPT_KEY_EXPIRE_TIMESTAMP)
                                    .subscribe(ignored -> { }, e -> log.warn(
                                            "HTTP 推送更新客服 ticket 已读 offset 失败, packetId={}", packet.getPacketId(), e));
                            CsMessageDeliveryRouteHelper.deliverCustomerServiceMessage(packet, route, false);
                        },
                        error -> {
                            log.error("HTTP 推送客服落库异常, packetId={}", packet.getPacketId(), error);
                            HttpPushDeliverySupport.publishException(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR,
                                    "客服持久化异常: " + error.getMessage(), packet);
                        });
    }

    private void handleWithdraw(Packet packet, CsImSessionRoute route) {
        String ticketScopeId = CsMessageScopeHelper.ticketMessageScopeId(route);
        String appKey = packet.getMessage().getMetadata().getAppKey();
        DefaultRepository.INSTANCE.reactiveHandleOperation(null, packet,
                        DefaultRepository.INSTANCE.reactiveLoadWithdrawTargetPackets(
                                packet, ticketScopeId, MessageIndexScope.CS_TICKET, true),
                        ExceptionCodeEnum.WITHDRAW_MESSAGE_VERIFY_ERROR,
                        () -> DefaultRepository.INSTANCE.savePacket2Mq(MqConstant.MQ_WITHDRAW_MESSAGE_TOPIC, ticketScopeId, packet),
                        packets -> DefaultRepository.INSTANCE.reactiveWithdrawMessage(
                                packet, ticketScopeId, MessageIndexScope.CS_TICKET, packets),
                        (ctx, packet0) -> {
                            CsMessageDeliveryRouteHelper.deliverCustomerServiceMessage(packet0, route, true);
                            if (StringUtils.isNoneBlank(ticketScopeId, appKey)) {
                                DefaultRepository.INSTANCE.refreshCsTicketLastMessageAfterWithdraw(appKey, ticketScopeId);
                            }
                        },
                        CsHttpPushDeliveryStrategy::publishExceptionEvent,
                        ExceptionCodeEnum.WITHDRAW_MESSAGE_ERROR)
                .subscribe();
    }

    private void handleReadReceipt(Packet packet, CsImSessionRoute route) {
        String ticketScopeId = CsMessageScopeHelper.ticketMessageScopeId(route);
        if (packet.getMessage().getMetadata() != null) {
            packet.getMessage().getMetadata().setCsReaderId(
                    CsMessageScopeHelper.resolveReaderId(packet.getMessage(), route));
        }
        DefaultRepository.INSTANCE.reactiveHandleOperation(null, packet,
                        DefaultRepository.INSTANCE.reactiveValidCsReadReceiptMessage(packet, route, packet.getDeviceType()),
                        () -> DefaultRepository.INSTANCE.savePacket2Mq(MqConstant.MQ_READ_RECEIPT_MESSAGE_TOPIC, ticketScopeId, packet),
                        DefaultRepository.INSTANCE.reactiveCsReadReceiptMessage(
                                packet, route, packet.getDeviceType(),
                                MessageConstant.CACHE_MESSAGE_READ_RECEIPT_KEY_EXPIRE_TIMESTAMP),
                        (ctx, packet0) -> CsMessageDeliveryRouteHelper.deliverReadReceiptToOriginalSender(packet0, route),
                        CsHttpPushDeliveryStrategy::publishExceptionEvent,
                        ExceptionCodeEnum.READ_RECEIPT_MESSAGE_ERROR)
                .subscribe();
    }

    private static void publishExceptionEvent(MessageEvent event) {
        MessageServerContext.publishEvent(event, true);
    }
}
