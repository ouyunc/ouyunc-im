package com.ouyunc.message.handler;


import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.processor.AbstractMessageProcessor;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fzx
 * @Description: 消息的后置处理器
 **/
public class PacketPostHandler extends SimpleChannelInboundHandler<Packet> {
    private static final Logger log = LoggerFactory.getLogger(PacketPostHandler.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        AbstractMessageProcessor<? extends Number> messageProcessor = MessageServerContext.messageProcessorCache.get(packet.getMessageType());
        if (messageProcessor == null) {
            log.error("非法消息类型，messageType= {}", packet.getMessageType());
            MessageServerContext.publishEvent(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.ILLEGAL_MESSAGE_TYPE_ERROR, "非法消息类型", packet), MessageEventTypeEnum.EXCEPTION), true);
            ctx.close();
            return;
        }
        messageProcessor.postProcess(ctx, packet);
    }


}
