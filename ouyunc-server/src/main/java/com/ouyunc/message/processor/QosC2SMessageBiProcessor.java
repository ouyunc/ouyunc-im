package com.ouyunc.message.processor;

import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.QosModeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.schedule.ScheduleTimer;
import com.ouyunc.message.validator.AuthValidator;
import com.ouyunc.message.validator.PermissionValidator;
import com.ouyunc.repository.support.QosAckContentParser;
import org.apache.commons.lang3.StringUtils;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * @Author fzx
 * @Description: qos外部客户端已经收到消息,只有开启qos 且在服务端模式下才会处理相关逻辑
 **/
public final class QosC2SMessageBiProcessor extends AbstractMessageBiProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(QosC2SMessageBiProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.QOS_C2S_ACK;
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
        return fireWhenPassed(ctx, packet,
                PermissionValidator.INSTANCE.negate().verify(packet, ctx),
                null,
                "权限不足, 请知悉。该消息 {} 被忽略");
    }
    /**
     * 外部客户端接收到消息后，发送消息已接收给服务端，做消息已接收确认
     */
    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        if (MessageContext.isQosEnable() && QosModeEnum.SERVER.equals(MessageServerContext.serverProperties().getQosMode())) {
            Message message = packet.getMessage();
            String ackId = QosAckContentParser.resolveAckId(message.getContent());
            if (StringUtils.isNotBlank(ackId)) {
                ScheduleTimer.cancel(ackId);
            }
        }else {
            log.warn("QosC2SMessageProcessor qos未开启或者qos模式不是服务端模式,忽略处理");
        }

    }
}
