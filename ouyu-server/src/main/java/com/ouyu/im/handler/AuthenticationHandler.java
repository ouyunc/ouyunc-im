package com.ouyu.im.handler;

import com.ouyu.im.context.IMContext;
import com.ouyu.im.packet.Packet;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: 认证处理器
 * @Version V1.0
 **/
public class AuthenticationHandler extends SimpleChannelInboundHandler<Packet> {
    private static Logger log = LoggerFactory.getLogger(AuthenticationHandler.class);

    /**
     * @Author fangzhenxun
     * @Description 登录逻辑处理
     * @param ctx
     * @param packet
     * @return void
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        // 所有的消息包处理，都是以消息类型为基准，在消息前置处理器中去处理
        //MessageEnum.prototype(packet.getMessageType()).preProcess(ctx, packet);
        IMContext.MESSAGE_PROCESSOR_CACHE.get(packet.getMessageType()).preProcess(ctx, packet);

    }
}
