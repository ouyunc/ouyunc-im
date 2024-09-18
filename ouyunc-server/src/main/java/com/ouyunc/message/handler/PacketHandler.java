package com.ouyunc.message.handler;

import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.context.MessageServerContext;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fzx
 * @Description: packet 业务逻辑处理器
 **/
public class PacketHandler extends SimpleChannelInboundHandler<Packet> {
    private static final Logger log = LoggerFactory.getLogger(PacketHandler.class);


    /**
     * @Author fzx
     * @Description 通过这里处理
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        log.info("消息逻辑处理器正在处理相关packet：{} ", packet);
        // 需要判断消息类型，转到对应的去处理
        MessageServerContext.messageProcessorCache.get(packet.getMessageType()).process(ctx, packet);
    }
}
