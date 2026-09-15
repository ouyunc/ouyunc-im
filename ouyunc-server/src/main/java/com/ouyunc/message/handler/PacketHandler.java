package com.ouyunc.message.handler;

import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ChannelOrderedTasks;
import com.ouyunc.message.processor.AbstractMessageBiProcessor;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * packet 业务逻辑处理器。
 * <ul>
 *   <li>PING：留在 EventLoop（由 PacketPreHandler fire 进入）</li>
 *   <li>集群 OUYUNC 直连本 Handler：仅 processStage 入有序队列（无 PreHandler）</li>
 *   <li>客户端 WS/MQTT 业务包：已在 PacketPreHandler 同任务完成 pre+process，不应再经本 Handler 入队</li>
 * </ul>
 **/
public class PacketHandler extends SimpleChannelInboundHandler<Packet> {
    private static final Logger log = LoggerFactory.getLogger(PacketHandler.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
        AbstractMessageBiProcessor<? extends Number> messageProcessor =
                MessageServerContext.messageProcessorCache.get(packet.getMessageType());
        if (messageProcessor == null) {
            log.error("非法消息类型，messageType= {}", packet.getMessageType());
            MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(
                    ExceptionCodeEnum.ILLEGAL_MESSAGE_TYPE_ERROR, "非法消息类型", packet),
                    MessageEventTypeEnum.EXCEPTION), true);
            ctx.close();
            return;
        }
        if (packet.getMessageType() == MessageTypeEnum.PING_PONG.getType()) {
            messageProcessor.process(ctx, packet);
            return;
        }
        // 集群内部等无 PreHandler 的路径：单次 process 入队
        ChannelOrderedTasks.executeAsync(ctx.channel(), () -> {
            if (!ctx.channel().isActive()) {
                return CompletableFuture.completedFuture(null);
            }
            return ChannelOrderedTasks.toVoidStage(messageProcessor.processStage(ctx, packet));
        });
    }
}
