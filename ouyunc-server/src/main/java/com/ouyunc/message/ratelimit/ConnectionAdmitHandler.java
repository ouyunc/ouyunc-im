package com.ouyunc.message.ratelimit;

import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.properties.MessageServerProperties;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * 直连模式下在连接建立时按远端地址准入。
 * 开启 PROXY Protocol 时这里不能计费：此时远端是负载均衡器，真实地址要等可信 PROXY 头。
 */
@ChannelHandler.Sharable
public final class ConnectionAdmitHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ConnectionAdmitHandler.class);

    public static final ConnectionAdmitHandler INSTANCE = new ConnectionAdmitHandler();

    private ConnectionAdmitHandler() {
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        MessageServerProperties props = MessageServerContext.serverProperties();
        if (props != null && props.isProxyProtocolEnabled()) {
            ctx.fireChannelActive();
            return;
        }
        String remoteIp = null;
        if (ctx.channel().remoteAddress() instanceof InetSocketAddress socketAddress
                && socketAddress.getAddress() != null) {
            remoteIp = socketAddress.getAddress().getHostAddress();
        }
        if (!ConnectionAdmission.admit(ctx, remoteIp)) {
            log.debug("连接速率超限，关闭 remote={}", ctx.channel().remoteAddress());
            ctx.close();
            return;
        }
        ctx.fireChannelActive();
    }
}
