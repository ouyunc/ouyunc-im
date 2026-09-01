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
        // 异步存储packet（目前只是保存相关信息，不做扩展，以后可以做数据分析使用），这里将该数据存储到时序数据库中
        repository().save(packet);
        // 两个都校验通过才放行
        if (!AuthValidator.INSTANCE.verify(packet, ctx)) {
            // 关闭当前 channel，这里会触发 DefaultSocketChannelInitializer 中的关闭逻辑
            log.error("校验消息失败: {} 认证未通过,开始关闭channel", packet);
            MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.LOGIN_AUTH_ERROR, "登录认证未通过!", packet), MessageEventTypeEnum.EXCEPTION), true);
            ctx.close();
            return;
        }
        // 构建校验逻辑
        PermissionValidator.INSTANCE.negate()
                    .verify(packet, ctx)
                    .onErrorResume(error -> {
                        log.error("校验过程中出现异常: {}", error.getMessage());
                        return Mono.just(true); // 出现异常时默认校验不通过
                    }).flatMap(result -> {
                        if (result) {
                            log.warn("权限不足, 请知悉。该消息 {} 被忽略", packet);
                            return Mono.empty(); // 校验不通过，不传递消息
                        }
                        return Mono.just(packet); // 校验通过，继续传递消息
                    }).subscribe(ctx::fireChannelRead);
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
