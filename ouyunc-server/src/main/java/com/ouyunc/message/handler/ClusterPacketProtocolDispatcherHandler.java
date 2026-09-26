package com.ouyunc.message.handler;

import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.cluster.auth.ClusterChannelGuard;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.protocol.NativePacketProtocol;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * 集群 OUYUNC 认证通过后，按协议号交给集群业务管道。
 * 客户端原生包见 {@link ClientPacketProtocolDispatcherHandler}。
 */
public class ClusterPacketProtocolDispatcherHandler extends SimpleChannelInboundHandler<Packet> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        if (ClusterChannelGuard.requireAuthenticatedPeer(ctx) == null) {
            return;
        }
        MessageServerContext.findProtocol(NativePacketProtocol.OUYUNC.getProtocol(), NativePacketProtocol.OUYUNC.getProtocolVersion()).doDispatcher(ctx, packet);
        // 注意：这里一定要将消息往下传，这个与ByteToMessageDecoder不一样，在ByteToMessageDecoder中可以不用传，因为源码中已经帮我们传了，具体可看源码。
        ctx.fireChannelRead(packet);
    }
}
