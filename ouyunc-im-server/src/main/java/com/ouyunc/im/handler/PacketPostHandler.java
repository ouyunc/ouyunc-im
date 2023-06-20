package com.ouyunc.im.handler;

import com.ouyunc.im.context.IMProcessContext;
import com.ouyunc.im.packet.Packet;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: 消息的后置处理器
 * @Version V3.0
 **/
public class PacketPostHandler extends SimpleChannelInboundHandler<Packet>{
    private static Logger log = LoggerFactory.getLogger(PacketPostHandler.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        log.info("消息后置处理器 PostHandler 正在处理相关packet：{} ", packet);
        IMProcessContext.MESSAGE_PROCESSOR.get(packet.getMessageType()).postProcess(ctx, packet);
    }


}
