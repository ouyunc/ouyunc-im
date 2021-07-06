package com.ouyu.im.handler;

import com.ouyu.im.context.IMContext;
import com.ouyu.im.packet.Packet;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * @Author fangzhenxun
 * @Description: 自定义 im 处理器，客户端连接池中存放的channel 也是这里面的类型数据
 * @Version V1.0
 **/
public class OuYuImServerHandler  extends SimpleChannelInboundHandler<Packet> {



    /**
     * @Author fangzhenxun
     * @Description 自定义偶遇im 处理器
     * @param ctx
     * @param packet
     * @return void
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        IMContext.MESSAGE_PROCESSOR_CACHE.get(packet.getMessageType()).doProcess(ctx, packet);
    }
}
