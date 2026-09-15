package com.ouyunc.message.handler;

import com.ouyunc.base.packet.Packet;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @deprecated 内容安全已迁移至鉴权后的 {@link ContentSafetyHandler}，请勿再挂在登录前。
 */
@Deprecated
public class MonitorHandler extends SimpleChannelInboundHandler<Packet> {
    private static final Logger log = LoggerFactory.getLogger(MonitorHandler.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
        log.debug("MonitorHandler 已废弃，请使用 ContentSafetyHandler");
        ctx.fireChannelRead(packet);
    }
}
