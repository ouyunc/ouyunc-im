package com.ouyunc.message.handler;

import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.protocol.NativePacketProtocol;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fzx
 * @Description: packet 协议调度处理器
 **/
public class PacketProtocolDispatcherHandler extends SimpleChannelInboundHandler<Packet> {
    private static final Logger log = LoggerFactory.getLogger(PacketProtocolDispatcherHandler.class);


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        log.info("开始添加packet的具体处理器");
        MessageServerContext.findProtocol(NativePacketProtocol.OUYUNC.getProtocol(), NativePacketProtocol.OUYUNC.getProtocolVersion()).doDispatcher(ctx, null);
        // 注意：这里一定要将消息往下传，这个与ByteToMessageDecoder不一样，在ByteToMessageDecoder中可以不用传，因为源码中已经帮我们传了，具体可看源码。
        ctx.fireChannelRead(packet);
    }
}
