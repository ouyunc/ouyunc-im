package com.ouyunc.im.handler;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.im.context.IMProcessContext;
import com.ouyunc.im.packet.Packet;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: mqtt协议服务核心处理
 **/
@ChannelHandler.Sharable
public class MqttServerHandler extends SimpleChannelInboundHandler<Packet> {
    private static Logger log = LoggerFactory.getLogger(MqttServerHandler.class);


    /**
     * @Author fangzhenxun
     * @Description mqtt 真正处理的逻辑
     * @param ctx
     * @param packet
     * @return void
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        log.info("MqttServerHandler 处理器正在处理... {}", JSON.toJSONString(packet));
        // 需要判断消息类型，转到对应的去处理
        IMProcessContext.MESSAGE_PROCESSOR.get(packet.getMessageType()).doProcess(ctx, packet);
    }
}
