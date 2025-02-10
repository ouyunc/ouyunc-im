package com.ouyunc.message.handler;

import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.processor.AbstractMessageProcessor;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fzx
 * @Description: 消息前置处理器, 处理登录相关
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
        log.info("消息前置处理器 PacketPreHandler 正在处理packet= {} ...", packet);
        // 所有的消息包处理，都是以消息类型为基准，在消息前置处理器中去处理，做认证和鉴权
        AbstractMessageProcessor<? extends Number> messageProcessor = MessageServerContext.messageProcessorCache.get(packet.getMessageType());
        if (messageProcessor == null) {
            log.error("非法消息类型，messageType= {}", packet.getMessageType());
            ctx.close();
            return;
        }
        // 判断是否开启外部客户端心跳，如果没开启但是发送了心跳类型的消息，则关闭channel
        if (!MessageServerContext.serverProperties().isClientHeartBeatEnable() && packet.getMessageType() == MessageTypeEnum.PING_PONG.getType()) {
            log.error("外部客户端未开启心跳, 非法消息类型，messageType= {}", packet.getMessageType());
            ctx.close();
            return;
        }
        messageProcessor.preProcess(ctx, packet);
    }
}
