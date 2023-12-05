package com.ouyunc.im.handler;

import com.ouyunc.im.context.IMProcessContext;
import com.ouyunc.im.packet.Packet;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: 自定义 Packet 处理器，客户端连接池中存放的channel 也是这里面的类型数据
 **/
public class OuyuncServerHandler extends SimpleChannelInboundHandler<Packet> {
    private static Logger log = LoggerFactory.getLogger(OuyuncServerHandler.class);


    /**
     * @param ctx
     * @param packet
     * @return void
     * @Author fangzhenxun
     * @Description 自定义偶遇im 处理器
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        log.info("OuyuncServerHandler 正在处理自定义ouyunc，协议包内容：  packet= {} ...", packet);
        IMProcessContext.MESSAGE_PROCESSOR.get(packet.getMessageType()).doProcess(ctx, packet);
    }
}
