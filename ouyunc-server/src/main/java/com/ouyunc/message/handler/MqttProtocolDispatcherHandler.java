package com.ouyunc.message.handler;

import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.protocol.NativePacketProtocol;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * @Author fzx
 * @Description: mqtt 原生协议分发处理器
 **/
public class MqttProtocolDispatcherHandler extends SimpleChannelInboundHandler<Object> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        MessageServerContext.findProtocol(NativePacketProtocol.MQTT.getProtocol(), NativePacketProtocol.MQTT.getProtocolVersion()).doDispatcher(ctx, null);
        ctx.fireChannelRead(((ByteBuf) msg).retain());
    }
}
