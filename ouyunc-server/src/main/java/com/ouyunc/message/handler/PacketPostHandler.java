package com.ouyunc.message.handler;


import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.context.MessageServerContext;
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
        log.info("消息后置处理器 PostHandler 正在处理相关packet：{} ", packet);
        MessageServerContext.messageProcessorCache.get(packet.getMessageType()).postProcess(ctx, packet);
    }


}
