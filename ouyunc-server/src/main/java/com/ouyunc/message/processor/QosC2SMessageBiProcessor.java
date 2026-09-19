package com.ouyunc.message.processor;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.QosModeEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.QosAckContent;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.schedule.QosRetryScheduler;
import com.ouyunc.message.validator.AuthValidator;
import com.ouyunc.message.validator.PermissionValidator;
import com.ouyunc.repository.support.QosAckContentParser;
import org.apache.commons.lang3.StringUtils;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * qos外部客户端已经收到消息,只有开启qos 且在服务端模式下才会处理相关逻辑。
 * ACK 取消重试必须绑定已认证 Channel 的 identity + deviceType。
 * C2S 正文固定 JSON：ackId=下行 packetId，messageId=原客户端消息 id。
 * 任务在始发节点：本机有则取消，否则按 originServerAddress 转集群包给 {@link ClusterQosRetryCancelMessageBiProcessor}。
 * 不给接收方回 S2C。C2S 控制包应为 qos=0。
 **/
public final class QosC2SMessageBiProcessor extends AbstractMessageBiProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(QosC2SMessageBiProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.QOS_C2S_ACK;
    }

    @Override
    public Mono<Boolean> preProcess(ChannelHandlerContext ctx, Packet packet) {
        if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
            log.error("校验消息失败: {} 认证未通过,开始关闭channel", packet);
            MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.LOGIN_AUTH_ERROR, "登录认证未通过!", packet), MessageEventTypeEnum.EXCEPTION), true);
            ctx.close();
            return Mono.just(false);
        }
        // QoS ACK 不做旁路归档（不是业务聊天消息）
        return PermissionValidator.INSTANCE.negate().verify(packet, ctx)
                .onErrorReturn(true)
                .map(reject -> {
                    if (Boolean.TRUE.equals(reject)) {
                        log.warn("权限不足, 请知悉。该消息 {} 被忽略", packet);
                        return false;
                    }
                    return true;
                });
    }

    /**
     * 外部客户端接收到消息后，发送消息已接收给服务端，做消息已接收确认
     */
    @Override
    public Mono<Void> process(ChannelHandlerContext ctx, Packet packet) {
        return Mono.fromRunnable(() -> {
            if (MessageContext.isQosEnable() && QosModeEnum.SERVER.equals(MessageServerContext.serverProperties().getQosMode())) {
                LoginClientInfo login = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
                if (login == null || StringUtils.isAnyBlank(login.getAppKey(), login.getIdentity())) {
                    log.warn("QoS ACK 缺少已认证登录身份，忽略取消重试");
                    return;
                }
                Message message = packet.getMessage();
                if (message == null || message.getContentType() != MessageContentTypeEnum.QOS_ACK_CONTENT.getType()) {
                    log.warn("QoS ACK contentType 非法, contentType={}", message != null ? message.getContentType() : null);
                    return;
                }
                String content = message.getContent();
                QosAckContent ack = QosAckContentParser.parse(content);
                if (ack == null) {
                    log.warn("QoS ACK 无法解析 JSON, content={}", content);
                    return;
                }
                long packetId = QosAckContentParser.toPacketId(ack.getAckId());
                if (packetId <= 0) {
                    log.warn("QoS ACK ackId 非法, content={}", content);
                    return;
                }
                QosRetryScheduler.onClientAck(login, packetId);
            } else if (MessageContext.isQosEnable()) {
                log.debug("客户端 QoS 模式不在服务端取消重试，忽略 C2S");
            } else {
                log.warn("QosC2SMessageProcessor qos未开启,忽略处理");
            }
            });
    }
}
