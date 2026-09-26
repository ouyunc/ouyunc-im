package com.ouyunc.message.handler;

import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.protocol.NativePacketProtocol;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * OUYUNC_CLIENT 首包后安装客户端业务管道（与集群 {@link ClusterPacketProtocolDispatcherHandler} 分离）。
 */
public class ClientPacketProtocolDispatcherHandler extends SimpleChannelInboundHandler<Packet> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
        MessageServerContext.findProtocol(
                NativePacketProtocol.OUYUNC_CLIENT.getProtocol(),
                NativePacketProtocol.OUYUNC_CLIENT.getProtocolVersion()).doDispatcher(ctx, packet);
        // 与 ByteToMessageDecoder 不同，须显式向下传递
        ctx.fireChannelRead(packet);
    }
}
