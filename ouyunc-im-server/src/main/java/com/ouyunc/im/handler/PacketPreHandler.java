package com.ouyunc.im.handler;

import com.ouyunc.im.context.IMProcessContext;
import com.ouyunc.im.packet.Packet;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: 消息前置处理器, 处理登录相关
 **/
public class PacketPreHandler extends SimpleChannelInboundHandler<Packet> {
    private static Logger log = LoggerFactory.getLogger(PacketPreHandler.class);

    /**
     * @Author fangzhenxun
     * @Description 登录逻辑处理
     * @param ctx
     * @param packet
     * @return void
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        log.info("消息前置处理器 PacketPreHandler 正在处理packet= {} ...", packet);
        // 所有的消息包处理，都是以消息类型为基准，在消息前置处理器中去处理，做认证和鉴权
        IMProcessContext.MESSAGE_PROCESSOR.get(packet.getMessageType()).preProcess(ctx, packet);
    }
}