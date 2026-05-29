package com.ouyunc.message.processor;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.model.ClientInfo;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.domain.constants.IdentityType;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.validator.AuthValidator;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 客服会话消息处理器（MessageType=CUSTOMER_SERVICE）。
 * 免好友校验；支持撤回、已读；与单聊共用会话索引，不单独区分缓存。
 */
public final class CustomerServiceMessageProcessor extends AbstractMessageProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(CustomerServiceMessageProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.CUSTOMER_SERVICE;
    }

    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        repository().save(packet).whenComplete((sendResult, ex) -> {
            if (ex != null) {
                log.error("Failed to save packet: {}", ex.getMessage());
                MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.MQ_PERSISTENCE_ERROR, "通过发送mq保存消息异常!", packet), MessageEventTypeEnum.EXCEPTION), true);
                return;
            }
            if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
                log.error("客服消息校验失败: {} 认证未通过, 关闭 channel", packet);
                MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.LOGIN_AUTH_ERROR, "登录认证未通过!", packet), MessageEventTypeEnum.EXCEPTION), true);
                ctx.close();
                return;
            }
            if (qosPreHandleIfEnabled(ctx, packet)) {
                return;
            }
            ctx.fireChannelRead(packet);
        });
    }

    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        log.debug("Processing customer service message...");
        Message message = packet.getMessage();
        String sessionId = IdentityUtil.sessionId(message.getFrom(), message.getTo());
        int contentType = message.getContentType();

        if (MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType() == contentType) {
            handleReadReceipt(ctx, packet);
            return;
        }

        saveMessage(packet).subscribe(result -> {
            if (!result) {
                log.error("客服会话索引写入失败: {}", packet);
                publishCachePersistenceError(packet, "客服消息写入会话失败");
                return;
            }
            repository().saveLastMessageForSession(sessionId, packet, MessageConstant.CACHE_SESSION_LAST_MESSAGE_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
            advanceSenderReadOffsetOnSend(packet, IdentityType.ONE_2_ONE);
            if (MessageContentTypeEnum.WITHDRAW_CONTENT.getType() == contentType) {
                handleWithdrawMessage(ctx, packet);
            } else {
                deliverToUserAndService(packet, false);
                ctx.fireChannelRead(packet);
            }
        });
    }

    private void handleWithdrawMessage(ChannelHandlerContext ctx, Packet packet) {
        String from = packet.getMessage().getFrom();
        String to = packet.getMessage().getTo();
        String sessionId = IdentityUtil.sessionId(from, to);
        repository().reactiveHandleOperation(ctx, packet,
                repository().reactiveLoadWithdrawTargetPackets(packet, sessionId, true),
                ExceptionCodeEnum.WITHDRAW_MESSAGE_VERIFY_ERROR,
                () -> repository().savePacket2Mq(MqConstant.KAFKA_WITHDRAW_MESSAGE_TOPIC, sessionId, packet),
                packets -> repository().reactiveWithdrawMessage(packet, sessionId, packets),
                (ctx0, packet0) -> {
                    deliverToUserAndService(packet0, true);
                    ctx0.fireChannelRead(packet0);
                },
                (exceptionEvent) -> MessageServerContext.publishEvent(exceptionEvent, true),
                ExceptionCodeEnum.WITHDRAW_MESSAGE_ERROR)
                .subscribe();
    }

    private void handleReadReceipt(ChannelHandlerContext ctx, Packet packet) {
        String sessionId = IdentityUtil.sessionId(packet.getMessage().getFrom(), packet.getMessage().getTo());
        repository().reactiveHandleOperation(ctx, packet,
                repository().reactiveValidReadReceiptMessage(packet, sessionId, IdentityType.ONE_2_ONE, false),
                () -> repository().savePacket2Mq(MqConstant.KAFKA_READ_RECEIPT_MESSAGE_TOPIC, sessionId, packet),
                repository().reactiveReadReceiptMessage(packet, IdentityType.ONE_2_ONE, MessageConstant.CACHE_MESSAGE_READ_RECEIPT_KEY_EXPIRE_TIMESTAMP),
                (ctx0, packet0) -> completeReadReceipt(ctx0, packet0, () -> deliverReadReceiptToSender(packet0)),
                (exceptionEvent) -> MessageServerContext.publishEvent(exceptionEvent, true),
                ExceptionCodeEnum.READ_RECEIPT_MESSAGE_ERROR)
                .subscribe();
    }

    private void deliverReadReceiptToSender(Packet packet) {
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        List<LoginClientInfo> senderClients = ClientHelper.onlineAll(appKey, message.getTo());
        if (CollectionUtils.isNotEmpty(senderClients)) {
            MessageHelper.asyncSendMessage(packet, senderClients);
        }
    }

    private void deliverToUserAndService(Packet packet, boolean forceSelfSync) {
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        ClientInfo clientInfo = MessageServerContext.localClientInfo(appKey, message.getFrom());
        if (forceSelfSync || (clientInfo != null && clientInfo.getSelfSync())) {
            List<LoginClientInfo> fromLoginList = ClientHelper.onlineAll(appKey, message.getFrom(), MessageServerContext.deviceType(appKey, packet.getDeviceType()));
            if (CollectionUtils.isNotEmpty(fromLoginList)) {
                MessageHelper.asyncSendMessage(packet, fromLoginList);
            }
        }
        List<LoginClientInfo> toLoginList = ClientHelper.onlineAll(appKey, message.getTo());
        if (CollectionUtils.isNotEmpty(toLoginList)) {
            MessageHelper.asyncSendMessage(packet, toLoginList);
        } else {
            log.debug("客服接收方 {} 不在线，已写入会话索引，上线后拉取", message.getTo());
        }
    }

    private Mono<Boolean> saveMessage(Packet packet) {
        Message message = packet.getMessage();
        String sessionId = IdentityUtil.sessionId(message.getFrom(), message.getTo());
        return repository().reactiveSaveMessage(packet, sessionId, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP);
    }
}
