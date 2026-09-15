package com.ouyunc.message.handler;

import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ChannelOrderedInbound;
import com.ouyunc.message.helper.ChannelOrderedTasks;
import com.ouyunc.message.processor.AbstractMessageBiProcessor;
import com.ouyunc.message.validator.DeviceValidator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 消息前置处理器：设备校验后，PING 留在 EventLoop；其余 pre+process 合并为同一连接有序任务。
 * <p>
 * 同连接一条业务消息只入队一次，避免 A-pre → B-pre → A-process 交错；
 * PRE 阶段通过 {@link ChannelOrderedInbound} 抑制向 PacketHandler 的二次入队。
 * </p>
 **/
public class PacketPreHandler extends SimpleChannelInboundHandler<Packet> {
    private static final Logger log = LoggerFactory.getLogger(PacketPreHandler.class);

    /**
     * @param ctx
     * @param packet
     * @return void
     * @Author fangzhenxun
     * @Description 登录逻辑处理
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        // 先在这里判断是否支持设备类型
        if (DeviceValidator.INSTANCE.negate().verify(packet,ctx)) {
            log.error("设备类型不支持，deviceType= {}, appKey:{}", packet.getDeviceType(), packet.getMessage().getMetadata().getAppKey());
            MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.ILLEGAL_DEVICE_TYPE_ERROR, null, packet), MessageEventTypeEnum.EXCEPTION), true);
            ctx.close();
            return;
        }
        // 所有的消息包处理，都是以消息类型为基准，在消息前置处理器中去处理，做认证和鉴权
        AbstractMessageBiProcessor<? extends Number> messageProcessor = MessageServerContext.messageProcessorCache.get(packet.getMessageType());
        if (messageProcessor == null) {
            log.error("非法消息类型，messageType= {}", packet.getMessageType());
            MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.ILLEGAL_MESSAGE_TYPE_ERROR, null, packet), MessageEventTypeEnum.EXCEPTION), true);
            ctx.close();
            return;
        }
        // 判断是否开启外部客户端心跳，如果没开启但是发送了心跳类型的消息，则关闭channel
        if (!MessageServerContext.serverProperties().isClientHeartBeatEnable() && packet.getMessageType() == MessageTypeEnum.PING_PONG.getType()) {
            log.error("外部客户端未开启心跳, 非法消息类型，messageType= {}", packet.getMessageType());
            MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.ILLEGAL_MESSAGE_TYPE_ERROR, "外部客户端未开启心跳, 非法消息类型", packet), MessageEventTypeEnum.EXCEPTION), true);
            ctx.close();
            return;
        }
        if (packet.getMessageType() == MessageTypeEnum.PING_PONG.getType()) {
            messageProcessor.preProcess(ctx, packet);
            return;
        }
        // 一条业务消息：preProcessStage →（通过后）processStage，同一有序任务内完成
        ChannelOrderedTasks.executeAsync(ctx.channel(), () -> runFullInbound(ctx, packet, messageProcessor));
    }

    /**
     * 有序全量入站：PRE 抑制 fire；校验通过再切 PROCESS 跑业务。
     */
    private static CompletionStage<Void> runFullInbound(ChannelHandlerContext ctx, Packet packet,
                                                        AbstractMessageBiProcessor<? extends Number> messageProcessor) {
        if (!ctx.channel().isActive()) {
            return CompletableFuture.completedFuture(null);
        }
        ChannelOrderedInbound.beginPre(ctx.channel());
        Mono<Void> full = messageProcessor.preProcessStage(ctx, packet)
                .then(Mono.defer(() -> {
                    if (!ChannelOrderedInbound.consumePrePassed(ctx.channel())) {
                        return Mono.empty();
                    }
                    if (!ctx.channel().isActive()) {
                        return Mono.empty();
                    }
                    ChannelOrderedInbound.beginProcess(ctx.channel());
                    return messageProcessor.processStage(ctx, packet);
                }));
        return ChannelOrderedTasks.toVoidStage(full)
                .whenComplete((ignored, error) -> ChannelOrderedInbound.clear(ctx.channel()));
    }
}
