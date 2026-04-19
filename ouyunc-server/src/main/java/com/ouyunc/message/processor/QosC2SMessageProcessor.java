package com.ouyunc.message.processor;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.QosModeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.QosAckContent;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.schedule.ScheduleTimer;
import com.ouyunc.message.validator.AuthValidator;
import com.ouyunc.message.validator.PermissionValidator;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * @Author fzx
 * @Description: qos外部客户端已经收到消息,只有开启qos 且在服务端模式下才会处理相关逻辑
 **/
public final class QosC2SMessageProcessor extends AbstractMessageProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(QosC2SMessageProcessor.class);

    @Override
    public MessageType type() {
        return MessageTypeEnum.QOS_C2S_ACK;
    }

    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        // 异步存储packet（目前只是保存相关信息，不做扩展，以后可以做数据分析使用），这里将该数据存储到时序数据库中
        repository().save(packet).whenComplete((sendResult, ex) -> {
            if (ex == null) {
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
            } else {
                // 发送失败
                log.error("Failed to send message: {} ", ex.getMessage());
                MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.MQ_PERSISTENCE_ERROR, "通过发送mq保存消息异常!", packet), MessageEventTypeEnum.EXCEPTION), true);
            }
        });
    }
    /**
     * 外部客户端接收到消息后，发送消息已接收给服务端，做消息已接收确认
     */
    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        if (MessageServerContext.serverProperties().isQosEnable() && QosModeEnum.SERVER.equals(MessageServerContext.serverProperties().getQosMode())) {
            Message message = packet.getMessage();
            // 可以判断这个receivedPackageId是否合法，不是自己发送的，以及消息类型是合法的，这里不做过多的判断
            QosAckContent qosAckContent = JSON.parseObject(message.getContent(), QosAckContent.class);
            // 移除离线消息,通过异步发送移除离线消息事件
            MessageServerContext.publishEvent(new MessageEvent(packet, MessageEventTypeEnum.REMOVE_OFFLINE), true);
            // 停止本地的qos定时任务
            ScheduleTimer.cancel(qosAckContent.getAckId());
        }else {
            log.warn("QosC2SMessageProcessor qos未开启或者qos模式不是服务端模式,忽略处理");
        }

    }
}
