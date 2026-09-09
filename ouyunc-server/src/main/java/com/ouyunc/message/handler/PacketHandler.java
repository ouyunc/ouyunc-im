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

/**
 * packet 业务逻辑处理器。PING 留在 EventLoop；其余按连接串行下沉，避免 groupUsersIdentity 等同步 Redis 堵 IO。
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
        ChannelOrderedTasks.execute(ctx.channel(), () -> {
            if (!ctx.channel().isActive()) {
                return;
            }
            messageProcessor.process(ctx, packet);
        });
    }
}
