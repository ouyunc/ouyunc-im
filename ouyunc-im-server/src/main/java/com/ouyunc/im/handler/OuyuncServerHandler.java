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
 * @Version V3.0
 **/
public class OuyuncServerHandler extends SimpleChannelInboundHandler<Packet> {
    private static Logger log = LoggerFactory.getLogger(OuyuncServerHandler.class);


    /**
     * @Author fangzhenxun
     * @Description 自定义偶遇im 处理器
     * @param ctx
     * @param packet
     * @return void
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        log.info("自定义packet ouyunc 协议处理器 OuyuncServerHandler 正在处理packet= {} ...", packet);
        IMProcessContext.MESSAGE_PROCESSOR.get(packet.getMessageType()).doProcess(ctx, packet);
    }
}
