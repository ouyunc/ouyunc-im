package com.ouyunc.message.processor.http.push.delivery;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.base.constant.enums.IdentityType;
import com.ouyunc.message.helper.AtMentionHelper;
import com.ouyunc.message.helper.MessageDeliveryRouter;
import com.ouyunc.message.helper.MessageRefHelper;
import com.ouyunc.repository.DefaultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * HTTP 推送：单聊投递。
 */
public enum One2OneHttpPushDeliveryStrategy implements HttpProcessor {

    INSTANCE;

    private static final Logger log = LoggerFactory.getLogger(One2OneHttpPushDeliveryStrategy.class);

    @Override
    public MessageTypeEnum messageType() {
        return MessageTypeEnum.ONE_2_ONE;
    }

    @Override
    public void process(Packet packet) {
        AtMentionHelper.clearAtIfPresent(packet.getMessage());
        if (!MessageRefHelper.normalizeMessageRefOrReject(packet)) {
            return;
        }
        Message message = packet.getMessage();
        String sessionId = IdentityUtil.sessionId(message.getFrom(), message.getTo());
        DefaultRepository.INSTANCE.reactiveSaveOne2OneMessage(packet, sessionId,
                        MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP)
                .subscribe(
                        saved -> {
                            if (!Boolean.TRUE.equals(saved)) {
                                log.error("HTTP 推送单聊落库失败: {}", packet);
                                HttpPushDeliverySupport.publishException(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR,
                                        "单聊消息写入会话失败", packet);
                                return;
                            }
                            DefaultRepository.INSTANCE.saveLastMessageForSession(sessionId, packet,
                                    MessageConstant.CACHE_SESSION_LAST_MESSAGE_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
                            DefaultRepository.INSTANCE.reactiveAdvanceSenderReadOffsetOnSend(packet, IdentityType.ONE_2_ONE,
                                            MessageConstant.CACHE_MESSAGE_READ_RECEIPT_KEY_EXPIRE_TIMESTAMP)
                                    .subscribe(ignored -> { }, e -> log.warn(
                                            "HTTP 推送更新单聊已读 offset 失败, packetId={}", packet.getPacketId(), e));
                            MessageDeliveryRouter.deliverPeerMessage(packet, false);
                        },
                        error -> {
                            log.error("HTTP 推送单聊落库异常, packetId={}", packet.getPacketId(), error);
                            HttpPushDeliverySupport.publishException(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR,
                                    "单聊持久化异常: " + error.getMessage(), packet);
                        });
    }
}
