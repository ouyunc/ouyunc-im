package com.ouyunc.message.processor.http.push.delivery;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.IdentityType;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.core.exception.ExceptionReporter;
import com.ouyunc.message.helper.AtMentionHelper;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageDeliveryRouteHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.http.HttpPipelineException;
import com.ouyunc.message.processor.http.push.HttpPushValidatorChain;
import com.ouyunc.repository.DefaultRepository;
import com.ouyunc.repository.support.MessageIndexScope;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * HTTP 推送：单聊投递（模拟用户，与 {@link com.ouyunc.message.processor.One2OneMessageBiProcessor} 对齐）。
 */
public final class One2OneHttpPushDeliveryStrategy implements HttpProcessor {

    public static final One2OneHttpPushDeliveryStrategy INSTANCE = new One2OneHttpPushDeliveryStrategy();

    private static final Logger log = LoggerFactory.getLogger(One2OneHttpPushDeliveryStrategy.class);

    private One2OneHttpPushDeliveryStrategy() {
    }

    @Override
    public MessageTypeEnum messageType() {
        return MessageTypeEnum.ONE_2_ONE;
    }

    @Override
    public void preProcess(Packet packet) throws HttpPipelineException {
        HttpPushValidatorChain.verifyOne2One(packet);
        HttpPushDeliverySupport.requireValidMessageRef(packet);
        AtMentionHelper.clearAtIfPresent(packet.getMessage());
    }

    @Override
    public Mono<Boolean> processMono(Packet packet) {
        // preProcess 已完成业务校验与 ref 规范化
        int contentType = packet.getMessage().getContentType();
        if (MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType() == contentType) {
            return handleReadReceipt(packet);
        }
        if (MessageContentTypeEnum.WITHDRAW_CONTENT.getType() == contentType) {
            Message message = packet.getMessage();
            return handleWithdraw(packet, IdentityUtil.sessionId(message.getFrom(), message.getTo()));
        }
        return saveAndDeliverChat(packet);
    }


    private Mono<Boolean> saveAndDeliverChat(Packet packet) {
        Message message = packet.getMessage();
        String sessionId = IdentityUtil.sessionId(message.getFrom(), message.getTo());
        return DefaultRepository.INSTANCE.reactiveSaveOne2OneMessage(packet, sessionId,
                        MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP)
                .flatMap(outcome -> {
                    if (outcome != null && outcome.isDuplicate()) {
                        return replayOnline(packet);
                    }
                    if (outcome == null || !outcome.isFreshWrite()) {
                        log.error("HTTP 推送单聊落库失败: {}", packet);
                        HttpPushDeliverySupport.publishException(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR,
                                "单聊消息写入会话失败", packet);
                        return Mono.just(false);
                    }
                    try {
                        DefaultRepository.INSTANCE.saveLastMessageForSession(sessionId, packet,
                                MessageConstant.CACHE_SESSION_LAST_MESSAGE_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
                    } catch (Exception e) {
                        log.warn("HTTP 推送更新单聊最后消息失败，继续投递 packetId={}", packet.getPacketId(), e);
                    }
                    DefaultRepository.INSTANCE.reactiveAdvanceSenderReadOffsetOnSend(packet, IdentityType.ONE_2_ONE,
                                    MessageConstant.CACHE_MESSAGE_READ_RECEIPT_KEY_EXPIRE_TIMESTAMP)
                            .subscribe(ignored -> { }, e -> log.warn(
                                    "HTTP 推送更新单聊已读 offset 失败, packetId={}", packet.getPacketId(), e));
                    MessageDeliveryRouteHelper.deliverPeerMessage(packet, false);
                    return Mono.just(true);
                })
                .onErrorResume(error -> {
                    log.error("HTTP 推送单聊落库异常, packetId={}", packet.getPacketId(), error);
                    ExceptionReporter.reportSystem(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR,
                            "单聊持久化异常: " + error.getMessage(),
                            "One2OneHttpPushDeliveryStrategy.process", packet, error);
                    return Mono.just(false);
                });
    }

    private Mono<Boolean> handleWithdraw(Packet packet, String sessionId) {
        return DefaultRepository.INSTANCE.reactiveHandleOperation(null, packet,
                        DefaultRepository.INSTANCE.reactiveLoadWithdrawTargetPackets(
                                packet, sessionId, MessageIndexScope.CHANNEL_SESSION, true),
                        ExceptionCodeEnum.WITHDRAW_MESSAGE_VERIFY_ERROR,
                        MqConstant.MQ_WITHDRAW_MESSAGE_TOPIC, sessionId,
                        packets -> DefaultRepository.INSTANCE.reactiveWithdrawMessage(
                                packet, sessionId, MessageIndexScope.CHANNEL_SESSION, packets),
                        (ctx, packet0) -> {
                            Message msg = packet0.getMessage();
                            if (msg != null && msg.getMetadata() != null) {
                                String appKey = msg.getMetadata().getIngress().getAppKey();
                                if (StringUtils.isNoneBlank(appKey, sessionId)) {
                                    DefaultRepository.INSTANCE.refreshSessionLastMessageAfterWithdraw(appKey, sessionId);
                                }
                            }
                            MessageDeliveryRouteHelper.deliverPeerMessage(packet0, true);
                        },
                        ExceptionCodeEnum.WITHDRAW_MESSAGE_ERROR)
                .map(Boolean.TRUE::equals);
    }

    private Mono<Boolean> handleReadReceipt(Packet packet) {
        String sessionId = IdentityUtil.sessionId(packet.getMessage().getFrom(), packet.getMessage().getTo());
        return DefaultRepository.INSTANCE.reactiveHandleOperation(null, packet,
                        DefaultRepository.INSTANCE.reactiveLoadValidatedReadReceiptPackets(
                                packet, sessionId, IdentityType.ONE_2_ONE, false),
                        ExceptionCodeEnum.READ_RECEIPT_MESSAGE_VERIFY_ERROR,
                        MqConstant.MQ_READ_RECEIPT_MESSAGE_TOPIC, sessionId,
                        packets -> DefaultRepository.INSTANCE.reactiveReadReceiptMessage(
                                packet, IdentityType.ONE_2_ONE,
                                MessageConstant.CACHE_MESSAGE_READ_RECEIPT_KEY_EXPIRE_TIMESTAMP, packets),
                        (ctx, packet0) -> deliverReadReceiptToSender(packet0),
                        ExceptionCodeEnum.READ_RECEIPT_MESSAGE_ERROR)
                .map(Boolean.TRUE::equals);
    }

    @Override
    public Mono<Boolean> replayOnline(Packet packet) {
        return Mono.fromCallable(() -> {
            int contentType = packet.getMessage().getContentType();
            if (MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType() == contentType) {
                deliverReadReceiptToSender(packet);
            } else {
                MessageDeliveryRouteHelper.deliverPeerMessage(packet,
                        MessageContentTypeEnum.WITHDRAW_CONTENT.getType() == contentType);
            }
            return Boolean.TRUE;
        });
    }

    private static void deliverReadReceiptToSender(Packet packet) {
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getIngress().getAppKey();
        List<LoginClientInfo> senderClients = ClientHelper.onlineAll(appKey, message.getTo());
        if (CollectionUtils.isNotEmpty(senderClients)) {
            MessageHelper.asyncSendMessage(packet, senderClients);
        }
    }
}
