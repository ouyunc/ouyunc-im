package com.ouyunc.im.handler;

import com.ouyunc.im.context.IMProcessContext;
import com.ouyunc.im.packet.Packet;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: ws 业务逻辑处理器
 **/
public class WsServerHandler extends SimpleChannelInboundHandler<Packet> {
    private static Logger log = LoggerFactory.getLogger(WsServerHandler.class);


    /**
     * @param ctx
     * @param packet
     * @return void
     * @Author fangzhenxun
     * @Description 通过这里处理
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        log.info("websocket 处理器WsServerHandler正在处理packet= {} ...", packet);
        // 需要判断消息类型，转到对应的去处理
        IMProcessContext.MESSAGE_PROCESSOR.get(packet.getMessageType()).doProcess(ctx, packet);
    }
}
