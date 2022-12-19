package com.ouyunc.im.handler;

import com.ouyunc.im.context.IMProcessContext;
import com.ouyunc.im.packet.Packet;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: 消息的可靠传递(qos机制)，通过超时，重传（客户端来处理），确认
 * @Version V3.0
 **/
public class QosHandler extends SimpleChannelInboundHandler<Packet> {
    private static Logger log = LoggerFactory.getLogger(QosHandler.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        log.info("消息可靠传递 QosServerHandler 正在处理相关packet：{} ", packet);
        IMProcessContext.MESSAGE_PROCESSOR.get(packet.getMessageType()).postProcess(ctx, packet);
    }
}
