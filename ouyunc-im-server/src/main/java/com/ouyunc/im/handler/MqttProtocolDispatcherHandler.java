package com.ouyunc.im.handler;

import com.ouyunc.im.protocol.Protocol;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * @Author fangzhenxun
 * @Description: mqtt 原生协议分发处理器
 **/
public class MqttProtocolDispatcherHandler extends SimpleChannelInboundHandler<Object> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        Protocol.prototype(Protocol.MQTT.getProtocol(), Protocol.MQTT.getVersion()).doDispatcher(ctx, null);
        ctx.fireChannelRead(((ByteBuf) msg).retain());
    }
}
