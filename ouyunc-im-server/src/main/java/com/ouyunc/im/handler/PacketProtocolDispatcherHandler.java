package com.ouyunc.im.handler;

import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.protocol.Protocol;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: packet 协议调度处理器
 **/
public class PacketProtocolDispatcherHandler extends SimpleChannelInboundHandler<Packet> {
    private static Logger log = LoggerFactory.getLogger(PacketProtocolDispatcherHandler.class);


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        log.info("开始添加packet的具体处理器");
        Protocol.prototype(Protocol.OUYUNC.getProtocol(), Protocol.OUYUNC.getVersion()).doDispatcher(ctx, null);
        // 计数器重新加一次，交给下一个handler 处理，
        // 注意：这里一定要将消息往下传，这个与ByteToMessageDecoder不一样，在ByteToMessageDecoder中可以不用传，因为源码中已经帮我们传了，具体可看源码。
        ctx.fireChannelRead(packet);
    }
}
