package com.ouyu.im.handler;

import com.ouyu.im.context.IMServerContext;
import com.ouyu.im.packet.Packet;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: ws 业务逻辑处理器
 * @Version V1.0
 **/
public class WsServerHandler  extends SimpleChannelInboundHandler<Packet> {
    private static Logger log = LoggerFactory.getLogger(WsServerHandler.class);


    /**
     * @Author fangzhenxun
     * @Description 通过这里处理
     * @param ctx
     * @param packet
     * @return void
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        // 需要判断消息类型，转到对应的去处理
        IMServerContext.MESSAGE_PROCESSOR_CACHE.get(packet.getMessageType()).doProcess(ctx, packet);
    }



}
